package it.palsoftware.pastiera

import it.palsoftware.pastiera.backup.PreferenceSchemas
import it.palsoftware.pastiera.backup.PreferenceValueType
import org.junit.Assert.assertEquals
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerKeyboardThemeAssignmentTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("+notnight")
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun fixedMode_returnsFixedTargetTheme_evenWhenSystemSlotsExist() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF101010.toInt())
        val light = theme(0xFFFFFFFF.toInt())
        val dark = theme(0xFF000000.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = false, light)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = true, dark)

        assertEquals(fixed.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).background)
    }

    @Test
    fun followSystem_usesLightAndDarkSlotsForTarget() {
        val context = RuntimeEnvironment.getApplication()
        val light = theme(0xFFEFEFEF.toInt())
        val dark = theme(0xFF111111.toInt())

        SettingsManager.setKeyboardThemeAssignmentMode(
            context,
            SettingsManager.KeyboardThemeTarget.SOFTWARE,
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        )
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, dark = false, light)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, dark = true, dark)

        RuntimeEnvironment.setQualifiers("+notnight")
        assertEquals(light.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background)

        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(dark.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background)
    }

    @Test
    fun legacyOverrideForQwerty_becomesFixedTheme_evenOverFollowSystem() {
        val context = RuntimeEnvironment.getApplication()
        val dark = theme(0xFF111111.toInt())
        val override = theme(0xFFABCDEF.toInt())

        SettingsManager.setKeyboardThemeAssignmentMode(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        )
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = true, dark)
        putLegacyOverrides("hardware", legacyOverride("en-US", "qwerty", override))
        RuntimeEnvironment.setQualifiers("+night")

        SettingsManager.migrateKeyboardThemeLayoutOverrides(context)

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).background
        )
        assertEquals(
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED,
            SettingsManager.getKeyboardThemeAssignmentMode(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        )
        assertFalse(prefs().contains("keyboard_theme_layout_overrides_hardware"))
    }

    @Test
    fun legacyOverrides_mostSpecificMatchWins_andOtherLayoutsAreIgnored() {
        val context = RuntimeEnvironment.getApplication()
        val layoutOnly = theme(0xFF111111.toInt())
        val languageAndLayout = theme(0xFF222222.toInt())
        val exactLocale = theme(0xFF333333.toInt())
        val otherLayout = theme(0xFF444444.toInt())

        putLegacyOverrides(
            "software",
            legacyOverride(null, "qwerty", layoutOnly),
            legacyOverride("de-DE", "qwertz", otherLayout),
            legacyOverride("en_US", null, exactLocale),
            legacyOverride("en", "qwerty", languageAndLayout)
        )

        SettingsManager.migrateKeyboardThemeLayoutOverrides(context)

        assertEquals(
            exactLocale.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background
        )
    }

    @Test
    fun legacyOverridesForOtherLayoutsOnly_leaveThemeUnchanged() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        putLegacyOverrides("hardware", legacyOverride("de-DE", "qwertz", theme(0xFF222222.toInt())))

        SettingsManager.migrateKeyboardThemeLayoutOverrides(context)

        assertEquals(
            fixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).background
        )
        assertFalse(prefs().contains("keyboard_theme_layout_overrides_hardware"))
    }

    @Test
    fun malformedLegacyOverrideJson_isDroppedWithoutCrashing() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        prefs().edit()
            .putString("keyboard_theme_layout_overrides_hardware", "{broken")
            .commit()

        SettingsManager.migrateKeyboardThemeLayoutOverrides(context)

        assertEquals(
            fixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).background
        )
        assertFalse(prefs().contains("keyboard_theme_layout_overrides_hardware"))
    }

    @Test
    fun deletingSavedTheme_isCaseInsensitiveAndKeepsOtherThemes() {
        val context = RuntimeEnvironment.getApplication()
        val first = theme(0xFF123456.toInt())
        val second = theme(0xFFABCDEF.toInt())
        SettingsManager.saveKeyboardTheme(context, "First", first)
        SettingsManager.saveKeyboardTheme(context, "Second", second)

        SettingsManager.deleteKeyboardTheme(context, " first ")

        val savedThemes = SettingsManager.getSavedKeyboardThemes(context)
        assertEquals(listOf("Second"), savedThemes.map { it.name })
        assertEquals(second, savedThemes.single().theme)
    }

    @Test
    fun savingAndEditingDraft_doesNotMutateActiveThemes() {
        val context = RuntimeEnvironment.getApplication()
        val activeHardware = theme(0xFF281B35.toInt())
        val activeSoftware = theme(0xFF102030.toInt())
        val initialDraft = SettingsManager.KeyboardThemeDraft(
            name = "Draft",
            theme = theme(0xFFF0F0F0.toInt())
        )

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, activeHardware)
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, activeSoftware)
        SettingsManager.saveKeyboardThemeDraft(context, initialDraft)
        SettingsManager.saveKeyboardThemeDraft(
            context,
            initialDraft.copy(
                theme = theme(0xFFABCDEF.toInt()),
                populatedFields = setOf("background")
            )
        )

        assertEquals(
            activeHardware,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        )
        assertEquals(
            activeSoftware,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE)
        )
        assertFalse(SettingsManager.isKeyboardThemePreferenceKey("keyboard_theme_drafts"))
    }

    @Test
    fun keyboardThemeAssignmentPreferences_areRecognizedForRestore() {
        listOf(
            "keyboard_theme_assignment_mode_hardware",
            "keyboard_theme_assignment_mode_software",
            "keyboard_theme_light_hardware",
            "keyboard_theme_light_software",
            "keyboard_theme_dark_hardware",
            "keyboard_theme_dark_software",
            "modifier_indicator_mode"
        ).forEach { key ->
            assertEquals(
                PreferenceValueType.STRING,
                PreferenceSchemas.expectedType("pastiera_prefs", key)
            )
        }
    }

    private fun prefs() = SettingsManager.getPreferences(RuntimeEnvironment.getApplication())

    private fun legacyOverride(locale: String?, layout: String?, theme: SettingsManager.KeyboardThemeSettings) =
        JSONObject().apply {
            locale?.let { put("locale", it) }
            layout?.let { put("layout", it) }
            put("theme", JSONObject(SettingsManager.keyboardThemeToJsonString(theme)))
        }

    private fun putLegacyOverrides(target: String, vararg overrides: JSONObject) {
        prefs().edit()
            .putString("keyboard_theme_layout_overrides_$target", JSONArray(overrides.toList()).toString())
            .commit()
    }

    private fun theme(background: Int): SettingsManager.KeyboardThemeSettings =
        SettingsManager.defaultKeyboardTheme().copy(
            background = background,
            accent = background xor 0x00FFFFFF
        )
}
