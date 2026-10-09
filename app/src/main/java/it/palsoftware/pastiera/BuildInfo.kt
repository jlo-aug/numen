package it.palsoftware.pastiera

import it.palsoftware.pastiera.BuildConfig

/**
 * Provides information about the app build.
 */
object BuildInfo {
    /**
     * Returns the formatted version and release channel string.
     */
    fun getBuildInfoString(): String {
        val version = BuildConfig.VERSION_NAME
        return "Ver. $version"
    }
}
