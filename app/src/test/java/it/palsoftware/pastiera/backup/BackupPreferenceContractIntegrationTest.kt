package it.palsoftware.pastiera.backup

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import it.palsoftware.pastiera.SettingsManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaExtractor
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.reflect.Modifier
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupPreferenceContractIntegrationTest {

    private companion object {
        const val TEST_AUDIO_DATA_SOURCE = "test://bundled-real-ogg"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listOf(
            "pastiera_prefs",
            "app_list_cache_prefs",
            "perf_img_scale",
            "recent_emojis_prefs"
        ).forEach { prefName ->
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.filesDir, SettingsManager.TYPING_SOUND_CUSTOM_DIR).deleteRecursively()
        val audioBytes = bundledOggBytes()
        DataSource.setFileDescriptorTransform { _, _ -> TEST_AUDIO_DATA_SOURCE }
        ShadowMediaExtractor.addTrack(
            DataSource.toDataSource(TEST_AUDIO_DATA_SOURCE),
            MediaFormat.createAudioFormat("audio/vorbis", 44_100, 1),
            audioBytes
        )
    }

    @After
    fun tearDown() {
        ShadowMediaExtractor.reset()
        DataSource.reset()
    }

    @Test
    fun richExport_restoresFreshUserStateWithZeroSkippedKeys() = runBlocking {
        val hardwareTheme = SettingsManager.defaultKeyboardTheme().copy(
            background = 0xFF102030.toInt(),
            accent = 0xFF405060.toInt()
        )
        val softwareTheme = SettingsManager.defaultKeyboardTheme().copy(
            background = 0xFF607080.toInt(),
            ortholinear = true,
            showLeds = false
        )
        val savedTheme = SettingsManager.defaultKeyboardTheme().copy(
            background = 0xFF112233.toInt()
        )
        val draftTheme = SettingsManager.defaultKeyboardTheme().copy(
            background = 0xFF445566.toInt()
        )
        val enterOverride = SettingsManager.AppEnterBehaviorOverride(
            packageName = "example.transfer.test",
            behavior = SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE
        )

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, hardwareTheme)
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, softwareTheme)
        SettingsManager.saveKeyboardTheme(context, "Transfer library", savedTheme)
        SettingsManager.saveKeyboardThemeDraft(
            context,
            SettingsManager.KeyboardThemeDraft(
                name = "Transfer draft",
                theme = draftTheme,
                populatedFields = setOf("background")
            )
        )
        SettingsManager.setKeyboardLayoutAutoByLocale(context, false)
        SettingsManager.setAppEnterBehaviorEnabled(context, true)
        SettingsManager.setAppEnterBehaviorPreset(
            context,
            SettingsManager.ENTER_BEHAVIOR_PRESET_CUSTOM
        )
        SettingsManager.setAppEnterBehaviorOverrides(context, listOf(enterOverride))
        SettingsManager.setNavModeCtrlHoldEnabled(context, true)
        SettingsManager.setDynamicVariationBarResizeToContent(context, true)
        SettingsManager.setTypingSoundMode(context, SettingsManager.TYPING_SOUND_MODE_CUSTOM)
        SettingsManager.setTypingSoundOutputMode(context, SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION)
        val typingSoundFile = File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}/normal/001.ogg"
        ).apply {
            parentFile?.mkdirs()
        }
        val typingSoundBytes = writeRealOgg(typingSoundFile)
        SettingsManager.getPreferences(context).edit()
            .putString("user_dictionary_entries", "[\"ContractWord\"]")
            .putString("auto_correct_custom_de", "{\"ctr\":\"ContractReplacement\"}")
            .putString("app_language_tag", "de-DE")
            .putBoolean("alt_shift_default_initialized", true)
            .putInt("current_sym_page", 2)
            .putBoolean("legacy_german_qwertz_default_migrated", true)
            .putInt("last_seen_whats_new_version", 86)
            .putInt("nav_mode_default_mappings_version", 3)
            .putBoolean("quick_launcher_default_assigned", true)
            .putFloat("keyboard_theme_preview_viewport_scale", 1.4f)
            .putBoolean("pastierina_mode_active", true)
            .putString(
                SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME,
                SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR
            )
            .putString(SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, "Contract sounds.zip")
            .putLong(SettingsManager.KEY_TYPING_SOUND_UPDATED_AT, 123L)
            .commit()
        context.getSharedPreferences("app_list_cache_prefs", Context.MODE_PRIVATE).edit()
            .putInt("package_change_sequence", 42)
            .commit()
        context.getSharedPreferences("perf_img_scale", Context.MODE_PRIVATE).edit()
            .putInt("2131230908", 3)
            .commit()

        val backupZip = File.createTempFile("preference_contract_", ".zip", context.cacheDir)
        val backupResult = BackupManager.createBackup(context, Uri.fromFile(backupZip))
        assertTrue(backupResult is BackupResult.Success)

        val zipEntries = readZipEntries(backupZip)
        assertTrue(zipEntries.containsKey("prefs/pastiera_prefs.json"))
        assertFalse(zipEntries.containsKey("prefs/app_list_cache_prefs.json"))
        assertFalse(zipEntries.containsKey("prefs/perf_img_scale.json"))
        assertFalse(zipEntries.containsKey("prefs/recent_emojis_prefs.json"))
        assertArrayEquals(
            typingSoundBytes,
            readZipEntryBytes(backupZip, "files/typing_sounds/custom_pack/normal/001.ogg")
        )
        val exportedEntries = JSONObject(zipEntries.getValue("prefs/pastiera_prefs.json"))
            .getJSONObject("entries")
        BackupPreferenceContract.deliberatelyExcludedPastieraKeys.keys.forEach { excludedKey ->
            assertFalse("Excluded key was exported: $excludedKey", exportedEntries.has(excludedKey))
        }
        assertFalse(exportedEntries.has(SettingsManager.KEY_TYPING_SOUND_UPDATED_AT))

        SettingsManager.getPreferences(context).edit().clear().commit()
        File(context.filesDir, SettingsManager.TYPING_SOUND_CUSTOM_DIR).deleteRecursively()
        val restoreResult = RestoreManager.restore(context, Uri.fromFile(backupZip))
        val restored = restoreResult as RestoreResult.Success

        assertTrue(restored.preferencesSummary.skippedKeys.isEmpty())
        assertEquals(
            exportedEntries.keys().asSequence().map { "pastiera_prefs:$it" }.toSet(),
            restored.preferencesSummary.appliedKeys.toSet()
        )
        assertEquals(
            hardwareTheme,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        )
        assertEquals(
            softwareTheme,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE)
        )
        assertEquals(
            listOf(SettingsManager.NamedKeyboardTheme("Transfer library", savedTheme)),
            SettingsManager.getSavedKeyboardThemes(context)
        )
        assertEquals(
            listOf(
                SettingsManager.KeyboardThemeDraft(
                    "Transfer draft",
                    draftTheme,
                    setOf("background")
                )
            ),
            SettingsManager.getKeyboardThemeDrafts(context)
        )
        assertFalse(SettingsManager.isKeyboardLayoutAutoByLocale(context))
        assertTrue(SettingsManager.getAppEnterBehaviorEnabled(context))
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_PRESET_CUSTOM,
            SettingsManager.getAppEnterBehaviorPreset(context)
        )
        assertEquals(listOf(enterOverride), SettingsManager.getAppEnterBehaviorOverrides(context))
        assertTrue(SettingsManager.getNavModeCtrlHoldEnabled(context))
        assertTrue(SettingsManager.getDynamicVariationBarResizeToContent(context))
        assertEquals(
            "[\"ContractWord\"]",
            SettingsManager.getPreferences(context).getString("user_dictionary_entries", null)
        )
        assertEquals(
            "{\"ctr\":\"ContractReplacement\"}",
            SettingsManager.getPreferences(context).getString("auto_correct_custom_de", null)
        )
        assertEquals("de-DE", SettingsManager.getAppLanguageTag(context))
        assertEquals(SettingsManager.TYPING_SOUND_MODE_CUSTOM, SettingsManager.getTypingSoundMode(context))
        assertEquals(
            SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION,
            SettingsManager.getTypingSoundOutputMode(context)
        )
        assertEquals("Contract sounds.zip", SettingsManager.getTypingSoundCustomDisplayName(context))
        assertArrayEquals(
            typingSoundBytes,
            SettingsManager.getTypingSoundCustomGroupFiles(context).getValue("normal").single().readBytes()
        )
        assertFalse(SettingsManager.getPreferences(context).contains(SettingsManager.KEY_TYPING_SOUND_UPDATED_AT))
        assertTrue(typingSoundFile.parentFile?.isDirectory == true)
    }

    @Test
    fun observedTransferKeys_haveExplicitTypesAndInternalKeysAreClassified() {
        val expectedUserTypes = mapOf(
            "keyboard_theme_hardware" to PreferenceValueType.STRING,
            "keyboard_theme_software" to PreferenceValueType.STRING,
            "keyboard_theme_saved_themes" to PreferenceValueType.STRING,
            "keyboard_theme_drafts" to PreferenceValueType.STRING,
            "keyboard_layout_auto_by_locale" to PreferenceValueType.BOOLEAN,
            "app_enter_behavior_enabled" to PreferenceValueType.BOOLEAN,
            "app_enter_behavior_preset" to PreferenceValueType.STRING,
            "app_enter_behavior_overrides" to PreferenceValueType.STRING,
            "nav_mode_ctrl_hold_enabled" to PreferenceValueType.BOOLEAN,
            "dynamic_variation_bar_resize_to_content" to PreferenceValueType.BOOLEAN,
            "typing_sound_mode" to PreferenceValueType.STRING,
            "typing_sound_output_mode" to PreferenceValueType.STRING,
            "typing_sound_custom_file_name" to PreferenceValueType.STRING,
            "typing_sound_custom_display_name" to PreferenceValueType.STRING
        )

        expectedUserTypes.forEach { (key, type) ->
            assertEquals(type, BackupPreferenceContract.expectedExportType("pastiera_prefs", key))
            assertTrue(BackupPreferenceContract.isExportable("pastiera_prefs", key))
            assertEquals(type, PreferenceSchemas.expectedType("pastiera_prefs", key))
        }
        listOf(
            "alt_shift_default_initialized",
            "current_sym_page",
            "legacy_german_qwertz_default_migrated",
            "titan2_elite_rounded_corners_enforced_v1",
            "last_seen_whats_new_version",
            "nav_mode_default_mappings_version",
            "quick_launcher_default_assigned",
            "keyboard_theme_preview_viewport_scale",
            "typing_sound_updated_at"
        ).forEach { key ->
            assertTrue(BackupPreferenceContract.deliberatelyExcludedPastieraKeys.containsKey(key))
            assertFalse(BackupPreferenceContract.isExportable("pastiera_prefs", key))
        }
        assertEquals(
            PreferenceValueType.STRING,
            BackupPreferenceContract.expectedExportType("pastiera_prefs", "auto_correct_custom_de")
        )
    }

    @Test
    fun everySettingsManagerPreferenceKey_isExportedOrDeliberatelyExcluded() {
        val preferenceKeys = SettingsManager::class.java.declaredFields
            .asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    field.name.startsWith("KEY_") &&
                    field.type == String::class.java
            }
            .map { field ->
                field.isAccessible = true
                field.get(null) as String
            }
            .toSet()

        val unclassified = preferenceKeys.filterNot { key ->
            BackupPreferenceContract.isExportable("pastiera_prefs", key) ||
                BackupPreferenceContract.deliberatelyExcludedPastieraKeys.containsKey(key)
        }

        assertTrue("Unclassified SettingsManager KEY_* values: $unclassified", unclassified.isEmpty())
    }

    @Test
    fun typingSoundRestore_rejectsUnsupportedPackBeforeTargetMutation() {
        val extractedFiles = File(context.cacheDir, "typing_sound_invalid_restore").apply {
            deleteRecursively()
        }
        val invalid = File(extractedFiles, "typing_sounds/custom_pack/normal/payload.exe").apply {
            parentFile?.mkdirs()
            writeText("not-a-supported-audio-file")
        }
        val targetPack = File(context.filesDir, SettingsManager.TYPING_SOUND_CUSTOM_DIR)

        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileBackupHelper.restoreFiles(context, extractedFiles)
            }
            assertTrue(invalid.exists())
            assertFalse(targetPack.exists())
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun typingSoundRestore_rejectsCorruptSupportedExtensionBeforeTargetMutation() {
        val extractedFiles = File(context.cacheDir, "typing_sound_corrupt_restore").apply {
            deleteRecursively()
        }
        File(extractedFiles, "typing_sounds/custom_pack/normal/001.ogg").apply {
            parentFile?.mkdirs()
            writeText("not actually an ogg stream")
        }
        val targetPack = File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}"
        )
        val originalTarget = File(targetPack, "normal/original.ogg")
        val originalBytes = writeRealOgg(originalTarget)

        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileBackupHelper.restoreFiles(context, extractedFiles)
            }
            assertArrayEquals(originalBytes, originalTarget.readBytes())
            assertEquals(setOf("normal/original.ogg"), targetPack.walkTopDown()
                .filter(File::isFile)
                .map { it.toRelativeString(targetPack).replace("\\", "/") }
                .toSet())
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun typingSoundRestore_rejectsEmptySupportedExtensionBeforeTargetMutation() {
        val extractedFiles = File(context.cacheDir, "typing_sound_empty_restore").apply {
            deleteRecursively()
        }
        File(extractedFiles, "typing_sounds/custom_pack/normal/001.ogg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf())
        }

        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileBackupHelper.restoreFiles(context, extractedFiles)
            }
            assertFalse(File(context.filesDir, SettingsManager.TYPING_SOUND_CUSTOM_DIR).exists())
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun typingSoundRestore_rejectsTruncatedOggHeaderBeforeTargetMutation() {
        val extractedFiles = File(context.cacheDir, "typing_sound_truncated_restore").apply {
            deleteRecursively()
        }
        File(extractedFiles, "typing_sounds/custom_pack/normal/001.ogg").apply {
            parentFile?.mkdirs()
            writeBytes("OggS\u0000\u0002truncated-vorbis".toByteArray())
        }
        ShadowMediaExtractor.reset()

        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileBackupHelper.restoreFiles(context, extractedFiles)
            }
            assertFalse(File(context.filesDir, SettingsManager.TYPING_SOUND_CUSTOM_DIR).exists())
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun typingSoundValidator_rejectsHeaderOnlyAudioContainers() {
        ShadowMediaExtractor.reset()
        val fixtures = mapOf(
            "header-only.ogg" to "OggS\u0000\u0002truncated-vorbis".toByteArray(),
            "header-only.wav" to "RIFF0000WAVEfmt ".toByteArray(),
            "header-only.mp3" to byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x00),
            "header-only.m4a" to "0000ftypisom".toByteArray()
        )

        fixtures.forEach { (name, bytes) ->
            val file = File(context.cacheDir, name).apply { writeBytes(bytes) }
            try {
                assertFalse(name, TypingSoundAudioValidator.hasSupportedAudioContent(file))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun typingSoundRestore_rejectsFileAboveImportLimit() {
        val extractedFiles = File(context.cacheDir, "typing_sound_oversized_restore").apply {
            deleteRecursively()
        }
        val oversized = File(extractedFiles, "typing_sounds/custom_pack/normal/001.ogg").apply {
            parentFile?.mkdirs()
        }
        RandomAccessFile(oversized, "rw").use { file ->
            file.setLength(SettingsManager.TYPING_SOUND_MAX_FILE_BYTES + 1L)
        }

        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileBackupHelper.restoreFiles(context, extractedFiles)
            }
            assertFalse(File(context.filesDir, SettingsManager.TYPING_SOUND_CUSTOM_DIR).exists())
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun activeCustomTypingSound_withoutValidPackFailsBackup() = runBlocking {
        SettingsManager.setTypingSoundMode(context, SettingsManager.TYPING_SOUND_MODE_CUSTOM)
        SettingsManager.getPreferences(context).edit()
            .putString(
                SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME,
                SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR
            )
            .putString(SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, "Missing.zip")
            .commit()
        val missingPackBackup = File.createTempFile("missing_typing_sound_", ".zip", context.cacheDir)

        val missingResult = BackupManager.createBackup(context, Uri.fromFile(missingPackBackup))

        assertTrue(missingResult is BackupResult.Failure)

        File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}/normal/payload.exe"
        ).apply {
            parentFile?.mkdirs()
            writeText("invalid")
        }
        val invalidPackBackup = File.createTempFile("invalid_typing_sound_", ".zip", context.cacheDir)

        val invalidResult = BackupManager.createBackup(context, Uri.fromFile(invalidPackBackup))

        assertTrue(invalidResult is BackupResult.Failure)
    }

    @Test
    fun activeCustomTypingSound_withCorruptAllowedExtensionFailsBackup() = runBlocking {
        SettingsManager.setTypingSoundMode(context, SettingsManager.TYPING_SOUND_MODE_CUSTOM)
        SettingsManager.getPreferences(context).edit()
            .putString(
                SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME,
                SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR
            )
            .putString(SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, "Corrupt.zip")
            .commit()
        File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}/normal/001.ogg"
        ).apply {
            parentFile?.mkdirs()
            writeText("not actually an ogg stream")
        }

        val result = BackupManager.createBackup(
            context,
            Uri.fromFile(File.createTempFile("corrupt_typing_sound_", ".zip", context.cacheDir))
        )

        assertTrue(result is BackupResult.Failure)
    }

    @Test
    fun backupAboveRestoreEntryLimitReturnsFailure() = runBlocking {
        val generatedLayoutDir = File(context.filesDir, "keyboard_layouts/zip-limit-test")
        val backupFile = File.createTempFile("entry_limit_backup_", ".zip", context.cacheDir)
        try {
            repeat(ZipHelper.DEFAULT_ARCHIVE_LIMITS.maxEntries + 1) { index ->
                File(generatedLayoutDir, "$index.json").apply {
                    parentFile?.mkdirs()
                    writeText("{}")
                }
            }

            val result = BackupManager.createBackup(context, Uri.fromFile(backupFile))

            assertTrue(result is BackupResult.Failure)
        } finally {
            generatedLayoutDir.deleteRecursively()
            backupFile.delete()
        }
    }

    @Test
    fun inactiveInvalidTypingSoundPack_isOmittedWithoutFailingBackup() = runBlocking {
        SettingsManager.setTypingSoundMode(context, SettingsManager.TYPING_SOUND_MODE_CLICK)
        SettingsManager.setTypingSoundOutputMode(context, SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM)
        SettingsManager.getPreferences(context).edit()
            .putString(
                SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME,
                SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR
            )
            .putString(SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, "Orphaned.zip")
            .commit()
        File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}/normal/payload.exe"
        ).apply {
            parentFile?.mkdirs()
            writeText("invalid")
        }
        val backupZip = File.createTempFile("inactive_typing_sound_", ".zip", context.cacheDir)

        val result = BackupManager.createBackup(context, Uri.fromFile(backupZip))
        val entries = readZipEntries(backupZip)
        val exportedPrefs = JSONObject(entries.getValue("prefs/pastiera_prefs.json"))
            .getJSONObject("entries")

        assertTrue(result is BackupResult.Success)
        assertTrue(entries.keys.none { it.startsWith("files/typing_sounds/") })
        assertEquals(
            SettingsManager.TYPING_SOUND_MODE_CLICK,
            exportedPrefs.getJSONObject(SettingsManager.KEY_TYPING_SOUND_MODE).getString("value")
        )
        assertEquals(
            SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM,
            exportedPrefs.getJSONObject(SettingsManager.KEY_TYPING_SOUND_OUTPUT_MODE).getString("value")
        )
        assertFalse(exportedPrefs.has(SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME))
        assertFalse(exportedPrefs.has(SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME))
    }

    @Test
    fun restoreWithoutPack_doesNotApplyCustomPrefsFromTargetState() {
        val preferences = SettingsManager.getPreferences(context)
        File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}/normal/001.ogg"
        ).apply {
            parentFile?.mkdirs()
            writeText("existing-pack")
        }
        preferences.edit()
            .putString(SettingsManager.KEY_TYPING_SOUND_MODE, SettingsManager.TYPING_SOUND_MODE_CUSTOM)
            .putString(
                SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME,
                SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR
            )
            .putString(SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, "Existing.zip")
            .commit()

        val summary = PreferencesBackupHelper.restorePreferences(
            context,
            mapOf(
                "pastiera_prefs" to mapOf(
                    SettingsManager.KEY_TYPING_SOUND_MODE to PreferenceValue(
                        PreferenceValueType.STRING,
                        SettingsManager.TYPING_SOUND_MODE_CUSTOM
                    ),
                    SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME to PreferenceValue(
                        PreferenceValueType.STRING,
                        SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR
                    ),
                    SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME to PreferenceValue(
                        PreferenceValueType.STRING,
                        "Backup-without-pack.zip"
                    ),
                    SettingsManager.KEY_TYPING_SOUND_OUTPUT_MODE to PreferenceValue(
                        PreferenceValueType.STRING,
                        SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION
                    )
                )
            ),
            hasRestoredTypingSoundPack = false
        )

        assertEquals(
            setOf("pastiera_prefs:${SettingsManager.KEY_TYPING_SOUND_OUTPUT_MODE}"),
            summary.appliedKeys.toSet()
        )
        assertEquals(
            setOf(
                "pastiera_prefs:${SettingsManager.KEY_TYPING_SOUND_MODE}",
                "pastiera_prefs:${SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME}",
                "pastiera_prefs:${SettingsManager.KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME}"
            ),
            summary.skippedKeys.toSet()
        )
        assertEquals("Existing.zip", SettingsManager.getTypingSoundCustomDisplayName(context))
        assertEquals(SettingsManager.TYPING_SOUND_MODE_CUSTOM, SettingsManager.getTypingSoundMode(context))
        assertEquals(
            SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION,
            SettingsManager.getTypingSoundOutputMode(context)
        )
    }

    @Test
    fun typingSoundRestore_replacesExistingPackAsAUnit() {
        val extractedFiles = File(context.cacheDir, "typing_sound_replace_restore").apply {
            deleteRecursively()
        }
        val newNormalBytes = writeRealOgg(
            File(extractedFiles, "typing_sounds/custom_pack/normal/001.ogg")
        )
        val targetPack = File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}"
        )
        File(targetPack, "normal/old.ogg").apply {
            parentFile?.mkdirs()
            writeText("old-normal")
        }
        File(targetPack, "space/old.wav").apply {
            parentFile?.mkdirs()
            writeText("old-space")
        }

        try {
            val summary = FileBackupHelper.restoreFiles(context, extractedFiles)

            assertEquals(
                setOf("typing_sounds/custom_pack/normal/001.ogg"),
                summary.restoredFiles.toSet()
            )
            assertEquals(
                setOf("normal/001.ogg"),
                targetPack.walkTopDown()
                    .filter(File::isFile)
                    .map { it.toRelativeString(targetPack).replace("\\", "/") }
                    .toSet()
            )
            assertArrayEquals(newNormalBytes, File(targetPack, "normal/001.ogg").readBytes())
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun typingSoundPackInstallFailure_restoresPreviousPackExactly() {
        val extractedFiles = File(context.cacheDir, "typing_sound_rollback_restore").apply {
            deleteRecursively()
        }
        writeRealOgg(File(extractedFiles, "typing_sounds/custom_pack/normal/001.ogg"))
        val targetPack = File(
            context.filesDir,
            "${SettingsManager.TYPING_SOUND_CUSTOM_DIR}/${SettingsManager.TYPING_SOUND_CUSTOM_PACK_DIR}"
        )
        File(targetPack, "normal/old.ogg").apply {
            parentFile?.mkdirs()
            writeText("old-normal")
        }
        File(targetPack, "space/old.wav").apply {
            parentFile?.mkdirs()
            writeText("old-space")
        }

        try {
            assertThrows(IOException::class.java) {
                FileBackupHelper.replaceTypingSoundPack(
                    targetRoot = context.filesDir,
                    rollbackRoot = context.cacheDir,
                    extractedFilesRoot = extractedFiles,
                    installStagedPack = { _, _ -> throw IOException("synthetic install failure") }
                )
            }

            assertEquals(
                mapOf(
                    "normal/old.ogg" to "old-normal",
                    "space/old.wav" to "old-space"
                ),
                targetPack.walkTopDown()
                    .filter(File::isFile)
                    .associate { file ->
                        file.toRelativeString(targetPack).replace("\\", "/") to file.readText()
                    }
            )
        } finally {
            extractedFiles.deleteRecursively()
        }
    }

    @Test
    fun everyPastieraKeyObservedInIssue179Backup_isExplicitlyExportedOrExcluded() {
        val observedKeys = """
            alt_shift_default_initialized
            alt_shift_layout_switch
            app_enter_behavior_enabled
            app_enter_behavior_overrides
            app_enter_behavior_preset
            auto_capitalize_first_letter
            auto_correct_custom_de
            auto_correct_custom_fr
            auto_correct_enabled_languages
            auto_replace_on_space_enter
            auto_space_punctuation
            comma_space
            current_sym_page
            custom_input_styles
            dismissed_releases
            dynamic_variation_bar_resize_to_content
            french_punctuation_only_french
            french_punctuation_spacing
            input_style_suggestion_locales
            keyboard_layout
            keyboard_layout_auto_by_locale
            keyboard_layout_list
            keyboard_theme_hardware
            keyboard_theme_saved_themes
            last_seen_whats_new_version
            launcher_shortcuts
            launcher_shortcuts_enabled
            legacy_german_qwertz_default_migrated
            long_press_modifier
            long_press_threshold
            mid_word_quote_to_apostrophe
            modifier_indicator_mode
            nav_mode_ctrl_hold_enabled
            nav_mode_default_mappings_version
            nav_mode_mappings_updated
            pastierina_mode_override
            physical_keyboard_currency_symbol
            physical_keyboard_profile_override
            quick_launcher_animation_duration_ms
            quick_launcher_auto_start_single
            quick_launcher_behavior
            quick_launcher_default_assigned
            quick_launcher_limit_results
            shift_backspace_delete
            smart_quotes
            software_keyboard_mode
            space_after_punctuation
            spaced_hyphen_to_en_dash
            static_variation_bar_base_layer_enabled
            static_variation_bar_mode
            static_variation_bar_preset
            sym_mappings_custom
            titan2_elite_rounded_corner_insets
            trackpad_gestures_enabled
            tutorial_completed
            user_dictionary_entries
            variations_updated
        """.trimIndent().lineSequence().filter(String::isNotBlank).toSet()

        val unclassified = observedKeys.filterNot { key ->
            BackupPreferenceContract.isExportable("pastiera_prefs", key) ||
                BackupPreferenceContract.deliberatelyExcludedPastieraKeys.containsKey(key)
        }

        assertTrue("Unclassified observed keys: $unclassified", unclassified.isEmpty())
    }

    private fun readZipEntries(zipFile: File): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(FileInputStream(zipFile)).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zipInput.readBytes().toString(Charsets.UTF_8)
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
        return entries
    }

    private fun readZipEntryBytes(zipFile: File, entryName: String): ByteArray {
        ZipInputStream(FileInputStream(zipFile)).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryName) {
                    return zipInput.readBytes()
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
        throw AssertionError("Missing ZIP entry: $entryName")
    }

    private fun writeRealOgg(target: File): ByteArray {
        target.parentFile?.mkdirs()
        val bytes = bundledOggBytes()
        target.writeBytes(bytes)
        return bytes
    }

    private fun bundledOggBytes(): ByteArray {
        val resourceId = context.resources.getIdentifier(
            "typing_click_normal_1",
            "raw",
            context.packageName
        )
        assertTrue("Bundled OGG fixture is missing", resourceId != 0)
        return context.resources.openRawResource(resourceId).use { it.readBytes() }
    }
}
