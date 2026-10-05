package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Sentence-aware correction end to end, through the real controller and the shipped English
 * pair counts, typing into a fake editor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SuggestionControllerContextTest {
    private lateinit var context: Context
    private val controllers = mutableListOf<SuggestionController>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        controllers.forEach(SuggestionController::destroy)
    }

    private fun controller(contextAware: Boolean = true): SuggestionController = controller { contextAware }

    private fun controller(textReplacements: () -> Boolean = { true }, contextAware: () -> Boolean): SuggestionController {
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            listOf(
                "let's" to 150, "get" to 175, "some" to 169, "dome" to 115, "food" to 144,
                "go" to 180, "home" to 160, "hone" to 100, "we" to 190, "can" to 185,
                "the" to 222, "is" to 210, "big" to 150, "be" to 200, "here" to 190, "herr" to 77
            ).forEach { (word, frequency) -> addTestEntry(word, frequency) }
        }
        val controller = SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = {
                SuggestionSettings(
                    suggestionsEnabled = true,
                    autoReplaceOnSpaceEnter = true,
                    textReplacementsEnabled = textReplacements(),
                    contextAwareCorrection = contextAware()
                )
            },
            onSuggestionsUpdated = {},
            currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> repository }
        ).also { controllers += it }
        if (!contextAware()) return controller
        val deadline = System.currentTimeMillis() + 20_000
        while (!controller.isContextCorrectorReadyForTests() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue("English pair counts must load", controller.isContextCorrectorReadyForTests())
        return controller
    }

    /** Types each word followed by a space, the way the keyboard reports boundaries. */
    private fun SuggestionController.type(input: FakeInputConnection, vararg words: String) {
        words.forEach { word ->
            input.appendFromEditor(word)
            onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)
        }
    }

    @Test
    fun fixesTheWordBeforeOnceTheNextOneIsTyped() {
        val controller = controller()
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome")
        assertEquals("get dome ", input.text)
        controller.type(input, "food")
        assertEquals("get some food ", input.text)
    }

    @Test
    fun backspaceRightAfterRestoresExactlyWhatWasTyped() {
        val controller = controller()
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome", "food")
        assertEquals("get some food ", input.text)
        assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
        assertEquals("get dome food ", input.text)
        // Undone means meant: the same words are left alone afterwards.
        controller.type(input, "get", "dome", "food")
        assertEquals("get dome food get dome food ", input.text)
    }

    @Test
    fun endOfSentenceReviewsTheLastWord() {
        val controller = controller()
        val input = FakeInputConnection(context, "")
        controller.type(input, "we", "can", "be")
        input.appendFromEditor("herr")
        controller.onBoundaryKey(KeyEvent.KEYCODE_PERIOD, null, input, boundaryCharOverride = '.')
        assertEquals("we can be here.", input.text)
        assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
        assertEquals("we can be herr.", input.text)
    }

    @Test
    fun correctSentencesAreUntouched() {
        val controller = controller()
        val input = FakeInputConnection(context, "")
        controller.type(input, "the", "dome", "is", "big")
        assertEquals("the dome is big ", input.text)
    }

    @Test
    fun oldTextTheCursorLandsOnIsNeverReviewed() {
        val controller = controller()
        // A saved draft: the words were not typed in this session.
        val input = FakeInputConnection(context, "get dome food")
        controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)
        assertEquals("get dome food ", input.text)
    }

    @Test
    fun enterIsNeverFollowedByAnEdit() {
        val controller = controller()
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome")
        input.appendFromEditor("food")
        controller.onBoundaryKey(KeyEvent.KEYCODE_ENTER, null, input)
        assertFalse(input.text.contains("some"))
    }

    @Test
    fun aSelectionIsNeverReplaced() {
        val controller = controller()
        val input = FakeInputConnection(context, "", selected = "up")
        controller.type(input, "get", "dome", "food")
        assertEquals("get dome food ", input.text)
    }

    @Test
    fun switchedOffMeansNothingChanges() {
        val controller = controller(contextAware = false)
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome", "food")
        assertEquals("get dome food ", input.text)
    }

    @Test
    fun anyOtherKeyEndsTheUndoWindow() {
        val controller = controller()
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome", "food")
        controller.clearLastReplacement() // What the key pipeline does for any non-Backspace key.
        assertFalse(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
        assertEquals("get some food ", input.text)
    }

    @Test
    fun wordsTheUserAddedAreMeantAsTyped() {
        val controller = controller()
        controller.addUserWord("dome")
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome", "food")
        assertEquals("get dome food ", input.text)
    }

    @Test
    fun pairCountsStayForEnglishReplacementsAndAreFreedWhenNothingNeedsThem() {
        // Numen only: Pastiera's build keeps its original text replacements.
        org.junit.Assume.assumeFalse(it.palsoftware.pastiera.BuildConfig.UPSTREAM_BRANDING)
        var sentenceAware = true
        var textReplacements = true
        val controller = controller(textReplacements = { textReplacements }) { sentenceAware }
        // Sentence-aware off: no sentence fixes, but English text replacements keep the pairs
        // for their contraction check ("ill" -> "I'll").
        sentenceAware = false
        val off = FakeInputConnection(context, "")
        controller.type(off, "get", "dome", "food")
        assertEquals("get dome food ", off.text)
        assertTrue(controller.isContextCorrectorReadyForTests(load = false))
        // Nothing needs them: freed.
        textReplacements = false
        controller.type(FakeInputConnection(context, ""), "hi")
        assertFalse(controller.isContextCorrectorReadyForTests(load = false))
        // Back on: loaded again and fixing.
        textReplacements = true
        sentenceAware = true
        val deadline = System.currentTimeMillis() + 20_000
        while (!controller.isContextCorrectorReadyForTests() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        val input = FakeInputConnection(context, "")
        controller.type(input, "get", "dome", "food")
        assertEquals("get some food ", input.text)
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String,
        private val selected: String? = null
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)
        val text: String get() = buffer.toString()
        fun appendFromEditor(text: String) {
            buffer.append(text)
        }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = selected
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            buffer.delete((buffer.length - beforeLength).coerceAtLeast(0), buffer.length)
            return true
        }
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }
        override fun beginBatchEdit(): Boolean = true
        override fun endBatchEdit(): Boolean = true
    }
}
