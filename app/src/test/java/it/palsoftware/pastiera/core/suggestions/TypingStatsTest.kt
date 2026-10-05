package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TypingStatsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(TypingStats.PREFS_NAME, Context.MODE_PRIVATE)
    private var clock = 1_000_000L

    private fun stats() = TypingStats(prefs) { clock }

    /** Types [words] four-letter words: five key presses each (letters and space), 200 ms apart. */
    private fun TypingStats.typeWords(words: Int) {
        repeat(words) {
            repeat(5) {
                clock += 200
                onKey()
            }
            onWord("type")
        }
    }

    @Test
    fun speedCountsActiveTypingOnlyInFiveCharacterWords() {
        val stats = stats()
        // 60 words x 5 characters, one key every 200 ms: 60 s of typing -> 60 wpm.
        stats.typeWords(60)
        // A long pause does not lower the speed.
        clock += 60_000
        stats.onKey()
        stats.save()
        assertEquals(60, TypingStats.summary(prefs).wordsPerMinute)
    }

    @Test
    fun speedWaitsForEnoughTyping() {
        val stats = stats()
        stats.typeWords(4)
        stats.save()
        assertNull(TypingStats.summary(prefs).wordsPerMinute)
    }

    @Test
    fun favoriteWordKeepsUpdatingOnceTheWordTableIsFull() {
        val stats = stats()
        // Fill the table with 400 different words, each used twice ("waab", "waac", ...).
        repeat(400) { i -> repeat(2) { stats.onWord("w" + ('a' + i / 26 / 26 % 26) + ('a' + i / 26 % 26) + ('a' + i % 26)) } }
        repeat(4) { stats.onWord("pizza") }
        stats.save()
        assertEquals("pizza", TypingStats.summary(prefs).favoriteWord)
    }

    @Test
    fun contractionsAreNeverTheFavorite() {
        val stats = stats()
        repeat(9) { stats.onWord("I've") }
        repeat(3) { stats.onWord("coffee") }
        stats.save()
        assertEquals("coffee", TypingStats.summary(prefs).favoriteWord)
    }

    @Test
    fun favoriteWordIgnoresFillerAndNeedsAFewUses() {
        val stats = stats()
        repeat(9) { stats.onWord("that") }
        repeat(2) { stats.onWord("coffee") }
        stats.save()
        assertNull(TypingStats.summary(prefs).favoriteWord)
        stats.onWord("Coffee")
        stats.save()
        val summary = TypingStats.summary(prefs)
        assertEquals("coffee", summary.favoriteWord)
        assertEquals(12, summary.words)
    }

    @Test
    fun undoneFixesDoNotCount() {
        val stats = stats()
        stats.onTypoFixed()
        stats.onTypoFixed()
        stats.onFixUndone()
        stats.save()
        assertEquals(1, TypingStats.summary(prefs).typosFixed)
    }

    @Test
    fun aResetFromTheHomeScreenWinsOverTheRunningKeyboard() {
        val stats = stats()
        repeat(5) { stats.onWord("hello") }
        stats.save()
        TypingStats.reset(prefs)
        stats.onWord("again")
        stats.save()
        assertEquals(1, TypingStats.summary(prefs).words)
    }

    @Test
    fun restrictedFieldsAreNeverCounted() {
        var allowed = false
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("hello", 150)
        }
        val controller = SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = { SuggestionSettings(suggestionsEnabled = true) },
            onSuggestionsUpdated = {},
            currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> repository },
            typingStats = stats(),
            isStatsAllowed = { allowed }
        )
        val input = object : BaseInputConnection(View(context), true) {
            val buffer = StringBuilder()
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                buffer.append(text ?: "")
                return true
            }
        }
        fun typeHello() {
            "hello".forEach { ch ->
                input.buffer.append(ch)
                controller.onCharacterCommitted(ch.toString(), input)
            }
            controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)
            controller.onContextReset()
        }
        typeHello() // A password field.
        assertEquals(0, TypingStats.summary(prefs).words)
        allowed = true
        typeHello()
        assertEquals(1, TypingStats.summary(prefs).words)
        controller.destroy()
    }
}
