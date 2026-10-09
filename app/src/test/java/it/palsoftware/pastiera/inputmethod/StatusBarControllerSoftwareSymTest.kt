package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardLayoutTemplates
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarControllerSoftwareSymTest {

    @Test
    fun softwareSymLayerKeepsHardwareKeyPositions() {
        val mappings = mapOf(
            KeyEvent.KEYCODE_T to "[",
            KeyEvent.KEYCODE_Y to "]",
            KeyEvent.KEYCODE_Z to "»",
            KeyEvent.KEYCODE_X to "«"
        )

        val content = SoftwareKeyboardSymLabels.buildContentByChar(
            rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(AospKeyboardView.SoftwareLayoutStyle.COMPACT),
            symMappings = mappings
        )

        assertEquals("[", content['t'])
        assertEquals("]", content['y'])
        assertEquals("»", content['z'])
        assertEquals("«", content['x'])
        assertFalse(content.containsKey('q'))
    }

    @Test
    fun softwareSymPunctuationKeysFallBackToTheirOwnSymbol() {
        val content = SoftwareKeyboardSymLabels.buildContentByChar(
            rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(AospKeyboardView.SoftwareLayoutStyle.FULL_ISO),
            symMappings = mapOf(KeyEvent.KEYCODE_COMMA to "…")
        )

        assertEquals("…", content[','])
        assertEquals("[", content['['])
        assertEquals("#", content['#'])
        assertEquals("\\", content['\\'])
    }
}
