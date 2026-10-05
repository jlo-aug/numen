package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SuggestionControllerReplacementReadinessTest {

    private lateinit var context: Context
    private val controllers = mutableListOf<SuggestionController>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit().clear().commit()
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en"))
        AutoCorrector.loadCorrections(context.assets, context)
    }

    @After
    fun tearDown() {
        controllers.forEach(SuggestionController::destroy)
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun builtInReplacementDoesNotWaitForDictionaryWhenSuggestionsAreDisabled() {
        val repository = ControlledDictionaryRepository(ready = false, loadFails = false)
        val controller = newController(
            repository,
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "im")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("I'm ", input.text)
    }

    @Test
    fun customReplacementDoesNotWaitForDictionaryWhenSuggestionsAreDisabled() {
        SettingsManager.saveCustomAutoCorrections(context, "en", mapOf("brb" to "be right back"))
        AutoCorrector.loadCorrections(context.assets, context)
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "brb")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("be right back ", input.text)
    }

    @Test
    fun replacementSurvivesDictionaryLoadFailure() {
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = true),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "ill")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("I'll ", input.text)
    }

    @Test
    fun warmDictionaryUsesTheSameExactReplacementPath() {
        val controller = newController(
            ControlledDictionaryRepository(ready = true, loadFails = false),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "didnt")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("didn't ", input.text)
    }

    @Test
    fun suggestionsEnabledOnlyAddsTrackerUpdatesAndDoesNotChangeReplacementSemantics() {
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = true,
            suggestionsEnabled = true
        )
        val input = FakeInputConnection(context, "ill")
        "ill".forEach { controller.onCharacterCommitted(it.toString(), input) }

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("I'll ", input.text)
    }

    @Test
    fun multipleBoundariesRemainIndependentWhileDictionaryIsLoading() {
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "im")

        assertTrue(controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
        input.appendFromEditor("ill")
        assertTrue(controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)

        assertEquals("I'm I'll ", input.text)
    }

    @Test
    fun readinessTransitionDoesNotChangeExactReplacementSemantics() {
        val repository = ControlledDictionaryRepository(ready = false, loadFails = false)
        val controller = newController(
            repository,
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "im")

        assertTrue(controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
        repository.isReady = true
        input.appendFromEditor("didnt")
        assertTrue(controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)

        assertEquals("I'm didn't ", input.text)
    }

    @Test
    fun replacementWorksImmediatelyAfterControllerRebind() {
        newController(
            ControlledDictionaryRepository(ready = true, loadFails = false),
            experimentalSuggestionsEnabled = true,
            suggestionsEnabled = true
        ).destroy()
        val reboundController = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "ill")

        val result = reboundController.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("I'll ", input.text)
    }

    @Test
    fun enterBoundaryDoesNotWaitForDictionaryReadiness() {
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "didnt")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_ENTER, null, input)

        assertTrue(result.replaced)
        assertEquals("didn't\n", input.text)
    }

    @Test
    fun disabledLanguageDoesNotCreateAnEnglishReplacement() {
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("de"))
        AutoCorrector.loadCorrections(context.assets, context)
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = false
        )
        val input = FakeInputConnection(context, "im")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertFalse(result.replaced)
        assertEquals("im ", input.text)
    }

    @Test
    fun readinessIndependentReplacementStillSupportsUndoWhenSuggestionsAreEnabled() {
        val controller = newController(
            ControlledDictionaryRepository(ready = false, loadFails = false),
            experimentalSuggestionsEnabled = true,
            suggestionsEnabled = true,
            autoReplaceOnSpaceEnter = true
        )
        val input = FakeInputConnection(context, "im")

        assertTrue(controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
        assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))

        assertEquals("im", input.text)
    }

    @Test
    fun replacementWhileRepositoryIsUnreadyIsIndependentOfBothSuggestionToggles() {
        val combinations = listOf(
            false to false,
            false to true,
            true to false,
            true to true
        )

        combinations.forEach { (experimentalEnabled, normalEnabled) ->
            val repository = ControlledDictionaryRepository(ready = false, loadFails = false)
            val controller = newController(
                repository = repository,
                experimentalSuggestionsEnabled = experimentalEnabled,
                suggestionsEnabled = normalEnabled
            )
            val input = FakeInputConnection(context, "didnt")

            val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

            val combination =
                "experimental_suggestions_enabled=$experimentalEnabled, suggestions_enabled=$normalEnabled"
            assertTrue(combination, result.replaced)
            assertEquals(combination, "didn't ", input.text)
            assertFalse(combination, repository.isReady)
        }
    }

    @Test
    fun replacementIsImmediateWhileBlockedDictionaryLoadRemainsCancellable() {
        val repository = BlockingDictionaryRepository()
        val controller = newController(
            repository = repository,
            experimentalSuggestionsEnabled = false,
            suggestionsEnabled = true
        )
        val input = FakeInputConnection(context, "im")

        val result = controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)

        assertTrue(result.replaced)
        assertEquals("I'm ", input.text)
        assertTrue(repository.awaitLoadStarted())

        controller.destroy()

        assertTrue(repository.awaitLoadCancellation())
        assertFalse(repository.isReady)
    }

    private fun newController(
        repository: DictionaryRepository,
        experimentalSuggestionsEnabled: Boolean,
        suggestionsEnabled: Boolean,
        autoReplaceOnSpaceEnter: Boolean = false
    ): SuggestionController {
        return SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = {
                SuggestionSettings(
                    textReplacementsEnabled = true,
                    suggestionsEnabled = suggestionsEnabled,
                    autoReplaceOnSpaceEnter = autoReplaceOnSpaceEnter
                )
            },
            isEnabled = { experimentalSuggestionsEnabled },
            onSuggestionsUpdated = {},
            currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> repository }
        ).also { controllers += it }
    }

    private class ControlledDictionaryRepository(
        ready: Boolean,
        private val loadFails: Boolean
    ) : DictionaryRepository by FakeDictionaryRepository() {
        @Volatile
        override var isReady: Boolean = ready

        @Volatile
        override var isLoadStarted: Boolean = false

        override suspend fun loadIfNeeded() {
            isLoadStarted = true
            if (loadFails) throw IllegalStateException("controlled load failure")
        }
    }

    private class BlockingDictionaryRepository :
        DictionaryRepository by FakeDictionaryRepository() {
        private val loadStarted = CountDownLatch(1)
        private val loadCancelled = CountDownLatch(1)

        @Volatile
        override var isReady: Boolean = false

        @Volatile
        override var isLoadStarted: Boolean = false

        override suspend fun loadIfNeeded() {
            isLoadStarted = true
            loadStarted.countDown()
            try {
                awaitCancellation()
            } finally {
                loadCancelled.countDown()
            }
        }

        fun awaitLoadStarted(): Boolean = loadStarted.await(2, TimeUnit.SECONDS)

        fun awaitLoadCancellation(): Boolean = loadCancelled.await(2, TimeUnit.SECONDS)
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        fun appendFromEditor(text: String) {
            buffer.append(text)
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val start = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(start, buffer.length)
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
