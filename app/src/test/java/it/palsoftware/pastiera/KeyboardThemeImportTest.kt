package it.palsoftware.pastiera

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardThemeImportTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun exportedTheme_roundTripsThroughStrictImport() {
        val theme = distinctTheme(0xFF102030.toInt())

        val imported = SettingsManager.keyboardThemeFromJsonString(
            SettingsManager.keyboardThemeToJsonString(theme)
        )

        assertEquals(theme, imported)
    }

    @Test
    fun missingLegacyBaselineField_isRejected() {
        assertNull(SettingsManager.keyboardThemeFromJsonString("{}"))

        val incomplete = JSONObject(
            SettingsManager.keyboardThemeToJsonString(SettingsManager.defaultKeyboardTheme())
        ).apply {
            remove("background")
        }

        assertNull(SettingsManager.keyboardThemeFromJsonString(incomplete.toString()))
    }

    @Test
    fun initialExportReleaseTheme_importsWithDefaultsForNewerFields() {
        val legacyExport = """
            {
              "background": -15790321,
              "divider": -14671840,
              "normal_key": -15658735,
              "special_key": -14540254,
              "text_and_icons": -13421773,
              "led_inactive": -12303292,
              "led_active": -11184811,
              "led_locked": -10066330,
              "accent": -8943463,
              "cursor_swipe": -7829368,
              "key_popup": -6710887,
              "key_popup_selected": -5592406,
              "suggestion": -4473925,
              "status_bar_button": -3355444,
              "key_corner_radius_ratio": 0.12,
              "chrome_corner_radius_ratio": 0.23,
              "key_height_scale": 1.11,
              "key_width_scale": 0.97,
              "row_gap_scale": 0.08,
              "distribute_horizontal_spacing": false,
              "ortholinear": true,
              "show_leds": false
            }
        """.trimIndent()
        val defaults = SettingsManager.defaultKeyboardTheme()

        val imported = SettingsManager.keyboardThemeFromJsonString(legacyExport)

        assertEquals(
            defaults.copy(
                background = -15790321,
                divider = -14671840,
                normalKey = -15658735,
                specialKey = -14540254,
                textAndIcons = -13421773,
                ledInactive = -12303292,
                ledActive = -11184811,
                ledLocked = -10066330,
                accent = -8943463,
                cursorSwipe = -7829368,
                keyPopup = -6710887,
                keyPopupSelected = -5592406,
                suggestion = -4473925,
                statusBarButton = -3355444,
                keyCornerRadiusRatio = 0.12f,
                chromeCornerRadiusRatio = 0.23f,
                keyHeightScale = 1.11f,
                keyWidthScale = 0.97f,
                rowGapScale = 0.08f,
                distributeHorizontalSpacing = false,
                ortholinear = true,
                showLeds = false
            ),
            imported
        )
    }

    @Test
    fun wrongTypesAndUnsupportedPopupStyle_areRejected() {
        val exported = SettingsManager.keyboardThemeToJsonString(SettingsManager.defaultKeyboardTheme())
        val stringColor = JSONObject(exported).apply { put("background", "-1") }
        val numericBoolean = JSONObject(exported).apply { put("show_leds", 1) }
        val stringOptionalFloat = JSONObject(exported).apply { put("suggestions_height_scale", "1.2") }
        val numericOptionalBoolean = JSONObject(exported).apply { put("key_popup_attached", 1) }
        val unsupportedStyle = JSONObject(exported).apply { put("key_popup_style", "future") }

        assertNull(SettingsManager.keyboardThemeFromJsonString(stringColor.toString()))
        assertNull(SettingsManager.keyboardThemeFromJsonString(numericBoolean.toString()))
        assertNull(SettingsManager.keyboardThemeFromJsonString(stringOptionalFloat.toString()))
        assertNull(SettingsManager.keyboardThemeFromJsonString(numericOptionalBoolean.toString()))
        assertNull(SettingsManager.keyboardThemeFromJsonString(unsupportedStyle.toString()))
    }

    @Test
    fun normalHardwareImport_savesExactlyOneThemeAndActivatesOnlyHardware() {
        val oldHardware = distinctTheme(0xFF010101.toInt())
        val oldSoftware = distinctTheme(0xFF020202.toInt())
        val imported = distinctTheme(0xFFABCDEF.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, oldHardware)
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, oldSoftware)

        assertTrue(
            saveImportedKeyboardTheme(
                context,
                SettingsManager.KeyboardThemeTarget.HARDWARE,
                " Imported hardware ",
                imported
            )
        )

        assertEquals(
            listOf(SettingsManager.NamedKeyboardTheme("Imported hardware", imported)),
            SettingsManager.getSavedKeyboardThemes(context)
        )
        assertEquals(
            imported,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        )
        assertEquals(
            oldSoftware,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE)
        )
    }

    @Test
    fun normalSoftwareImport_activatesOnlySoftware() {
        val oldHardware = distinctTheme(0xFF111111.toInt())
        val oldSoftware = distinctTheme(0xFF222222.toInt())
        val imported = distinctTheme(0xFF334455.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, oldHardware)
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, oldSoftware)

        assertTrue(
            saveImportedKeyboardTheme(
                context,
                SettingsManager.KeyboardThemeTarget.SOFTWARE,
                "Software import",
                imported
            )
        )

        assertEquals(
            oldHardware,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        )
        assertEquals(
            imported,
            SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE)
        )
    }

    @Test
    fun duplicateSavedOrDraftName_rejectsImportWithoutMutation() {
        val active = distinctTheme(0xFF121212.toInt())
        val imported = distinctTheme(0xFF343434.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, active)
        SettingsManager.saveKeyboardTheme(context, "Existing", distinctTheme(0xFF565656.toInt()))
        SettingsManager.saveKeyboardThemeDraft(
            context,
            SettingsManager.KeyboardThemeDraft("Draft", SettingsManager.defaultKeyboardTheme())
        )

        assertFalse(
            saveImportedKeyboardTheme(
                context,
                SettingsManager.KeyboardThemeTarget.HARDWARE,
                " existing ",
                imported
            )
        )
        assertFalse(
            saveImportedKeyboardTheme(
                context,
                SettingsManager.KeyboardThemeTarget.HARDWARE,
                "DRAFT",
                imported
            )
        )

        assertEquals(listOf("Existing"), SettingsManager.getSavedKeyboardThemes(context).map { it.name })
        assertEquals(active, SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE))
    }

    @Test
    fun draftImport_preservesNameCompletesDraftAndDoesNotActivateEitherTarget() {
        val hardware = distinctTheme(0xFF101010.toInt())
        val software = distinctTheme(0xFF202020.toInt())
        val imported = distinctTheme(0xFF303030.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, hardware)
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, software)
        val draft = SettingsManager.KeyboardThemeDraft(
            name = "In progress",
            theme = distinctTheme(0xFF404040.toInt()),
            populatedFields = setOf(DRAFT_BACKGROUND)
        )

        val replaced = importedKeyboardThemeDraft(draft, imported)
        SettingsManager.saveKeyboardThemeDraft(context, replaced)

        assertEquals("In progress", replaced.name)
        assertEquals(imported, replaced.theme)
        assertEquals(KEYBOARD_THEME_DRAFT_REQUIRED_FIELDS, replaced.populatedFields)
        assertEquals(replaced, SettingsManager.getKeyboardThemeDrafts(context).single())
        assertEquals(hardware, SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE))
        assertEquals(software, SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE))
        assertTrue(SettingsManager.getSavedKeyboardThemes(context).isEmpty())
    }

    private fun distinctTheme(background: Int): SettingsManager.KeyboardThemeSettings =
        SettingsManager.defaultKeyboardTheme().copy(
            background = background,
            divider = background xor 0x00010101,
            accent = background xor 0x000F0F0F,
            keyCornerRadiusRatio = 0.17f,
            keyHeightScale = 1.08f,
            distributeHorizontalSpacing = false,
            ortholinear = true,
            keyPopupStyle = SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC,
            keyPopupAttached = false
        )
}
