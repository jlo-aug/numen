package it.palsoftware.pastiera

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerDeviceSymModelTest {

    private val context: android.app.Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun freshConfiguration_enablesDeviceSymAsFirstKeyLayer() {
        val config = SettingsManager.getSymPagesConfig(context)

        assertTrue(config.deviceEnabled)
        assertEquals(SymPagesConfig.PAGE_DEVICE, config.firstEnabledKeyLayer())
        assertEquals(
            listOf(SymPagesConfig.PAGE_DEVICE, SymPagesConfig.PAGE_EMOJI, SymPagesConfig.PAGE_SYMBOLS),
            config.enabledOrderedPages()
        )
    }

    @Test
    fun exactLegacyDefault_isMigratedOnce() {
        val legacyJson = """{
            "deviceEnabled":false,
            "emojiEnabled":true,
            "symbolsEnabled":true,
            "clipboardEnabled":false,
            "emojiPickerEnabled":false,
            "symPageOrder":["device","emoji","symbols","clipboard","emoji_picker"]
        }""".trimIndent()
        SettingsManager.getPreferences(context).edit()
            .putString("sym_pages_config", legacyJson)
            .commit()

        val migrated = SettingsManager.getSymPagesConfig(context)
        val persisted = JSONObject(
            SettingsManager.getPreferences(context).getString("sym_pages_config", null)!!
        )

        assertTrue(migrated.deviceEnabled)
        assertEquals(2, persisted.getInt("schemaVersion"))
        assertTrue(persisted.getBoolean("deviceEnabled"))
        assertEquals(migrated, SettingsManager.getSymPagesConfig(context))
    }

    @Test
    fun customizedLegacyConfiguration_preservesDisabledDeviceSym() {
        val legacyJson = """{
            "deviceEnabled":false,
            "emojiEnabled":true,
            "symbolsEnabled":false,
            "clipboardEnabled":true,
            "emojiPickerEnabled":false,
            "symPageOrder":["clipboard","emoji","device","symbols","emoji_picker"]
        }""".trimIndent()
        SettingsManager.getPreferences(context).edit()
            .putString("sym_pages_config", legacyJson)
            .commit()

        val migrated = SettingsManager.getSymPagesConfig(context)

        assertFalse(migrated.deviceEnabled)
        assertFalse(migrated.symbolsEnabled)
        assertTrue(migrated.clipboardEnabled)
        assertEquals(SymPagesConfig.PAGE_EMOJI, migrated.firstEnabledKeyLayer())
    }

    @Test
    fun legacyAltBinding_isMigratedToTypedModifierBinding() {
        val prefs = SettingsManager.getPreferences(context)
        prefs.edit().putString("alt_character_layer_binding", "device:titan2").commit()

        val binding = SettingsManager.getAltModifierBinding(context)

        assertEquals(AltModifierBinding.DeviceSymProfile("titan2"), binding)
        assertEquals("device_sym:titan2", prefs.getString(SettingsManager.KEY_ALT_MODIFIER_BINDING, null))
        assertFalse(prefs.contains("alt_character_layer_binding"))
    }
}
