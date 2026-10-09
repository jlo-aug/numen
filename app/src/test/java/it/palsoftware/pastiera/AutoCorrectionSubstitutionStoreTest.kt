package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.AutoCorrector
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
class AutoCorrectionSubstitutionStoreTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun addCustomSubstitution_savesTriggerAtFront() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, mapOf("brb" to "be right back"))

        val saved = AutoCorrectionSubstitutionStore.addCustomSubstitution(
            context = context,
            trigger = "  OMW ",
            replacement = "on my way"
        )

        assertTrue(saved)
        val corrections = SettingsManager.getCustomAutoCorrections(context)
        assertEquals(listOf("omw", "brb"), corrections.keys.toList())
        assertEquals("on my way", corrections["omw"])
    }

    @Test
    fun addCustomSubstitution_rejectsBlankValues() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(
            AutoCorrectionSubstitutionStore.addCustomSubstitution(
                context = context,
                    trigger = "",
                replacement = "replacement"
            )
        )
        assertFalse(
            AutoCorrectionSubstitutionStore.addCustomSubstitution(
                context = context,
                    trigger = "abbr",
                replacement = " "
            )
        )
    }

    @Test
    fun addCustomSubstitution_isAvailableImmediatelyToAutoCorrector() {
        val context = RuntimeEnvironment.getApplication()

        AutoCorrectionSubstitutionStore.addCustomSubstitution(
            context = context,
            trigger = "omw",
            replacement = "on my way"
        )

        val correction = AutoCorrector.processText("omw")
        assertEquals("on my way", correction?.second)
    }

    @Test
    fun addCustomSubstitution_replacesTssstWithTotallynewsubstitution() {
        val context = RuntimeEnvironment.getApplication()

        AutoCorrectionSubstitutionStore.addCustomSubstitution(
            context = context,
            trigger = "tssst",
            replacement = "totallynewsubstitution"
        )

        val correction = AutoCorrector.processText("tssst ")

        assertEquals("tssst", correction?.first)
        assertEquals("totallynewsubstitution", correction?.second)
    }
}
