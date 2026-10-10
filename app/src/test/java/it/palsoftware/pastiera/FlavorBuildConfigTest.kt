package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Test

class FlavorBuildConfigTest {

    @Test
    fun numenIsItsOwnApp() {
        assertEquals("app.numen.keyboard", BuildConfig.APPLICATION_ID)
        assertEquals("Version ${BuildConfig.VERSION_NAME}", BuildInfo.getBuildInfoString())
    }
}
