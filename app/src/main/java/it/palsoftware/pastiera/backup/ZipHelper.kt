package it.palsoftware.pastiera.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {
    // A rich migration backup currently uses only a handful of small entries. These limits still
    // leave ample room for the separately bounded 16 MiB typing-sound pack and future user data.
    internal val DEFAULT_ARCHIVE_LIMITS = ArchiveLimits(
        maxEntries = 1_024,
        maxEntryBytes = 32L * 1024L * 1024L,
        maxTotalBytes = 64L * 1024L * 1024L
    )

    internal data class ArchiveLimits(
        val maxEntries: Int,
        val maxEntryBytes: Long,
        val maxTotalBytes: Long
    )

    fun zip(sourceDir: File, outputStream: OutputStream) {
        zipWithLimits(sourceDir, outputStream, DEFAULT_ARCHIVE_LIMITS)
    }

    internal fun zipWithLimits(
        sourceDir: File,
        outputStream: OutputStream,
        limits: ArchiveLimits
    ) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            val basePath = sourceDir.toPath()
            var entryCount = 0
            var totalBytes = 0L
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    entryCount += 1
                    if (entryCount > limits.maxEntries) {
                        throw IllegalStateException("Refusing to create archive with too many entries")
                    }
                    val relative = basePath.relativize(file.toPath()).toString().replace("\\", "/")
                    val entry = ZipEntry(relative)
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input ->
                        totalBytes = copyWithLimits(
                            input = input,
                            output = zipOut,
                            initialTotalBytes = totalBytes,
                            limits = limits,
                            entryLimitMessage = "Refusing to create archive entry above size limit",
                            totalLimitMessage = "Refusing to create archive above total size limit"
                        )
                    }
                    zipOut.closeEntry()
                }
        }
    }

    fun unzip(inputStream: InputStream, targetDir: File) {
        unzipWithLimits(inputStream, targetDir, DEFAULT_ARCHIVE_LIMITS)
    }

    internal fun unzipWithLimits(
        inputStream: InputStream,
        targetDir: File,
        limits: ArchiveLimits
    ) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
            var entry = zipIn.nextEntry
            val canonicalTargetRoot = targetDir.canonicalFile
            val canonicalTargetPath = canonicalTargetRoot.toPath()
            var entryCount = 0
            var totalBytes = 0L

            while (entry != null) {
                entryCount += 1
                if (entryCount > limits.maxEntries) {
                    throw IllegalStateException("Refusing to unzip archive with too many entries")
                }
                val entryName = entry.name.removePrefix("./")
                if (File(entryName).isAbsolute) {
                    throw IllegalStateException("Refusing to unzip absolute entry: $entryName")
                }
                val outFile = File(targetDir, entryName)
                val canonicalOutFile = outFile.canonicalFile
                if (!canonicalOutFile.toPath().startsWith(canonicalTargetPath)) {
                    throw IllegalStateException("Refusing to unzip entry outside target dir: $entryName")
                }

                if (entry.isDirectory) {
                    canonicalOutFile.mkdirs()
                } else {
                    canonicalOutFile.parentFile?.mkdirs()
                    FileOutputStream(canonicalOutFile).use { output ->
                        totalBytes = copyWithLimits(
                            input = zipIn,
                            output = output,
                            initialTotalBytes = totalBytes,
                            limits = limits,
                            entryLimitMessage = "Refusing to unzip entry above size limit",
                            totalLimitMessage = "Refusing to unzip archive above total size limit"
                        )
                    }
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    private fun copyWithLimits(
        input: InputStream,
        output: OutputStream,
        initialTotalBytes: Long,
        limits: ArchiveLimits,
        entryLimitMessage: String,
        totalLimitMessage: String
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0L
        var totalBytes = initialTotalBytes
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (read == 0) continue
            if (read.toLong() > limits.maxEntryBytes - entryBytes) {
                throw IllegalStateException(entryLimitMessage)
            }
            if (read.toLong() > limits.maxTotalBytes - totalBytes) {
                throw IllegalStateException(totalLimitMessage)
            }
            output.write(buffer, 0, read)
            entryBytes += read
            totalBytes += read
        }
        return totalBytes
    }
}
