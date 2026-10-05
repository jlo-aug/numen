package it.palsoftware.pastiera.data.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class LayoutRepositoryManagerTest {

    @Test
    fun withDownloadTempFile_deletesFileAfterSuccess() = withTempDirectory { cacheDir ->
        lateinit var tempFile: File

        val result = LayoutRepositoryManager.withDownloadTempFile(cacheDir) { file ->
            tempFile = file
            file.writeText("downloaded")
            "installed"
        }

        assertEquals("installed", result)
        assertFalse(tempFile.exists())
        assertFalse(cacheDir.listFiles().orEmpty().any())
    }

    @Test
    fun withDownloadTempFile_deletesFileWhenDownloadProcessingThrows() = withTempDirectory { cacheDir ->
        lateinit var tempFile: File

        assertThrows(IOException::class.java) {
            LayoutRepositoryManager.withDownloadTempFile(cacheDir) { file ->
                tempFile = file
                file.writeText("partial download")
                throw IOException("simulated copy failure")
            }
        }

        assertFalse(tempFile.exists())
        assertFalse(cacheDir.listFiles().orEmpty().any())
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val tempDir = Files.createTempDirectory("layout-download-test").toFile()
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
