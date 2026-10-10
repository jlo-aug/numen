package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AlternateCharacterManager
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the SYM key steps through on a Titan 2 Elite, and Numen's simple defaults for SYM, Alt and the bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SymPagesOnTitanTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2 Elite",
            device = "titan2",
            product = "titan2",
            board = "G72BoardV1"
        )
    }

    @After
    fun tearDown() {
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun symStepsThroughTheSavedPagesAndEachHasKeys() {
        val prefs = SettingsManager.getPreferences(context)
        val characters = AlternateCharacterManager(context.assets, prefs, context)
        val sym = SymLayoutController(context, prefs, characters)

        assertEquals("\"", characters.getDeviceSymMappings()[KeyEvent.KEYCODE_L])

        val enabled = SettingsManager.getSymPagesConfig(context).enabledOrderedPages()
        val shown = mutableListOf<Int>()
        repeat(enabled.size + 1) {
            val page = sym.toggleSymPage()
            shown += page
            if (page in setOf(1, 2, 5)) {
                assertTrue("page $page has no keys", sym.currentSymMappings().orEmpty().isNotEmpty())
            }
        }
        assertEquals(0, shown.last())
        assertEquals(enabled.size, shown.dropLast(1).distinct().size)
    }

    @Test
    fun byDefaultSymOpensSymbolsThenTheEmojiPickerAndAltTypesThePrintedSymbols() {
        assertEquals(listOf("symbols", "emoji_picker"), SettingsManager.getSymPagesConfig(context).enabledOrderedPages())
        val characters = AlternateCharacterManager(context.assets, SettingsManager.getPreferences(context), context)
        assertEquals("1", characters.getAltModifierMappings()[KeyEvent.KEYCODE_W])
    }

    @Test
    fun theDefaultBarIsEmojiAndMicrophoneWithoutVariations() {
        assertEquals(listOf(SettingsManager.STATUS_BAR_BUTTON_EMOJI), SettingsManager.getStatusBarSlotsLeft(context))
        assertEquals(listOf(SettingsManager.STATUS_BAR_BUTTON_MICROPHONE), SettingsManager.getStatusBarSlotsRight(context))
        assertEquals(false, SettingsManager.getStatusBarVariationsVisible(context))
        assertEquals(false, SettingsManager.getSuggestionsEnabled(context))
    }

    @Test
    fun aSavedConfigIsNeverChanged() {
        saveConfig(schemaVersion = 2, order = """["device","emoji","symbols","clipboard","emoji_picker"]""", device = true)
        assertEquals(listOf("device", "emoji", "symbols"), SettingsManager.getSymPagesConfig(context).enabledOrderedPages())
    }

    private fun saveConfig(schemaVersion: Int, order: String, device: Boolean) {
        SettingsManager.getPreferences(context).edit().putString(
            "sym_pages_config",
            """{"schemaVersion":$schemaVersion,"deviceEnabled":$device,"emojiEnabled":true,"symbolsEnabled":true,""" +
                """"clipboardEnabled":false,"emojiPickerEnabled":false,"symPageOrder":$order}"""
        ).commit()
    }
}
