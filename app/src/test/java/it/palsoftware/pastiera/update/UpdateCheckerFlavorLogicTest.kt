package it.palsoftware.pastiera.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerFlavorLogicTest {

    @Test
    fun stableNoticesPrioritizePlektraAndRetainPastiera() {
        val pastiera = UpdateCheckResult(
            successful = true,
            hasAnnouncement = true,
            releaseTag = "v0.87",
            isPastieraStableUpdate = true
        )
        val plektra = UpdateCheckResult(
            successful = true,
            hasAnnouncement = true,
            releaseTag = "v1.0"
        )

        val result = combineStableUpdateResults(pastiera, plektra)

        assertEquals("v1.0", result.releaseTag)
        assertEquals("v0.87", result.followUpAnnouncement?.releaseTag)
    }

    @Test
    fun stableNoticesUsePastieraWhenNoPlektraReleaseExists() {
        val pastiera = UpdateCheckResult(
            successful = true,
            hasAnnouncement = true,
            releaseTag = "v0.87",
            isPastieraStableUpdate = true
        )

        val result = combineStableUpdateResults(
            pastiera,
            UpdateCheckResult(successful = true)
        )

        assertEquals("v0.87", result.releaseTag)
        assertTrue(result.isPastieraStableUpdate)
        assertNull(result.followUpAnnouncement)
    }

    @Test
    fun nightlyNoticesPrioritizePlektraAndRetainPastiera() {
        val pastiera = UpdateCheckResult(
            successful = true,
            hasAnnouncement = true,
            releaseTag = "nightly/v0.87-nightly.1",
            isNightlyUpdate = true
        )
        val plektra = UpdateCheckResult(
            successful = true,
            hasAnnouncement = true,
            releaseTag = "v1.0-beta.1"
        )

        val result = combineNightlyUpdateResults(pastiera, plektra)

        assertEquals("v1.0-beta.1", result.releaseTag)
        assertEquals("nightly/v0.87-nightly.1", result.followUpAnnouncement?.releaseTag)
    }

    @Test
    fun stableChannelUsesLatestNonPrerelease() {
        val release = findLatestRelease(sampleReleases(), "stable")

        requireNotNull(release)
        assertEquals("v0.85", release.tagName)
        assertEquals("Plektra 0.85", release.displayName)
        assertEquals("https://example.com/releases/v0.85", release.releasePageUrl)
    }

    @Test
    fun pastieraStableUsesHighestNewerRegularRelease() {
        val releases = listOf(
            stableRelease("v0.87", "Pastiera 0.87"),
            stableRelease("v0.86", "Pastiera 0.86"),
            stableRelease("v0.88", "Pastiera 0.88"),
            GitHubRelease(
                tagName = "v0.89-rc1",
                name = "Pastiera 0.89 RC 1",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.89-rc1"
            )
        )

        val release = findNewerStableRelease(releases, "0.86")

        requireNotNull(release)
        assertEquals("v0.88", release.tagName)
        assertEquals("Pastiera 0.88", release.displayName)
    }

    @Test
    fun pastieraStableIgnoresCurrentOlderAndUnknownVersions() {
        val releases = listOf(
            stableRelease("v0.86", "Pastiera 0.86"),
            stableRelease("v0.85", "Pastiera 0.85"),
            stableRelease("security-latest", "Pastiera security update")
        )

        assertNull(findNewerStableRelease(releases, "0.86"))
    }

    @Test
    fun nightlyChannelUsesLatestNightlyPrerelease() {
        val release = findLatestRelease(sampleReleases(), "nightly")

        requireNotNull(release)
        assertEquals("nightly/v0.85-nightly.20260306.214144", release.tagName)
        assertEquals("Plektra Nightly 0.85", release.displayName)
        assertEquals("https://example.com/releases/nightly-v0.85-nightly.20260306.214144", release.releasePageUrl)
    }

    @Test
    fun nightlyChannelAcceptsPrereleasesWithoutNightlyTagPrefix() {
        val releases = listOf(
            GitHubRelease(
                tagName = "beta/v0.85-beta1",
                name = "Plektra Beta",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/beta"
            )
        )

        assertEquals("beta/v0.85-beta1", findLatestRelease(releases, "nightly")?.tagName)
    }

    @Test
    fun normalizeReleaseVersionStripsKnownPrefixes() {
        assertEquals("0.85", normalizeReleaseVersion("v0.85"))
        assertEquals("0.85", normalizeReleaseVersion("V0.85"))
        assertEquals(
            "0.85-nightly.20260306.214144",
            normalizeReleaseVersion("nightly/v0.85-nightly.20260306.214144")
        )
    }

    @Test
    fun nightlyReleaseNotesUseTheBaseReleaseVersion() {
        assertEquals(
            "0.86",
            normalizeReleaseNotesVersion("0.86-nightly.20260926.123809")
        )
        assertEquals("0.86", normalizeReleaseNotesVersion("0.86"))
    }

    private fun sampleReleases(): List<GitHubRelease> =
        listOf(
            GitHubRelease(
                tagName = "nightly/v0.85-nightly.20260306.214144",
                name = "Plektra Nightly 0.85",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/nightly-v0.85-nightly.20260306.214144"
            ),
            GitHubRelease(
                tagName = "v0.85",
                name = "Plektra 0.85",
                prerelease = false,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.85"
            ),
            GitHubRelease(
                tagName = "v0.84",
                name = null,
                prerelease = false,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.84"
            )
        )

    private fun stableRelease(tag: String, name: String) = GitHubRelease(
        tagName = tag,
        name = name,
        prerelease = false,
        draft = false,
        htmlUrl = "https://example.com/releases/$tag"
    )
}
