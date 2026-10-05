package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Accuracy benchmark for aggressive autocorrect against the real English dictionary. Prints a
 * report and guards against regressions; known words must never change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TypoBenchmarkTest {

    private val typos = linkedMapOf(
        "missed key" to listOf(
            "wrld" to "world", "thnks" to "thanks", "becase" to "because", "peple" to "people",
            "somthing" to "something", "probaly" to "probably", "diferent" to "different",
            "realy" to "really", "finaly" to "finally", "intresting" to "interesting",
            "enviroment" to "environment", "recomend" to "recommend", "mondy" to "monday",
            "frday" to "friday", "yesterdy" to "yesterday", "mesage" to "message",
            "adress" to "address", "goverment" to "government", "beautful" to "beautiful",
            "evrything" to "everything", "anythng" to "anything", "togther" to "together",
            "remmber" to "remember", "tomrrow" to "tomorrow", "importnt" to "important",
            "mothe" to "mother", "ubject" to "subject", "th" to "the", "nd" to "and"
        ),
        "missed double letter" to listOf(
            "helo" to "hello", "sory" to "sorry", "beter" to "better", "leter" to "letter",
            "coffe" to "coffee", "ocur" to "occur", "sucess" to "success", "acount" to "account",
            "aparently" to "apparently", "tomorow" to "tomorrow", "imediately" to "immediately",
            "necesary" to "necessary", "comunity" to "community", "diference" to "difference",
            "sugest" to "suggest", "realy" to "really", "dinner" to "dinner", "runing" to "running",
            "geting" to "getting", "stoped" to "stopped"
        ),
        "swapped letters" to listOf(
            "teh" to "the", "adn" to "and", "hte" to "the", "thier" to "their",
            "recieve" to "receive", "wierd" to "weird", "freind" to "friend",
            "becuase" to "because", "jsut" to "just", "waht" to "what", "taht" to "that",
            "whcih" to "which", "wiht" to "with", "siad" to "said", "agian" to "again",
            "thnig" to "thing", "liek" to "like", "knwo" to "know", "beleive" to "believe",
            "peopel" to "people"
        ),
        "neighbor key" to listOf(
            "thw" to "the", "wirk" to "work", "si" to "is", "ot" to "to", "hapoy" to "happy", "goid" to "good",
            "helli" to "hello", "lpve" to "love", "nicw" to "nice", "grwat" to "great",
            "meetinf" to "meeting", "workinh" to "working", "thinj" to "think", "juat" to "just",
            "wirld" to "world", "amd" to "and", "tge" to "the", "thr" to "the",
            "yhe" to "the", "rhe" to "the", "qnd" to "and", "sometjing" to "something"
        ),
        "extra letter" to listOf(
            "thhe" to "the", "helpp" to "help", "tthe" to "the", "untill" to "until",
            "andd" to "and", "frriend" to "friend", "whatt" to "what", "becausee" to "because",
            "juust" to "just", "thiss" to "this", "worldd" to "world", "aand" to "and",
            "pleasse" to "please", "thannks" to "thanks", "goodd" to "good",
            "willk" to "will", "ticketsx" to "tickets"
        ),
        "shift held too long" to listOf(
            "THe" to "The", "HEllo" to "Hello", "WHat" to "What", "THis" to "This",
            "OKay" to "Okay", "THanks" to "Thanks", "WEll" to "Well", "SOrry" to "Sorry"
        ),
        "badly mangled" to listOf(
            "instalaltion" to "installation", "accomodatoin" to "accommodation", "intresitng" to "interesting",
            "defintiely" to "definitely", "convesration" to "conversation", "appointmnet" to "appointment",
            "imporatnt" to "important", "diffrenet" to "different"
        ),
        "two mistakes" to listOf(
            "tommorow" to "tomorrow", "definately" to "definitely", "seperate" to "separate",
            "occured" to "occurred", "accomodate" to "accommodate", "embarass" to "embarrass",
            "existance" to "existence", "independant" to "independent", "neccessary" to "necessary",
            "recieved" to "received"
        )
    )

    /** Real words and acronyms: must never be changed. */
    private val knownWords = listOf(
        "form", "from", "there", "their", "than", "then", "were", "where", "quite", "quiet",
        "lose", "loose", "affect", "effect", "bat", "hat", "two", "advice", "advise", "its",
        "IDs", "TVs", "USA", "NASA", "VMware", "OK", "WiFi",
        // Real words beyond the suggestion dictionary, contractions and possessives.
        "bothers", "nosy", "congrats", "drooped", "curt", "who're", "waiter's", "Where's"
    )

    /** Names and slang the dictionary may not know: ideally left alone. */
    private val unknownButValid = listOf(
        "Aurelian", "OpenBubbles", "Ximena", "Unihertz", "Plektra", "SwiftKey", "gonna", "wanna",
        "tbh", "imo", "btw", "yall", "lol", "omw", "Niagara", "Sami", "Mennad", "bf", "xo", "ur"
    )

    private fun correct(controller: AutoReplaceController, word: String): String {
        val context = RuntimeEnvironment.getApplication()
        val inputConnection = FakeInputConnection(context, word)
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord(word) },
            inputConnection = inputConnection
        )
        return inputConnection.text.trimEnd()
    }

    @Test
    fun englishTypoBenchmark() {
        val context = RuntimeEnvironment.getApplication()
        val repository = AndroidDictionaryRepository(
            context = context,
            assets = context.assets,
            userDictionaryStore = UserDictionaryStore(),
            baseLocale = Locale.ENGLISH
        )
        // The repository refuses to load on the main thread (the test thread).
        runBlocking(Dispatchers.IO) { repository.loadIfNeeded() }
        assertTrue("English dictionary must load for the benchmark", repository.isReady)
        // Slang extras must never override base entries' casing or frequency.
        assertTrue(repository.topByNormalized("ok", limit = 4).any { it.word == "OK" })
        assertTrue(repository.topByNormalized("wifi", limit = 4).any { it.word == "WiFi" })

        // A fresh controller per word so undo/rejection state never leaks between cases.
        fun controller() = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 3,
                    aggressiveAutoReplace = true
                )
            }
        )

        val report = StringBuilder("\nTYPO BENCHMARK (aggressive)\n")
        var total = 0
        var fixed = 0
        typos.forEach { (group, cases) ->
            var groupFixed = 0
            val misses = mutableListOf<String>()
            cases.forEach { (typo, expected) ->
                val result = correct(controller(), typo)
                if (if (group == "shift held too long") result == expected else result.equals(expected, ignoreCase = true)) groupFixed++ else misses += "$typo→$result (want $expected)"
            }
            total += cases.size
            fixed += groupFixed
            report.append("  %-22s %2d/%2d  %s\n".format(group, groupFixed, cases.size, misses.joinToString(", ")))
        }
        val changedKnown = knownWords.mapNotNull { word ->
            correct(controller(), word).takeIf { it != word }?.let { "$word→$it" }
        }
        val changedUnknown = unknownButValid.mapNotNull { word ->
            correct(controller(), word).takeIf { it != word }?.let { "$word→$it" }
        }
        report.append("  TOTAL typos fixed       $fixed/$total\n")
        report.append("  known words changed     ${changedKnown.size}/${knownWords.size} $changedKnown\n")
        report.append("  names/slang changed     ${changedUnknown.size}/${unknownButValid.size} $changedUnknown\n")
        println(report)

        assertEquals("Known words must never be changed: $changedKnown", 0, changedKnown.size)
        assertEquals("Names and slang must be left alone: $changedUnknown", 0, changedUnknown.size)
        assertTrue("Benchmark regressed: $fixed/$total\n$report", fixed >= MIN_FIXED)
    }

    @Test
    fun englishSentenceAwareCorrection() {
        val context = RuntimeEnvironment.getApplication()
        val repository = AndroidDictionaryRepository(
            context = context,
            assets = context.assets,
            userDictionaryStore = UserDictionaryStore(),
            baseLocale = Locale.ENGLISH
        )
        runBlocking(Dispatchers.IO) { repository.loadIfNeeded() }
        val engine = SuggestionEngine(repository, locale = Locale.ENGLISH)
        val pairs = context.assets.open("common/dictionaries/en_pairs.txt")
            .bufferedReader().useLines { PairCounts.parse(it) }
        val corrector = ContextCorrector(
            frequencyOf = repository::getExactWordFrequency,
            isNeighborKey = engine::isNeighborKey,
            pairCount = pairs::count,
            isProtected = repository::isUserOrSlangWord
        )
        val start = PairCounts.SENTENCE_START
        val end = PairCounts.SENTENCE_END

        // (before, word, after) -> expected fix: one-key slips that only the neighbours reveal.
        val fixes = listOf(
            Triple("get", "dome", "food") to "some", Triple("bring", "dome", "water") to "some",
            Triple("have", "dome", "coffee") to "some", Triple("need", "dome", "help") to "some",
            Triple("letter", "form", "the") to "from", Triple("away", "form", "home") to "from",
            Triple("came", "form", "the") to "from", Triple("email", "form", "my") to "from",
            Triple("i", "lime", "it") to "like", Triple("i", "lime", "this") to "like",
            Triple("very", "food", "idea") to "good", Triple("so", "food", "morning") to "good",
            Triple("a", "food", "job") to "good", Triple("at", "hone", "now") to "home",
            Triple("go", "hone", "and") to "home", Triple("the", "sane", "time") to "same",
            Triple("a", "food", "time") to "good", Triple("from", "hone", "and") to "home",
            Triple(start, "Dome", "food") to "Some", Triple("get", "dome", end) to "some",
            Triple("go", "hone", end) to "home", Triple("be", "herr", end) to "here",
            Triple("at", "wor", end) to "work", Triple("some", "foo", end) to "food",
            Triple("wagged", "it's", "tail") to "its", Triple(start, "Its", "raining") to "It's",
            Triple("lost", "it's", "way") to "its"
        )
        // Correct sentences full of near-miss words: must never be touched.
        val untouched = listOf(
            Triple("the", "dome", "is"), Triple("to", "tie", "the"), Triple("i", "bet", "some"),
            Triple("the", "form", "today"), Triple("take", "off", "the"), Triple("a", "bit", "of"),
            Triple("good", "food", "court"), Triple("i", "live", "here"), Triple("and", "then", "the"),
            Triple("the", "war", "is"), Triple("fast", "food", "is"), Triple("to", "come", "home"),
            Triple("i", "like", "it"), Triple("get", "some", "food"), Triple("from", "the", "store"),
            Triple("at", "home", "now"), Triple("the", "same", "time"), Triple("what", "time", "is"),
            Triple("a", "good", "job"), Triple("fill", "the", "form"), Triple("a", "dome", "shaped"),
            Triple("key", "lime", "pie"), Triple("the", "lime", "is"), Triple("lime", "and", "salt"),
            Triple("hone", "your", "skills"), Triple("the", "hood", "of"), Triple("his", "fork", "and"),
            Triple("the", "word", "is"), Triple("a", "fine", "time"), Triple("the", "sane", "choice"),
            Triple("tone", "of", "voice"), Triple("the", "wood", "is"), Triple("lose", "the", "game"),
            Triple("a", "tame", "cat"), Triple("we", "dine", "at"), Triple("the", "mood", "is"),
            Triple("their", "home", "is"), Triple("my", "food", "is"), Triple("good", "for", "you"),
            Triple(start, "Some", "food"),
            Triple(start, "They", "end"), Triple(start, "They", "first"), Triple("i", "gave", "some"),
            Triple("we", "gave", "the"), Triple("how", "they", "end"), Triple("for", "them", "last"),
            Triple(start, "Call", "the"), Triple(start, "Won", "the"), Triple(start, "Win", "the"),
            Triple(start, "Hey", "want"), Triple(start, "Yet", "some"), Triple(start, "Hand", "the"),
            Triple(start, "Then", "next"), Triple("to", "tie", end), Triple("the", "dome", end),
            Triple("we", "won", end), Triple("i", "gave", end), Triple("they", "came", end),
            // Technical and literary text where a rarer word is meant.
            Triple("current", "directory", "to"), Triple(start, "Directory", "on"), Triple("screen", "width", "to"),
            Triple("option", "off", "causes"), Triple("and", "hooks", "are"), Triple("the", "locale", "to"),
            Triple(start, "Ids", "when"), Triple("the", "debug", "and"), Triple("for", "parity", "with"),
            Triple("be", "handy", "if"), Triple("had", "dined", "that"), Triple("yet", "borne", "a"),
            Triple("village", "inn", "over"), Triple("business", "nor", "anything"),
            // Its / it's used correctly.
            Triple("of", "its", "own"), Triple(start, "It's", "a"), Triple("think", "it's", "time"),
            Triple("and", "its", "products"),
            // Shipped slang is meant as typed.
            Triple("good", "tho", end), Triple("was", "meh", end), Triple("nice", "tho", "lol")
        )
        val wrong = fixes.mapNotNull { (case, expected) ->
            val got = corrector.review(case.first, case.second, case.third)
            if (got == expected) null else "${case.first} ${case.second} ${case.third} -> $got (want $expected)"
        }
        val changed = untouched.mapNotNull { case ->
            corrector.review(case.first, case.second, case.third)
                ?.let { "${case.first} ${case.second} ${case.third} -> $it" }
        }
        println("\nSENTENCE-AWARE: fixed ${fixes.size - wrong.size}/${fixes.size} $wrong; correct sentences changed ${changed.size}/${untouched.size} $changed")
        assertEquals("Correct sentences must not change: $changed", 0, changed.size)
        assertTrue("Sentence-aware fixes regressed: $wrong", fixes.size - wrong.size >= MIN_SENTENCE_FIXES)
    }

    private class FakeInputConnection(context: Context, initialText: String) :
        BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)
        val text: String get() = buffer.toString()
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)
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

    private companion object {
        /** Raise as accuracy improves; never lower without a reason. */
        const val MIN_FIXED = 132
        // Precision is measured on held-out text (tools/eval_context_correction.py), not here.
        const val MIN_SENTENCE_FIXES = 14
    }
}
