package it.palsoftware.pastiera.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

internal fun successorReleasesApiUrl(): String =
    "https://api.github.com/repos/${BuildConfig.SUCCESSOR_GITHUB_REPOSITORY}/releases?per_page=20"

internal fun successorReleasesPage(): String =
    "https://github.com/${BuildConfig.SUCCESSOR_GITHUB_REPOSITORY}/releases"

private val client = OkHttpClient()
private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

internal data class UpdateCheckResult(
    val successful: Boolean,
    val hasAnnouncement: Boolean = false,
    val releaseTag: String? = null,
    val displayName: String? = null,
    val releasePageUrl: String? = null,
    val downloadUrl: String? = null,
    val isNightlyUpdate: Boolean = false,
    val isPastieraStableUpdate: Boolean = false,
    val followUpAnnouncement: UpdateCheckResult? = null
)

internal fun combineStableUpdateResults(
    pastiera: UpdateCheckResult,
    successor: UpdateCheckResult
): UpdateCheckResult {
    val successful = pastiera.successful && successor.successful
    return when {
        successor.hasAnnouncement -> successor.copy(
            successful = successful,
            followUpAnnouncement = pastiera.takeIf { it.hasAnnouncement }
        )
        pastiera.hasAnnouncement -> pastiera.copy(successful = successful)
        else -> UpdateCheckResult(successful = successful)
    }
}

internal fun combineNightlyUpdateResults(
    pastiera: UpdateCheckResult,
    successor: UpdateCheckResult
): UpdateCheckResult = combineStableUpdateResults(pastiera, successor)

private enum class ReleaseFeed {
    SUCCESSOR,
    PASTIERA_STABLE,
    PASTIERA_NIGHTLY
}

internal fun checkForUpdate(
    context: Context,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean = true,
    callback: (UpdateCheckResult) -> Unit
) = checkRelease(context, releaseChannel, ignoreDismissedReleases, ReleaseFeed.SUCCESSOR, callback)

private fun checkForPastieraStableUpdate(
    context: Context,
    ignoreDismissedReleases: Boolean,
    callback: (UpdateCheckResult) -> Unit
) = checkRelease(
    context,
    "stable",
    ignoreDismissedReleases,
    ReleaseFeed.PASTIERA_STABLE,
    callback
)


internal fun checkForNightlyUpdate(
    context: Context,
    ignoreDismissedReleases: Boolean = true,
    callback: (UpdateCheckResult) -> Unit
) {
    if (BuildConfig.RELEASE_CHANNEL != "nightly") {
        postResult(callback, UpdateCheckResult(successful = true))
        return
    }
    checkRelease(context, "nightly", ignoreDismissedReleases, ReleaseFeed.PASTIERA_NIGHTLY, callback)
}

internal fun checkForUpdateNotices(
    context: Context,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean = true,
    callback: (UpdateCheckResult) -> Unit
) {
    if (BuildConfig.RELEASE_CHANNEL == "nightly") {
        checkForNightlyUpdate(context, ignoreDismissedReleases) { nightly ->
            checkForUpdate(context, "nightly", ignoreDismissedReleases) { successor ->
                callback(combineNightlyUpdateResults(nightly, successor))
            }
        }
    } else {
        checkForPastieraStableUpdate(context, ignoreDismissedReleases) { pastiera ->
            checkForUpdate(context, "stable", ignoreDismissedReleases) { successor ->
                callback(combineStableUpdateResults(pastiera, successor))
            }
        }
    }
}

private fun checkRelease(
    context: Context,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean,
    feed: ReleaseFeed,
    callback: (UpdateCheckResult) -> Unit
) {
    if (!shouldUseGithubUpdateChecks(context)) {
        postResult(callback, UpdateCheckResult(successful = true))
        return
    }

    val request = Request.Builder()
        .url(
            if (feed == ReleaseFeed.SUCCESSOR) successorReleasesApiUrl()
            else "https://api.github.com/repos/palsoftware/pastiera/releases?per_page=20"
        )
        .header("Accept", "application/vnd.github+json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            postResult(callback, UpdateCheckResult(successful = false))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                if (!res.isSuccessful) {
                    postResult(callback, UpdateCheckResult(successful = false))
                    return
                }

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postResult(callback, UpdateCheckResult(successful = false))
                    return
                }

                val latestRelease = try {
                    val releases = parseGitHubReleases(JSONArray(body))
                    when (feed) {
                        ReleaseFeed.PASTIERA_NIGHTLY -> findNewerNightlyRelease(releases, BuildConfig.VERSION_NAME)
                        ReleaseFeed.PASTIERA_STABLE -> findNewerStableRelease(releases, BuildConfig.VERSION_NAME)
                        ReleaseFeed.SUCCESSOR -> findLatestRelease(releases, releaseChannel)
                    }
                } catch (_: Exception) {
                    postResult(callback, UpdateCheckResult(successful = false))
                    return
                }
                if (latestRelease == null) {
                    postResult(callback, UpdateCheckResult(successful = true))
                    return
                }

                val releaseTag = latestRelease.tagName
                if (ignoreDismissedReleases) {
                    val dismissalKey = when (feed) {
                        ReleaseFeed.PASTIERA_NIGHTLY -> "pastiera-nightly:$releaseTag"
                        ReleaseFeed.PASTIERA_STABLE -> "pastiera-stable:$releaseTag"
                        ReleaseFeed.SUCCESSOR -> releaseTag
                    }
                    val isDismissed = SettingsManager.isReleaseDismissed(context, dismissalKey)
                    if (isDismissed) {
                        // Release was dismissed, don't show update
                        postResult(callback, UpdateCheckResult(successful = true))
                        return
                    }
                }
                
                postResult(
                    callback,
                    UpdateCheckResult(
                        successful = true,
                        hasAnnouncement = true,
                        releaseTag = releaseTag,
                        displayName = latestRelease.displayName,
                        releasePageUrl = latestRelease.releasePageUrl,
                        downloadUrl = latestRelease.downloadUrl,
                        isNightlyUpdate = feed == ReleaseFeed.PASTIERA_NIGHTLY,
                        isPastieraStableUpdate = feed == ReleaseFeed.PASTIERA_STABLE
                    )
                )
            }
        }
    })
}

private fun postResult(
    callback: (UpdateCheckResult) -> Unit,
    result: UpdateCheckResult
) {
    mainHandler.post {
        callback(result)
    }
}

fun showUpdateDialog(
    context: Context,
    releaseTag: String,
    displayName: String,
    releasePageUrl: String?,
    onClosed: () -> Unit = {}
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.successor_dialog_title)
        .setMessage(context.getString(R.string.successor_dialog_message, displayName))
        .setPositiveButton(R.string.successor_dialog_open_release) { _, _ ->
            openUrl(context, releasePageUrl ?: successorReleasesPage())
            onClosed()
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, releaseTag)
            onClosed()
        }
        .create()
    dialog.setOnCancelListener { onClosed() }
    dialog.show()
}

private fun showPastieraStableUpdateDialog(
    context: Context,
    releaseTag: String,
    displayName: String,
    releasePageUrl: String?,
    onClosed: () -> Unit
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.pastiera_stable_update_title)
        .setMessage(context.getString(R.string.pastiera_stable_update_message, displayName))
        .setPositiveButton(R.string.pastiera_stable_update_open) { _, _ ->
            openUrl(context, releasePageUrl ?: "https://github.com/palsoftware/pastiera/releases")
            onClosed()
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, "pastiera-stable:$releaseTag")
            onClosed()
        }
        .create()
    dialog.setOnCancelListener { onClosed() }
    dialog.show()
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

internal fun showReleaseNotice(context: Context, result: UpdateCheckResult) {
    val tag = result.releaseTag ?: return
    val name = result.displayName ?: return
    val showFollowUp = {
        result.followUpAnnouncement?.let { showReleaseNotice(context, it) }
        Unit
    }
    if (result.isPastieraStableUpdate) {
        showPastieraStableUpdateDialog(context, tag, name, result.releasePageUrl, showFollowUp)
        return
    }
    if (!result.isNightlyUpdate) {
        showUpdateDialog(context, tag, name, result.releasePageUrl, showFollowUp)
        return
    }
    val builder = AlertDialog.Builder(context)
        .setTitle(R.string.nightly_update_title)
        .setMessage(context.getString(R.string.nightly_update_message, name))
        .setPositiveButton(R.string.nightly_update_open) { _, _ ->
            openUrl(context, result.releasePageUrl ?: "https://github.com/palsoftware/pastiera/releases")
            showFollowUp()
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, "pastiera-nightly:$tag")
            showFollowUp()
        }
    result.downloadUrl?.let { url ->
        builder.setNegativeButton(R.string.nightly_update_download) { _, _ ->
            openUrl(context, url)
            showFollowUp()
        }
    }
    val dialog = builder.create()
    dialog.setOnCancelListener { showFollowUp() }
    dialog.show()
}
