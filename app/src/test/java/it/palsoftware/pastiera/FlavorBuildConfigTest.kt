package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorBuildConfigTest {

    @Test
    fun releaseChannelBuildConfigMatchesFlavor() {
        assertTrue(BuildConfig.SUCCESSOR_GITHUB_REPOSITORY.matches(Regex("[^/]+/[^/]+")))
        when (BuildConfig.RELEASE_CHANNEL) {
            "stable" -> {
                assertEquals(BuildConfig.IS_FDROID_BUILD, !BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
                assertFalse(BuildConfig.VERSION_NAME.contains("nightly"))
                assertTrue(BuildConfig.UPSTREAM_BRANDING)
                assertEquals("Ver. ${BuildConfig.VERSION_NAME} - Stable", BuildInfo.getBuildInfoString())
            }
            "nightly" -> {
                // Numen: its own app, never checks Pastiera's releases, no Pastiera branding.
                assertEquals("app.numen.keyboard", BuildConfig.APPLICATION_ID)
                assertFalse(BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
                assertFalse(BuildConfig.UPSTREAM_BRANDING)
                assertEquals("Ver. ${BuildConfig.VERSION_NAME}", BuildInfo.getBuildInfoString())
            }
            else -> error("Unexpected release channel: ${BuildConfig.RELEASE_CHANNEL}")
        }
    }
}
