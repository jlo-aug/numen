package it.palsoftware.pastiera.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import it.palsoftware.pastiera.AppBroadcastActions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestoreManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var userDefaultsFile: File
    private var originalUserDefaultsContent: String? = null
    private var originalUserDefaultsExisted: Boolean = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        userDefaultsFile = File(context.filesDir, "user_defaults.json")
        originalUserDefaultsExisted = userDefaultsFile.exists()
        originalUserDefaultsContent = userDefaultsFile.takeIf { it.exists() }?.readText()

        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("user_dictionary_entries")
            .remove("keyboard_layout")
            .remove("custom_input_styles")
            .remove("clicks_power_keyboard_snapshots_v1")
            .remove("clicks_power_soc_calibration_PK-42")
            .remove("physical_keyboard_profile_override")
            .remove("titan2_layout_enabled")
            .remove("titan2_elite_rounded_corner_insets")
            .remove("pastierina_mode_active")
            .remove("software_keyboard_mode_runtime_override")
            .commit()
        if (userDefaultsFile.exists()) {
            userDefaultsFile.delete()
        }
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("user_dictionary_entries")
            .remove("keyboard_layout")
            .remove("custom_input_styles")
            .remove("clicks_power_keyboard_snapshots_v1")
            .remove("clicks_power_soc_calibration_PK-42")
            .remove("physical_keyboard_profile_override")
            .remove("titan2_layout_enabled")
            .remove("titan2_elite_rounded_corner_insets")
            .remove("pastierina_mode_active")
            .remove("software_keyboard_mode_runtime_override")
            .commit()

        if (originalUserDefaultsExisted) {
            userDefaultsFile.parentFile?.mkdirs()
            userDefaultsFile.writeText(originalUserDefaultsContent ?: "{}")
        } else if (userDefaultsFile.exists()) {
            userDefaultsFile.delete()
        }
    }

    @Test
    fun restore_withUserDictionaryPref_appliesPrefAndSendsRefreshBroadcast() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "user_dictionary_entries" to PreferenceValue(
                            PreferenceValueType.STRING,
                            """["alpha","beta"]"""
                        )
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val broadcastCount = countUserDictionaryBroadcastsDuring {
            val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
            val success = result as RestoreResult.Success

            assertTrue(success.preferencesSummary.appliedKeys.contains("pastiera_prefs:user_dictionary_entries"))
            assertEquals(
                setOf(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY),
                success.postActionsTriggered
            )
            val restoredValue = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                .getString("user_dictionary_entries", null)
            assertEquals("""["alpha","beta"]""", restoredValue)
        }

        assertEquals(1, broadcastCount)
    }

    @Test
    fun restore_oldStyleBackupWithUserDefaultsFile_stillSendsRefreshBroadcast() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "keyboard_layout" to PreferenceValue(PreferenceValueType.STRING, "qwerty")
                    )
                )
            ),
            fileEntries = mapOf(
                "user_defaults.json" to JSONObject().put("hello", true).toString()
            )
        )

        val broadcastCount = countUserDictionaryBroadcastsDuring {
            val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
            val success = result as RestoreResult.Success

            assertTrue(success.fileSummary.restoredFiles.contains("user_defaults.json"))
            assertEquals(
                setOf(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY),
                success.postActionsTriggered
            )
            assertTrue(File(context.filesDir, "user_defaults.json").exists())
            assertEquals(
                "qwerty",
                context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                    .getString("keyboard_layout", null)
            )
        }

        assertEquals(1, broadcastCount)
    }

    @Test
    fun restore_oldStyleBackupWithoutUserDictionaryArtifacts_restoresWithoutRefreshBroadcast() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "keyboard_layout" to PreferenceValue(PreferenceValueType.STRING, "colemak")
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val broadcastCount = countUserDictionaryBroadcastsDuring {
            val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
            val success = result as RestoreResult.Success
            assertEquals(
                "colemak",
                context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                    .getString("keyboard_layout", null)
            )
            assertTrue(success.postActionsTriggered.isEmpty())
            assertTrue(success.fileSummary.restoredFiles.none { it.endsWith("user_defaults.json") })
        }

        assertEquals(0, broadcastCount)
    }

    @Test
    fun restore_customInputStyles_triggersRuntimeRegistration() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "custom_input_styles" to PreferenceValue(
                            PreferenceValueType.STRING,
                            "de:qwertz;ru:custom-russian"
                        )
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
        val success = result as RestoreResult.Success

        assertEquals(
            setOf(RestoreManager.PostRestoreAction.REGISTER_CUSTOM_INPUT_STYLES),
            success.postActionsTriggered
        )
        assertEquals(
            "de:qwertz;ru:custom-russian",
            context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                .getString("custom_input_styles", null)
        )
    }

    @Test
    fun restore_clicksPowerStateAndCalibration_onFreshInstall() {
        val snapshot = "[{\"deviceName\":\"Power Keyboard\"}]"
        val calibration = "3:8,4:9"

        val summary = PreferencesBackupHelper.restorePreferences(
            context,
            mapOf(
                "pastiera_prefs" to mapOf(
                    "clicks_power_keyboard_snapshots_v1" to PreferenceValue(
                        PreferenceValueType.STRING,
                        snapshot
                    ),
                    "clicks_power_soc_calibration_PK-42" to PreferenceValue(
                        PreferenceValueType.STRING,
                        calibration
                    )
                )
            )
        )

        assertTrue(summary.skippedKeys.isEmpty())
        assertEquals(
            setOf(
                "pastiera_prefs:clicks_power_keyboard_snapshots_v1",
                "pastiera_prefs:clicks_power_soc_calibration_PK-42"
            ),
            summary.appliedKeys.toSet()
        )
        val preferences = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        assertEquals(snapshot, preferences.getString("clicks_power_keyboard_snapshots_v1", null))
        assertEquals(calibration, preferences.getString("clicks_power_soc_calibration_PK-42", null))
    }

    @Test
    fun restore_existingUnknownKeyIsSkippedWhileRestoreOnlyLegacyKeyIsApplied() {
        val preferences = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        val unknownKey = "unclassified_existing_restore_test"
        val restoreOnlyKey = "restore_sym_page"
        preferences.edit()
            .putString(unknownKey, "keep-existing")
            .remove(restoreOnlyKey)
            .commit()

        try {
            val summary = PreferencesBackupHelper.restorePreferences(
                context,
                mapOf(
                    "pastiera_prefs" to mapOf(
                        unknownKey to PreferenceValue(PreferenceValueType.STRING, "backup-value"),
                        restoreOnlyKey to PreferenceValue(PreferenceValueType.INT, 4)
                    )
                )
            )

            assertEquals(listOf("pastiera_prefs:$restoreOnlyKey"), summary.appliedKeys)
            assertEquals(listOf("pastiera_prefs:$unknownKey"), summary.skippedKeys)
            assertEquals("keep-existing", preferences.getString(unknownKey, null))
            assertEquals(4, preferences.getInt(restoreOnlyKey, -1))
        } finally {
            preferences.edit().remove(unknownKey).remove(restoreOnlyKey).commit()
        }
    }

    @Test
    fun inspect_doesNotApplyPreferences() = runBlocking {
        val preferences = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        preferences.edit().putString("keyboard_layout", "qwertz").commit()
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "keyboard_layout" to PreferenceValue(PreferenceValueType.STRING, "azerty")
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val result = RestoreManager.inspect(context, Uri.fromFile(backupZip))

        assertTrue(result is RestoreInspectionResult.Success)
        assertEquals("qwertz", preferences.getString("keyboard_layout", null))
    }

    @Test
    fun adaptiveRestore_preservesTargetDeviceValuesButImportsPortableSettings() = runBlocking {
        val preferences = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        preferences.edit()
            .putString("physical_keyboard_profile_override", "auto")
            .putBoolean("titan2_layout_enabled", true)
            .putBoolean("titan2_elite_rounded_corner_insets", true)
            .putBoolean("pastierina_mode_active", false)
            .putString("software_keyboard_mode_runtime_override", "force_hardware")
            .commit()
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "physical_keyboard_profile_override" to PreferenceValue(
                            PreferenceValueType.STRING,
                            "titan2"
                        ),
                        "titan2_layout_enabled" to PreferenceValue(PreferenceValueType.BOOLEAN, false),
                        "titan2_elite_rounded_corner_insets" to PreferenceValue(
                            PreferenceValueType.BOOLEAN,
                            false
                        ),
                        "pastierina_mode_active" to PreferenceValue(PreferenceValueType.BOOLEAN, true),
                        "software_keyboard_mode_runtime_override" to PreferenceValue(
                            PreferenceValueType.STRING,
                            "force_virtual"
                        ),
                        "keyboard_layout" to PreferenceValue(PreferenceValueType.STRING, "azerty")
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val result = RestoreManager.restore(
            context,
            Uri.fromFile(backupZip),
            RestoreManager.ImportMode.ADAPT_TO_CURRENT_DEVICE
        ) as RestoreResult.Success

        assertEquals("auto", preferences.getString("physical_keyboard_profile_override", null))
        assertTrue(preferences.getBoolean("titan2_layout_enabled", false))
        assertTrue(preferences.getBoolean("titan2_elite_rounded_corner_insets", false))
        assertFalse(preferences.getBoolean("pastierina_mode_active", true))
        assertEquals(
            "force_hardware",
            preferences.getString("software_keyboard_mode_runtime_override", null)
        )
        assertEquals("azerty", preferences.getString("keyboard_layout", null))
        assertTrue(
            result.preferencesSummary.skippedKeys.containsAll(
                BackupPreferencePolicy.runtimeDerivedKeys + BackupPreferencePolicy.targetDeviceDerivedKeys
            )
        )
    }

    private suspend fun countUserDictionaryBroadcastsDuring(block: suspend () -> Unit): Int {
        var count = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AppBroadcastActions.USER_DICTIONARY_UPDATED) {
                    count++
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(AppBroadcastActions.USER_DICTIONARY_UPDATED))
        try {
            block()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            context.unregisterReceiver(receiver)
        }
        return count
    }

    private fun createBackupZip(
        includeMetadata: Boolean,
        prefsFiles: Map<String, String>,
        fileEntries: Map<String, String>
    ): File {
        val zipFile = File.createTempFile("restore_test_", ".zip", context.cacheDir)
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            if (includeMetadata) {
                val metadata = BackupMetadata(
                    versionCode = 1,
                    versionName = "legacy-test",
                    timestamp = "2026-02-25T00:00:00Z",
                    components = (prefsFiles.keys.map { "prefs/$it" } + fileEntries.keys.map { "files/$it" }).sorted()
                )
                addZipEntry(zipOut, "backup_meta.json", metadata.toJsonString())
            }
            prefsFiles.forEach { (name, content) ->
                addZipEntry(zipOut, "prefs/$name", content)
            }
            fileEntries.forEach { (relativePath, content) ->
                addZipEntry(zipOut, "files/$relativePath", content)
            }
        }
        return zipFile
    }

    private fun prefsBackupJson(
        prefName: String,
        entries: Map<String, PreferenceValue>
    ): String {
        val json = JSONObject()
        json.put("name", prefName)
        val entriesJson = JSONObject()
        entries.forEach { (key, value) ->
            entriesJson.put(key, value.toJson())
        }
        json.put("entries", entriesJson)
        return json.toString(2)
    }

    private fun addZipEntry(zipOut: ZipOutputStream, path: String, content: String) {
        zipOut.putNextEntry(ZipEntry(path))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }
}
