package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.random.Random

/**
 * Types everyday messages key by key through the real suggestion pipeline (English dictionary,
 * word-pair counts, aggressive autocorrect and sentence-aware corrections on), with realistic
 * physical-keyboard slips injected, and scores every word: slips fixed, missed or turned into
 * another wrong word, and, most important, correct words that got changed.
 *
 * Texts: hand-written texting messages, and everyday Tatoeba sentences held out of the pair
 * counts. The full report goes to build/reports/chat-simulation.txt; use it to tune heuristics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatSimulationTest {

    private data class Word(val intended: String, val typed: String, val kind: String?)

    private class Score {
        val cleanWords = IntArray(1)
        val falseChanges = mutableListOf<String>()
        val total = linkedMapOf<String, Int>()
        val fixed = linkedMapOf<String, Int>()
        val wrongFixes = mutableListOf<String>()
        val missed = mutableListOf<String>()
        var misaligned = mutableListOf<String>()

        fun slip(kind: String, outcome: String?, line: String) {
            total[kind] = (total[kind] ?: 0) + 1
            when (outcome) {
                null -> fixed[kind] = (fixed[kind] ?: 0) + 1
                else -> (if (outcome.startsWith("missed")) missed else wrongFixes) += "[$kind] $line"
            }
        }
    }

    private fun englishController(context: Context): Pair<SuggestionController, DictionaryRepository> {
        val repository = AndroidDictionaryRepository(
            context = context,
            assets = context.assets,
            userDictionaryStore = UserDictionaryStore(),
            baseLocale = Locale.ENGLISH
        )
        runBlocking(Dispatchers.IO) { repository.loadIfNeeded() }
        AutoCorrector.loadCorrections(context.assets, context) // Text replacements, as the keyboard loads them.
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
        while (!controller.isContextCorrectorReadyForTests() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue("English pair counts must load", controller.isContextCorrectorReadyForTests())
        return controller to repository
    }

    @Test
    fun numbersHashtagsAddressesAndCodeStayAsTyped() {
        val context = RuntimeEnvironment.getApplication()
        val (controller, _) = englishController(context)
        val cases = listOf(
            "meet at 3pm ok" to "meet at 3pm ok",
            "see you at 6pm, bye" to "see you at 6pm, bye",
            "only 5km left" to "only 5km left",
            "#nofilter today" to "#nofilter today",
            "#tbt again" to "#tbt again",
            "mail jonh.smith@gmail.com now" to "mail jonh.smith@gmail.com now",
            "open teh.txt please" to "open teh.txt please",
            "rename my_varible later" to "rename my_varible later",
            // A "." that ends the sentence still gets its fix.
            "I went hme. Then" to "I went home. Then"
        )
        val wrong = cases.mapNotNull { (typed, expected) ->
            type(context, controller, typed).takeIf { it != expected }?.let { "$typed -> $it (want $expected)" }
        }
        controller.destroy()
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun neighbourKeySlipsAndTheWordBeforePickTheFix() {
        val context = RuntimeEnvironment.getApplication()
        val (controller, _) = englishController(context)
        val cases = listOf(
            // Field reports: a slip on the first letter, and a slip into a day of the week.
            "Vool plan" to "Cool plan",
            "see you on fridat then" to "see you on Friday then",
            // The word before picks between equally likely slips.
            "can you pick up some kilk on the way" to "can you pick up some milk on the way",
            "I will be able fo come" to "I will be able to come",
            // A name written twice in the conversation: a slip on it is fixed.
            "Ximena is here. I told Ximena. Ximwna said hi and I told Ximwna thanks" to
                "Ximena is here. I told Ximena. Ximena said hi and I told Ximena thanks",
            // A capitalized slip on a day, an extra letter on a sentence's first word, short words.
            "See you next Sudnay then" to "See you next Sunday then",
            "Thefy made it" to "They made it",
            "I don't know wat happened" to "I don't know what happened",
            "Why do people ike it" to "Why do people like it",
            // Well-known misspellings have one fix.
            "I did not recieve it" to "I did not receive it",
            "Your arguement is fine" to "Your argument is fine",
            // A first name stays, even one slip from a common word.
            "Ali drank tea" to "Ali drank tea",
            // A missed space next to a name; brand names and code stay.
            "andMary went home" to "and Mary went home",
            "Tomis here" to "Tom is here",
            "my iPhone works" to "my iPhone works",
            "myVariable is set" to "myVariable is set",
            // Field report: texting shorthand, and a far-key swap is no slip.
            "my pref would be tacos" to "my pref would be tacos",
            // Slang and words held for emphasis stay; one extra key is still a slip.
            "ngl im tryna lowkey yeet it rn" to "ngl I'm tryna lowkey yeet it rn",
            "lmk if u wanna hmu tmrw" to "lmk if u wanna hmu tmrw",
            "that was sooo good pleaseee come" to "that was sooo good pleaseee come",
            "we alll went" to "we all went",
            // Spelling mistakes: one vowel for another.
            "we need a sepereate permit" to "we need a separate permit",
            "that is definatly true" to "that is definitely true",
            // Words run together, from the common misspellings list.
            "alot of people like it aswell now" to "a lot of people like it as well now",
            // Doubled letters left out of a long word; a split only where the two words go together.
            "the acomodation was nice" to "the accommodation was nice",
            // A short non-word that both neighbours explain; unclear ones and real words stay.
            "the cat hs fur" to "the cat has fur",
            "I hs a car" to "I hs a car",
            "return the ewe to her" to "return the ewe to her"
        )
        val wrong = cases.mapNotNull { (typed, expected) ->
            type(context, controller, typed).takeIf { it != expected }?.let { "$typed -> $it (want $expected)" }
        }
        controller.destroy()
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun contractionsFollowTheSentence() {
        val context = RuntimeEnvironment.getApplication()
        val (controller, _) = englishController(context)
        val cases = listOf(
            "ill be there soon" to "I'll be there soon",
            "ok ill call you" to "ok I'll call you",
            "its raining again" to "it's raining again",
            "dont worry" to "don't worry",
            // Field report: the next word shows "ill" was "I'll".
            "thanks for your projects ill quit soon" to "thanks for your projects I'll quit soon",
            "I think it was 12 gallons ill check my old phone again" to "I think it was 12 gallons I'll check my old phone again",
            "Ok, ill call you" to "Ok, I'll call you",
            "I ll quit now" to "I'll quit now",
            "I don t know" to "I don't know",
            "she fell ill and stayed home" to "she fell ill and stayed home",
            "id like that a lot" to "I'd like that a lot",
            "show your id card please" to "show your id card please",
            // Field reports: the next word shows the apostrophe was missed; possessives stay.
            "it's messy and its mostly on there" to "it's messy and it's mostly on there",
            "it was it's own thing" to "it was its own thing",
            "the dog wagged its tail" to "the dog wagged its tail",
            "He was very ill" to "He was very ill",
            "the dog wagged its tail" to "the dog wagged its tail",
            "Have you got your ID" to "Have you got your ID",
            "an old storage shed" to "an old storage shed"
        )
        val wrong = cases.mapNotNull { (typed, expected) ->
            type(context, controller, typed).takeIf { it != expected }?.let { "$typed -> $it (want $expected)" }
        }
        controller.destroy()
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun everydayMessagesWithRealisticSlips() {
        val context = RuntimeEnvironment.getApplication()
        val (controller, repository) = englishController(context)

        val report = StringBuilder()
        val results = listOf("chat_messages_en.txt", "tatoeba_heldout_en.txt").map { name ->
            val lines = javaClass.classLoader!!.getResource("simulation/$name")!!.readText()
                .lines().filter { it.isNotBlank() && !it.startsWith("#") }
            name to simulate(context, controller, repository, lines, Random(SEED), report)
        }
        controller.destroy()

        val summary = StringBuilder("\nCHAT SIMULATION (seed $SEED, ${(SLIP_RATE * 100).toInt()}% of words slipped)\n")
        results.forEach { (name, score) -> summary.append(summarize(name, score)) }
        File("build/reports").mkdirs()
        File("build/reports/chat-simulation.txt").writeText(summary.toString() + report)
        println(summary)

        results.forEach { (name, score) ->
            val clean = score.cleanWords[0]
            assertTrue(
                "$name: correct words changed ${score.falseChanges.size}/$clean: ${score.falseChanges.take(10)}",
                score.falseChanges.size * 1000 <= clean * MAX_FALSE_CHANGES_PER_THOUSAND
            )
            val slips = score.total.values.sum()
            val fixed = score.fixed.values.sum()
            assertTrue("$name: slips fixed $fixed/$slips", fixed * 100 >= slips * MIN_FIXED_PERCENT)
        }
    }

    private fun summarize(name: String, score: Score): String = buildString {
        val slips = score.total.values.sum()
        val fixed = score.fixed.values.sum()
        append("== $name\n")
        append("  correct words changed  ${score.falseChanges.size}/${score.cleanWords[0]}\n")
        append("  slips fixed            $fixed/$slips (${if (slips > 0) fixed * 100 / slips else 0}%)\n")
        score.total.forEach { (kind, n) -> append("    %-26s %4d/%-4d\n".format(kind, score.fixed[kind] ?: 0, n)) }
        append("  slips made worse       ${score.wrongFixes.size}\n")
        append("  sentences misaligned   ${score.misaligned.size}\n")
    }

    private fun simulate(
        context: Context,
        controller: SuggestionController,
        repository: DictionaryRepository,
        sentences: List<String>,
        random: Random,
        report: StringBuilder
    ): Score {
        val score = Score()
        sentences.forEach { sentence ->
            val words = injectSlips(sentence.split(' '), random, repository)
            val typed = words.joinToString(" ") { it.typed }.replace("\u0000 ", "")
            val output = type(context, controller, typed)
            score(words, sentence, typed, output, score) { word ->
                val core = word.trim { !it.isLetter() && it != '\'' }
                val frequency = repository.getExactWordFrequency(core)
                if (frequency > 0) "known f=$frequency" else "unknown"
            }
        }
        report.append("\n\n######## ${sentences.size} sentences\n")
        report.append("\n--- CORRECT WORDS CHANGED (${score.falseChanges.size})\n").append(score.falseChanges.joinToString("\n"))
        report.append("\n--- SLIPS MADE WORSE (${score.wrongFixes.size})\n").append(score.wrongFixes.joinToString("\n"))
        report.append("\n--- SLIPS MISSED (${score.missed.size})\n").append(score.missed.joinToString("\n"))
        report.append("\n--- MISALIGNED (${score.misaligned.size})\n").append(score.misaligned.joinToString("\n"))
        return score
    }

    /** Types [text] the way the keyboard reports it: letters, then boundaries (space, punctuation). */
    private fun type(context: Context, controller: SuggestionController, text: String): String {
        val input = FakeInputConnection(context)
        controller.onContextReset()
        for (ch in text) {
            when {
                ch == ' ' -> {
                    if (input.text.endsWith(" ")) continue // Auto-space after punctuation took it.
                    if (!controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).committed) input.append(" ")
                }
                ch in BOUNDARY_PUNCTUATION -> {
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
        return input.text.trimEnd()
    }

    private fun score(
        words: List<Word>,
        sentence: String,
        typed: String,
        output: String,
        score: Score,
        describe: (String) -> String
    ) {
        val line = "$sentence\n      typed: $typed\n      got:   $output"
        val intended = sentence.split(' ')
        val got = output.split(' ')
        // A missed space typed two intended words as one token (the first one carries the marker).
        val typedTokens = mutableListOf<Pair<String, List<Int>>>()
        var i = 0
        while (i < words.size) {
            if (words[i].kind == MISSED_SPACE) {
                typedTokens += words[i].typed.removeSuffix("\u0000") + words[i + 1].typed to listOf(i, i + 1)
                i += 2
            } else {
                typedTokens += words[i].typed to listOf(i)
                i++
            }
        }
        when (got.size) {
            intended.size -> words.forEachIndexed { index, word ->
                if (word.kind == MISSED_SPACE && index > 0 && words[index - 1].kind == MISSED_SPACE &&
                    words[index - 1].typed.endsWith("\u0000")
                ) {
                    return@forEachIndexed // Second half of a missed space: scored with the first.
                }
                if (word.kind == MISSED_SPACE) {
                    val ok = got[index] == intended[index] && got[index + 1] == intended[index + 1]
                    score.slip(MISSED_SPACE, if (ok) null else "worse", "${intended[index]} ${intended[index + 1]} -> ${got[index]} ${got[index + 1]} | $line")
                } else {
                    tally(word, got[index], line, score, describe)
                }
            }
            typedTokens.size -> typedTokens.forEachIndexed { index, (token, indices) ->
                if (indices.size == 2) {
                    score.slip(MISSED_SPACE, if (got[index] == token) "missed" else "worse", "$token -> ${got[index]} | $line")
                } else {
                    tally(words[indices[0]], got[index], line, score, describe)
                }
            }
            else -> score.misaligned += line
        }
    }

    private fun tally(word: Word, got: String, line: String, score: Score, describe: (String) -> String) {
        if (word.kind == null) {
            score.cleanWords[0]++
            if (got != word.intended) score.falseChanges += "${word.intended} -> $got [${describe(word.intended)}] | $line"
        } else {
            val outcome = when (got) {
                word.intended -> null
                word.typed -> "missed"
                else -> "worse"
            }
            score.slip(word.kind, outcome, "${word.typed} -> $got (want ${word.intended}) [${describe(word.typed)}] | $line")
        }
    }

    // ---- Slip model: rough shares of one-key slips on a physical keyboard. ----

    private fun injectSlips(tokens: List<String>, random: Random, repository: DictionaryRepository): List<Word> {
        val words = tokens.map { Word(it, it, null) }.toMutableList()
        var i = 0
        while (i < words.size) {
            val token = tokens[i]
            val core = token.trimEnd { it in BOUNDARY_PUNCTUATION }
            val tail = token.substring(core.length)
            if (core.length < 2 || !core.all { it.isLetter() } || random.nextDouble() >= SLIP_RATE) {
                i++
                continue
            }
            val roll = random.nextDouble()
            val next = tokens.getOrNull(i + 1)?.trimEnd { it in BOUNDARY_PUNCTUATION }
            if (roll < 0.08 && tail.isEmpty() && next != null && next.length >= 2 && next.all { it.isLetter() }) {
                // The space was missed: "\u0000" marks where it was, so the typed text joins the words.
                words[i] = Word(token, "$token\u0000", MISSED_SPACE)
                words[i + 1] = Word(tokens[i + 1], tokens[i + 1], MISSED_SPACE)
                i += 2
                continue
            }
            val slipped = when {
                roll < 0.14 && core.length >= 3 && core[0].isUpperCase() && core[1].isLowerCase() ->
                    "shift held too long" to core[0] + core[1].uppercase() + core.substring(2)
                roll < 0.44 -> "neighbour key" to neighbourKey(core, random)
                roll < 0.69 && core.length >= 3 -> "missed letter" to core.removeRange(random.nextInt(core.length).let { it..it })
                roll < 0.84 -> "extra letter" to extraLetter(core, random)
                else -> "swapped letters" to swap(core, random)
            }
            val (kind, typedCore) = slipped
            if (typedCore == null || typedCore == core) {
                i++
                continue
            }
            val label = if (repository.getExactWordFrequency(typedCore) > 0) "$kind (real word)" else kind
            words[i] = Word(token, typedCore + tail, label)
            i++
        }
        return words
    }

    private fun neighbourKey(word: String, random: Random): String? {
        val index = random.nextInt(word.length)
        val options = neighbours(word[index].lowercaseChar())
        if (options.isEmpty()) return null
        val key = options.random(random).let { if (word[index].isUpperCase()) it.uppercaseChar() else it }
        return word.replaceRange(index, index + 1, key.toString())
    }

    private fun extraLetter(word: String, random: Random): String {
        val index = random.nextInt(word.length)
        val base = word[index].lowercaseChar()
        val extra = if (random.nextBoolean()) base else neighbours(base).randomOrNull(random) ?: base
        return word.substring(0, index + 1) + extra + word.substring(index + 1)
    }

    private fun swap(word: String, random: Random): String? {
        val index = random.nextInt(word.length - 1)
        if (word[index] == word[index + 1] || word[index].isUpperCase()) return null
        return word.substring(0, index) + word[index + 1] + word[index] + word.substring(index + 2)
    }

    private fun neighbours(key: Char): List<Char> {
        val (row, column) = KEY_POSITIONS[key] ?: return emptyList()
        return KEY_POSITIONS.filter { (other, position) ->
            other != key && Math.hypot((position.first - row).toDouble(), (position.second - column).toDouble()) <= 1.5
        }.keys.toList()
    }

    private class FakeInputConnection(context: Context) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder()
        val text: String get() = buffer.toString()
        fun append(text: String) {
            buffer.append(text)
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

    private companion object {
        const val SEED = 7
        const val SLIP_RATE = 0.12
        const val MISSED_SPACE = "missed space"
        const val BOUNDARY_PUNCTUATION = ".,!?;:"
        /** Guards (as of 2026-10-05: 0.3 per thousand changed, 61-64% fixed); tighten as heuristics improve. */
        const val MAX_FALSE_CHANGES_PER_THOUSAND = 1
        const val MIN_FIXED_PERCENT = 72

        val KEY_POSITIONS: Map<Char, Pair<Int, Int>> = buildMap {
            "qwertyuiop".forEachIndexed { i, c -> put(c, 0 to i) }
            "asdfghjkl".forEachIndexed { i, c -> put(c, 1 to i) }
            // The keyboard the build is for: Numen's Titan 2 Elite has no gap in the bottom row.
            "zxcvbnm".mapIndexed { i, c -> c to i + 1 }
        
.forEach { (c, i) -> put(c, 2 to i) }
        }
    }
}
