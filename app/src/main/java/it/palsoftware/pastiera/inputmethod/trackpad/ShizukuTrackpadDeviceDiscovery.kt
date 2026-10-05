package it.palsoftware.pastiera.inputmethod.trackpad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuTrackpadDeviceDiscovery {
    suspend fun discover(): List<TrackpadInputDevice> = withContext(Dispatchers.IO) {
        discoverBlocking()
    }

    internal fun discoverBlocking(): List<TrackpadInputDevice> {
        val process = startProcess(arrayOf("getevent", "-pl"))
        return try {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "getevent capability discovery failed with exit code $exitCode: ${error.trim()}"
            }
            TrackpadInputDeviceDiscovery.parseGeteventCapabilities(output)
        } finally {
            process.destroy()
        }
    }

    internal fun startProcess(command: Array<String>): Process {
        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        newProcessMethod.isAccessible = true
        return newProcessMethod.invoke(null, command, null, null) as Process
    }
}
