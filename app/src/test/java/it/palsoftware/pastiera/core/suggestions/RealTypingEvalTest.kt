package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * Autocorrect on real people's typing, compared with what their phone's own autocorrect did.
 * Runs only when NUMEN_EVAL_FILE points at a test file (built from a research typing dataset, kept
 * outside the repository), one case per line:
 *
 *   set <TAB> words before <TAB> typed <TAB> intended <TAB> next word <TAB> phone's result
 *
 * The report goes to build/reports/real-typing-eval.txt. Cases where the typed word was already
 * right measure the opposite mistake: changing a correct word.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RealTypingEvalTest {

    private class Tally {
        var cases = 0
        var fixed = 0
        var wrong = 0
        var phoneFixed = 0
        var phoneWrong = 0
        var correctCases = 0
        var correctChanged = 0
        var phoneCorrectChanged = 0
        val wrongExamples = mutableListOf<String>()
        val missedExamples = mutableListOf<String>()
        val changedExamples = mutableListOf<String>()
    }

    @Test
    fun realTypingAgainstPhoneAutocorrect() {
        val path = System.getenv("NUMEN_EVAL_FILE")
        assumeTrue("Set NUMEN_EVAL_FILE to run", !path.isNullOrBlank() && File(path).exists())
        val limit = System.getenv("NUMEN_EVAL_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
        val context = RuntimeEnvironment.getApplication()
        val controller = englishController(context)
        val tallies = linkedMapOf<String, Tally>()
        // Optional per-case dump for analysis: set, typed, intended, result, outcome, rule.
        val dump = System.getenv("NUMEN_EVAL_DUMP")?.let { File(it).bufferedWriter() }

        File(path!!).useLines { lines ->
            lines.take(limit).forEach { line ->
                val cells = line.split('\t')
                if (cells.size < 6) return@forEach
                val (set, before, typed, intended, next, phone) = cells.toList()
                val tally = tallies.getOrPut(set) { Tally() }
                it.palsoftware.pastiera.inputmethod.DebugCaptureStore.clearAll()
                val got = typeCase(context, controller, before, typed, next)
                val rule = it.palsoftware.pastiera.inputmethod.DebugCaptureStore.autoCorrectionsSnapshot()
                    .lastOrNull { e -> e.before == typed }?.let { e -> e.reason ?: e.source ?: "" }.orEmpty()
                val label = "$before [$typed] $next"
                // The dataset's sentences often skip capitals and apostrophes ("i", "dont"): ignore both.
                fun same(a: String, b: String) = a.replace("'", "").equals(b.replace("'", ""), ignoreCase = true)
                dump?.write(listOf(set, typed, intended, got, if (same(typed, intended)) "correct" else if (same(got, intended)) "fixed" else if (same(got, typed)) "missed" else "wrong", rule).joinToString("\t") + "\n")
                if (same(typed, intended)) {
                    tally.correctCases++
                    if (!same(got, typed)) {
                        tally.correctChanged++
                        if (tally.changedExamples.size < 80) tally.changedExamples += "$label -> $got"
                    }
                    if (!same(phone, typed)) tally.phoneCorrectChanged++
                } else {
                    tally.cases++
                    when {
                        same(got, intended) -> tally.fixed++
                        same(got, typed) -> if (tally.missedExamples.size < 80) tally.missedExamples += "$label (want $intended)"
                        else -> {
                            tally.wrong++
                            if (tally.wrongExamples.size < 80) tally.wrongExamples += "$label -> $got (want $intended)"
                        }
                    }
                    if (same(phone, intended)) tally.phoneFixed++ else if (!same(phone, typed)) tally.phoneWrong++
                }
            }
        }
        controller.destroy()
        dump?.close()

        val report = StringBuilder("REAL TYPING (Numen vs the participant's phone)\n")
        tallies.forEach { (set, t) ->
            fun pct(n: Int, d: Int) = if (d == 0) "-" else "%.1f%%".format(n * 100.0 / d)
            report.append("== $set\n")
            report.append("  typos ${t.cases}: Numen fixed ${pct(t.fixed, t.cases)}, wrong ${pct(t.wrong, t.cases)}")
            report.append(" | phone fixed ${pct(t.phoneFixed, t.cases)}, wrong ${pct(t.phoneWrong, t.cases)}\n")
            report.append("  correct words ${t.correctCases}: Numen changed ${pct(t.correctChanged, t.correctCases)}")
            report.append(" | phone changed ${pct(t.phoneCorrectChanged, t.correctCases)}\n")
            report.append("  WRONG\n").append(t.wrongExamples.joinToString("\n") { "    $it" }).append('\n')
            report.append("  MISSED\n").append(t.missedExamples.joinToString("\n") { "    $it" }).append('\n')
            report.append("  CORRECT WORDS CHANGED\n").append(t.changedExamples.joinToString("\n") { "    $it" }).append('\n')
        }
        File("build/reports/real-typing-eval.txt").writeText(report.toString())
        println(report.lineSequence().filter { it.startsWith("==") || it.startsWith("  typos") || it.startsWith("  correct") }.joinToString("\n"))
    }

    /** Types the case and returns what became of the typed word (two words if it was split). */
    private fun typeCase(context: Context, controller: SuggestionController, before: String, typed: String, next: String): String {
        val text = listOf(before, typed, next).filter { it.isNotEmpty() }.joinToString(" ") + " "
        val input = Input(context)
        controller.onContextReset()
        for (ch in text) {
            when {
                ch == ' ' -> {
                    if (input.text.endsWith(" ")) continue
                    if (!controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).committed) input.append(" ")
                }
                ch in ".,!?;:" -> {
                    val result = controller.onBoundaryKey(KeyEvent.KEYCODE_UNKNOWN, null, input, boundaryCharOverride = ch)
                    if (!result.committed) input.append(ch.toString())
                }
                else -> {
                    input.append(ch.toString())
                    controller.onCharacterCommitted(ch.toString(), input)
                }
            }
        }
        controller.clearLastReplacement()
        val tokens = input.text.trim().split(' ')
        val start = if (before.isEmpty()) 0 else before.split(' ').size
        val end = tokens.size - (if (next.isEmpty()) 0 else 1)
        return if (start < end) tokens.subList(start, end).joinToString(" ") else ""
    }

    private fun englishController(context: Context): SuggestionController {
        val repository = AndroidDictionaryRepository(context, context.assets, UserDictionaryStore(), Locale.ENGLISH)
        runBlocking(Dispatchers.IO) { repository.loadIfNeeded() }
        it.palsoftware.pastiera.inputmethod.AutoCorrector.loadCorrections(context.assets, context)
        val controller = SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = {
                SuggestionSettings(
                    suggestionsEnabled = true,
                    autoReplaceOnSpaceEnter = true,
                    aggressiveAutoReplace = true,
                    contextAwareCorrection = true
                )
            },
            onSuggestionsUpdated = {},
            currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> repository }
        )
        val deadline = System.currentTimeMillis() + 30_000
        while (!controller.isContextCorrectorReadyForTests() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return controller
    }

    private class Input(context: Context) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder()
        val text: String get() = buffer.toString()
        fun append(value: String) {
            buffer.append(value)
        }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
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

    private operator fun <T> List<T>.component6(): T = this[5]
}
