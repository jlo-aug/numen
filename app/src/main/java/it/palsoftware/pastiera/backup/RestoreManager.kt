package it.palsoftware.pastiera.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import it.palsoftware.pastiera.DeviceIdentitySnapshot
import it.palsoftware.pastiera.AppBroadcastActions
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RestoreManager {
    private const val TAG = "RestoreManager"
    private const val USER_DICTIONARY_PREF_KEY = "pastiera_prefs:user_dictionary_entries"
    private const val CUSTOM_INPUT_STYLES_PREF_KEY = "pastiera_prefs:custom_input_styles"
    private const val USER_DEFAULTS_FILE_NAME = "user_defaults.json"

    enum class PostRestoreAction {
        REFRESH_USER_DICTIONARY,
        REGISTER_CUSTOM_INPUT_STYLES
    }

    enum class ImportMode {
        UNCHANGED,
        ADAPT_TO_CURRENT_DEVICE
    }

    data class DeviceChange(
        val source: DeviceIdentitySnapshot,
        val target: DeviceIdentitySnapshot
    )

    private data class PostRestoreTriggerRule(
        val action: PostRestoreAction,
        val matchingAppliedPrefKeys: Set<String> = emptySet(),
        val matchingRestoredFileNames: Set<String> = emptySet()
    )

    private val postRestoreTriggerRules = listOf(
        PostRestoreTriggerRule(
            action = PostRestoreAction.REFRESH_USER_DICTIONARY,
            matchingAppliedPrefKeys = setOf(USER_DICTIONARY_PREF_KEY),
            matchingRestoredFileNames = setOf(USER_DEFAULTS_FILE_NAME)
        ),
        PostRestoreTriggerRule(
            action = PostRestoreAction.REGISTER_CUSTOM_INPUT_STYLES,
            matchingAppliedPrefKeys = setOf(CUSTOM_INPUT_STYLES_PREF_KEY)
        )
    )

    suspend fun inspect(context: Context, sourceUri: Uri): RestoreInspectionResult =
        withContext(Dispatchers.IO) {
            val workingDir = File(context.cacheDir, "restore_inspection_${System.currentTimeMillis()}")
                .apply { mkdirs() }
            val extractedDir = File(workingDir, "unzipped").apply { mkdirs() }
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    ZipHelper.unzip(input, extractedDir)
                } ?: return@withContext RestoreInspectionResult.Failure("Unable to open source backup")

                val metadata = BackupMetadata.fromFile(File(extractedDir, "backup_meta.json"))
                val currentDevice = DeviceSpecific.detectedDeviceIdentity()
                RestoreInspectionResult.Success(
                    metadata = metadata,
                    deviceChange = detectDeviceChange(metadata?.sourceDevice, currentDevice)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Restore inspection failed", e)
                RestoreInspectionResult.Failure(e.message ?: "Unable to inspect backup")
            } finally {
                workingDir.deleteRecursively()
            }
        }

    suspend fun restore(
        context: Context,
        sourceUri: Uri,
        importMode: ImportMode = ImportMode.UNCHANGED
    ): RestoreResult = withContext(Dispatchers.IO) {
        val workingDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val extractedDir = File(workingDir, "unzipped").apply { mkdirs() }

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipHelper.unzip(input, extractedDir)
            } ?: return@withContext RestoreResult.Failure("Unable to open source backup")

            val metadata = BackupMetadata.fromFile(File(extractedDir, "backup_meta.json"))
            val prefsDir = File(extractedDir, "prefs")
            val filesDir = File(extractedDir, "files")

            val prefsData = PreferencesBackupHelper.readPreferencesFromBackup(prefsDir)
            val fileSummary = FileBackupHelper.restoreFiles(context, filesDir)
            val excludedKeys = BackupPreferencePolicy.runtimeDerivedKeys +
                if (importMode == ImportMode.ADAPT_TO_CURRENT_DEVICE) {
                    BackupPreferencePolicy.targetDeviceDerivedKeys
                } else {
                    emptySet()
                }
            val prefsSummary = PreferencesBackupHelper.restorePreferences(
                context,
                prefsData,
                excludedKeys = excludedKeys,
                hasRestoredTypingSoundPack = fileSummary.restoredFiles.any { path ->
                    path.startsWith(
                        "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/" +
                            "${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}/"
                    )
                }
            )
            val postRestoreActions = collectTriggeredPostRestoreActions(prefsSummary, fileSummary)
            notifyPostRestoreEffects(context, postRestoreActions)

            RestoreResult.Success(
                metadata = metadata,
                preferencesSummary = prefsSummary,
                fileSummary = fileSummary,
                postActionsTriggered = postRestoreActions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreResult.Failure(e.message ?: "Restore failed")
        } finally {
            extractedDir.deleteRecursively()
            workingDir.deleteRecursively()
        }
    }

    internal fun detectDeviceChange(
        source: DeviceIdentitySnapshot?,
        target: DeviceIdentitySnapshot
    ): DeviceChange? {
        val sourceId = source?.stableId ?: return null
        val targetId = target.stableId ?: return null
        return if (sourceId != targetId) DeviceChange(source, target) else null
    }

    internal fun shouldNotifyUserDictionaryRefresh(
        preferencesSummary: PreferencesRestoreSummary,
        fileSummary: FileRestoreSummary
    ): Boolean {
        return collectTriggeredPostRestoreActions(preferencesSummary, fileSummary)
            .contains(PostRestoreAction.REFRESH_USER_DICTIONARY)
    }

    internal fun collectTriggeredPostRestoreActions(
        preferencesSummary: PreferencesRestoreSummary,
        fileSummary: FileRestoreSummary
    ): Set<PostRestoreAction> {
        val appliedPrefKeys = preferencesSummary.appliedKeys.toSet()
        val restoredFiles = fileSummary.restoredFiles

        return postRestoreTriggerRules.mapNotNullTo(linkedSetOf()) { rule ->
            val prefMatch = rule.matchingAppliedPrefKeys.any(appliedPrefKeys::contains)
            val fileMatch = rule.matchingRestoredFileNames.any { fileName ->
                restoredFiles.any { restored ->
                    restored == fileName || restored.endsWith("/$fileName")
                }
            }
            if (prefMatch || fileMatch) rule.action else null
        }
    }

    private fun notifyPostRestoreEffects(
        context: Context,
        actions: Set<PostRestoreAction>
    ) {
        Log.i(TAG, "Restore post-actions triggered: $actions")
        if (actions.isEmpty()) return

        actions.forEach { action ->
            when (action) {
                PostRestoreAction.REFRESH_USER_DICTIONARY -> {
                    val intent = Intent(AppBroadcastActions.USER_DICTIONARY_UPDATED).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                    Log.i(TAG, "Sent user dictionary refresh broadcast after restore")
                }
                PostRestoreAction.REGISTER_CUSTOM_INPUT_STYLES -> {
                    AdditionalSubtypeUtils.registerAdditionalSubtypes(context.applicationContext)
                    Log.i(TAG, "Registered custom input styles after restore")
                }
            }
        }
    }
}

sealed class RestoreInspectionResult {
    data class Success(
        val metadata: BackupMetadata?,
        val deviceChange: RestoreManager.DeviceChange?
    ) : RestoreInspectionResult()

    data class Failure(val reason: String) : RestoreInspectionResult()
}

sealed class RestoreResult {
    data class Success(
        val metadata: BackupMetadata?,
        val preferencesSummary: PreferencesRestoreSummary,
        val fileSummary: FileRestoreSummary,
        val postActionsTriggered: Set<RestoreManager.PostRestoreAction>
    ) : RestoreResult()

    data class Failure(val reason: String) : RestoreResult()
}
