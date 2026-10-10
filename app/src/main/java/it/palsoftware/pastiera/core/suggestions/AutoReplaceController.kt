package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.Punctuation
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import it.palsoftware.pastiera.inputmethod.DebugCaptureStore
import kotlin.math.ln

class AutoReplaceController(
    private val repository: DictionaryRepository,
    private val suggestionEngine: SuggestionEngine,
    private val settingsProvider: () -> SuggestionSettings,
    private val knownWordProvider: ((String) -> Boolean)? = null,
    private val exactReplacementProvider: ((String, Char?) -> String?)? = null,
    private val keptOnceStore: KeptOnceStore = KeptOnceStore.inMemory(),
    /** Saves a learned word to the personal dictionary (every active language). */
    private val learnWord: (String) -> Unit = repository::addUserEntryQuick,
    /**
     * Whether a text replacement may rewrite a real word ("ill" -> "I'll") after [previous],
     * which is [PairCounts.SENTENCE_START] at a sentence start and null when unclear.
     */
    private val realWordReplacementAllowed: (previous: String?, word: String, replacement: String) -> Boolean =
        { _, _, _ -> true },
    /** False in fields that ask the keyboard not to learn, such as incognito tabs. */
    private val isLearningAllowed: () -> Boolean = { true },
    /** How often [second] follows [first] in everyday text (lowercase); 0 when unknown or not loaded. */
    private val pairCount: (first: String, second: String) -> Int = { _, _ -> 0 },
    /** Words written in this conversation, filled by the caller. */
    private val recentWords: RecentWords = RecentWords(),
    /** How often [third] follows [first] [second] (lowercase); 0 when unknown or not loaded. */
    private val tripleCount: (first: String, second: String, third: String) -> Int = { _, _, _ -> 0 },
    /** Whether [pairCount] has data; until then a missing pair proves nothing. */
    private val pairsReady: () -> Boolean = { false },
    /** Context x error-model fixes for made-up words (English, aggressive mode). */
    private val noisyChannel: NoisyChannel? = null
) {
    private fun triggerFromBoundaryChar(boundaryChar: Char?): DebugCaptureStore.AutoCorrectionTrigger {
        return when (boundaryChar) {
            ' ' -> DebugCaptureStore.AutoCorrectionTrigger.SPACE
            '\n' -> DebugCaptureStore.AutoCorrectionTrigger.ENTER
            else -> DebugCaptureStore.AutoCorrectionTrigger.OTHER
        }
    }

    private fun punctuationBoundaryForKeyCode(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_COMMA -> ','
            KeyEvent.KEYCODE_PERIOD -> '.'
            KeyEvent.KEYCODE_SEMICOLON -> ';'
            KeyEvent.KEYCODE_SLASH -> '/'
            else -> null
        }
    }
    public data class ReplaceResult(
        val replaced: Boolean,
        val committed: Boolean,
        val replacement: String? = null
    )

    companion object {
        private const val UNDO_TRAILING_LOOKBACK = 4
        private const val PLURAL_MIN_LENGTH = 5
        private const val COMPOUND_MIN_HALF = 3
        private const val COMPOUND_HALF_MIN_FREQUENCY = 100
        private const val COMPOUND_FIX_MIN_FREQUENCY = 100
        // Plurals of very common words are in the dictionary already; "agains" is a slip.
        private const val PLURAL_BASE_MAX_FREQUENCY = 140
        private val UNDO_BOUNDARY_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':')
        private const val AGGRESSIVE_CANDIDATE_LIMIT = 24
        private const val UNDO_CONTEXT_LENGTH = 64
        // Aggressive ranking: typo likelihood dominates, word frequency (0..~220) breaks ties.
        private const val FREQUENCY_WEIGHT = 0.6
        // Bonus for a candidate that often follows the word before, saturating at this pair count.
        private const val CONTEXT_WEIGHT = 0.8
        // When the noisy-channel model weighs a made-up word but is unsure, the older guess is skipped.
        private const val NOISY_CHANNEL_DECIDES = false
        private const val CONTEXT_SATURATION_COUNT = 1000.0
        // The same for the two words before.
        private const val TRIPLE_WEIGHT = 0.3
        private const val TRIPLE_SATURATION_COUNT = 100.0

        private fun saturating(count: Int, saturation: Double): Double =
            if (count <= 0) 0.0 else minOf(1.0, ln(1.0 + count) / ln(1.0 + saturation))
        // Bonus for a candidate written earlier in this conversation, and how likely a slip onto
        // such a word must be (a neighbouring key, swapped letters, or one missed or extra letter).
        private const val RECENT_WORD_BONUS = 0.2
        private const val RECENT_SLIP_MIN_LIKELIHOOD = 0.65
        private const val RECENT_SLIP_MAX_COMMON_FREQUENCY = 140
        private const val MAX_FREQUENCY = 220.0
        // Long words are rarely mistyped into other words, so far-key substitutions are allowed.
        private const val PROXIMITY_FILTER_MAX_LENGTH = 7
        // A first-letter slip may only become a common word ("vool" -> "cool"), never a rare one.
        private const val FIRST_LETTER_FIX_MIN_FREQUENCY = 115
        private const val ADDRESS_LOOKBACK = 64
        private const val MIN_SPLIT_WORD_LENGTH = 4
        // deepFix tuning: fix badly mangled common words but leave rare unknown words alone.
        private const val DEEP_SLIP_COST = 0.5
        private const val DEEP_VOWEL_COST = 0.5
        private const val VOWELS = "aeiou"
        private const val DEEP_MISSED_COST = 0.7
        private const val DEEP_EXTRA_COST = 0.9
        private const val DEEP_FIX_MIN_LENGTH = 7
        private const val DEEP_FIX_MAX_LENGTH_DIFFERENCE = 2
        private const val DEEP_FIX_MIN_FREQUENCY = 100
        private const val DEEP_FIX_MAX_COST = 1.25
        private const val DEEP_FIX_MIN_MARGIN = 0.3
        private const val DEEP_FIX_CANDIDATE_LIMIT = 6000
        private const val TWO_LETTER_FIX_MIN_FREQUENCY = 150
        private const val TWO_LETTER_FIX_MIN_MARGIN = 0.15
        // A missed first letter ("ike" -> "like", "ubject" -> "subject") is fixed only toward a
        // common word.
        private const val MISSED_FIRST_LETTER_MIN_LENGTH = 3
        private const val MISSED_FIRST_LETTER_MIN_FREQUENCY = 110
        // A capitalised word may be a name, so at a sentence start it is only fixed by a likely
        // slip to a common lowercase word.
        private const val SENTENCE_START_FIX_MIN_FREQUENCY = 115
        private const val SENTENCE_START_FIX_MIN_LIKELIHOOD = 0.65
        // Names are rarely one slip away from a long everyday word, so those may be less common.
        private const val SENTENCE_START_LONG_WORD_LENGTH = 7
        private const val SENTENCE_START_LONG_FIX_MIN_FREQUENCY = 60
        // A lowercase slip only becomes a name or place when that name is common.
        private const val PROPER_NOUN_FIX_MIN_FREQUENCY = 130
        private const val PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY = 100
        private const val PROPER_NOUN_SLIP_MIN_LENGTH = 5
        // Up to this length, swapping a letter for a far key is not a slip ("pref" is not "prey").
        private const val FAR_KEY_SWAP_MAX_LENGTH = 5
        // typoLikelihood of a missed letter, swapped letters or a doubled key.
        private const val CLASSIC_SLIP_LIKELIHOOD = 0.8
        // Letters that usually end a word on purpose rather than by a slip.
        private const val SUFFIX_LETTERS = "sdeyrn"
        // A fix this unlikely loses to a clean missed-space split.
        private const val WEAK_FIX_LIKELIHOOD = 0.5
        // So does a fix to a word far rarer than both halves of the split.
        private const val COMMON_SPLIT_MIN_FREQUENCY = 150
        private const val COMMON_SPLIT_MIN_GAIN = 40
        // English clitics after an apostrophe.
        private val CLITICS = setOf("s", "ll", "re", "ve", "d", "m")
        private const val SPLIT_LOOKBACK = 4
        private const val PREVIOUS_WORD_LOOKBACK = 32
        // Both halves of a split must be common words, and two-letter halves very common.
        private const val MIN_SPLIT_PART_FREQUENCY = 100
        private const val MIN_SPLIT_SHORT_PART_FREQUENCY = 150
        // A missed space after or before a little everyday word ("of", "by", "the").
        private const val GLUE_WORD_MAX_LENGTH = 4
        private const val GLUE_WORD_MIN_FREQUENCY = 170
        private const val UNATTESTED_SPLIT_MIN_FREQUENCY = 130
        private const val GLUE_OTHER_MIN_LENGTH = 4
        // A missed space next to a name ("andMary", "Tomis"); surname endings never split ("Peterson").
        private const val NAME_SPLIT_MIN_WORD_FREQUENCY = 120
        // Contractions whose apostrophe is often typed as a space ("I ll"). Only unambiguous ones.
        private val SPACED_CONTRACTIONS = buildSet {
            listOf("i", "you", "we", "they", "he", "she", "it", "that", "there", "who", "what").forEach { add("$it'll") }
            listOf("i", "you", "we", "they", "he", "she", "that", "there", "who").forEach { add("$it'd") }
            listOf("i", "you", "we", "they", "could", "would", "should", "might", "must", "who").forEach { add("$it've") }
            listOf("you", "we", "they", "who", "what").forEach { add("$it're") }
            add("i'm")
            listOf("it", "that", "what", "there", "let").forEach { add("$it's") }
            listOf("don", "can", "won", "isn", "aren", "wasn", "weren", "didn", "doesn", "hasn", "haven", "hadn",
                "couldn", "wouldn", "shouldn", "mustn", "ain").forEach { add("$it't") }
        }
        private val SURNAME_ENDINGS = setOf("son", "sen", "ton", "ley", "ford", "berg", "stein", "man", "field", "wood", "worth", "by", "er", "ing", "ville")

        /**
         * Fixes a two-letter non-word ("si" -> "is") towards a very common word, only when that
         * fix clearly beats every other.
         */
        internal fun twoLetterFix(
            word: String,
            frequencyOf: (String) -> Int,
            isNeighborKey: (Char, Char) -> Boolean,
            contextFit: (String) -> Double = { 0.0 }
        ): String? {
            if (word.length != 2 || !word.all { it in 'a'..'z' }) return null
            val candidates = buildSet {
                add(word.reversed())
                for (i in 0..1) for (c in 'a'..'z') if (c != word[i] && isNeighborKey(word[i], c)) add(word.replaceRange(i, i + 1, c.toString()))
                for (i in 0..2) for (c in 'a'..'z') add(word.substring(0, i) + c + word.substring(i))
            }
            val ranked = candidates
                .filter { candidate -> frequencyOf(candidate) >= TWO_LETTER_FIX_MIN_FREQUENCY }
                .map { candidate ->
                    candidate to typoLikelihood(word, candidate, isNeighborKey) +
                        FREQUENCY_WEIGHT * frequencyOf(candidate) / MAX_FREQUENCY + contextFit(candidate)
                }
                .sortedByDescending { (_, score) -> score }
            val (best, score) = ranked.firstOrNull() ?: return null
            val runnerUp = ranked.getOrNull(1)?.second ?: 0.0
            return best.takeIf { score - runnerUp >= TWO_LETTER_FIX_MIN_MARGIN }
        }

        /**
         * Keyboard-weighted edit cost of [typed] against [word]. An extra letter is cheap when a
         * finger could have added it (doubled, or next to a letter beside it).
         */
        internal fun keyboardWeightedCost(typed: String, word: String, isNeighborKey: (Char, Char) -> Boolean): Double {
            val t = typed.lowercase()
            val w = word.lowercase()
            // Rolling rows: i-2 (for swapped letters), i-1 and i.
            var twoBack = DoubleArray(w.length + 1)
            var previous = DoubleArray(w.length + 1) { j -> j * DEEP_MISSED_COST }
            var current = DoubleArray(w.length + 1)
            for (i in 1..t.length) {
                val letter = t[i - 1]
                val before = t.getOrNull(i - 2)
                val after = t.getOrNull(i)
                val fatFinger = (before != null && (before == letter || isNeighborKey(letter, before))) ||
                    (after != null && (after == letter || isNeighborKey(letter, after)))
                val extra = if (fatFinger) DEEP_SLIP_COST else DEEP_EXTRA_COST
                current[0] = previous[0] + DEEP_EXTRA_COST
                for (j in 1..w.length) {
                    val target = w[j - 1]
                    val substitution = when {
                        letter == target -> 0.0
                        isNeighborKey(letter, target) -> DEEP_SLIP_COST
                        // Spelling, not fingers: one vowel for another ("seperate", "definately").
                        letter in VOWELS && target in VOWELS -> DEEP_VOWEL_COST
                        else -> 1.0
                    }
                    // One of a doubled letter left out ("acomodation", "embaras") is a spelling habit.
                    val missed = if (j > 1 && w[j - 1] == w[j - 2]) DEEP_SLIP_COST else DEEP_MISSED_COST
                    var cost = minOf(previous[j] + extra, current[j - 1] + missed, previous[j - 1] + substitution)
                    if (i > 1 && j > 1 && letter == w[j - 2] && before == target && letter != target) {
                        cost = minOf(cost, twoBack[j - 2] + DEEP_SLIP_COST)
                    }
                    current[j] = cost
                }
                val recycled = twoBack
                twoBack = previous
                previous = current
                current = recycled
            }
            return previous[w.length]
        }

        /**
         * Last resort for a long word too mangled for the regular fix: the common word with the
         * same first letter that a few likely slips explain, if it clearly beats every other.
         */
        internal fun deepFix(
            word: String,
            candidates: List<DictionaryEntry>,
            isNeighborKey: (Char, Char) -> Boolean
        ): String? {
            if (word.length < DEEP_FIX_MIN_LENGTH || !word.all { it.isLetter() }) return null
            val ranked = candidates.asSequence()
                .filter { entry ->
                    entry.frequency >= DEEP_FIX_MIN_FREQUENCY && entry.word.all { it.isLetter() } &&
                        // Two letters apart covers both of two doubled letters left out ("embaras").
                        kotlin.math.abs(entry.word.length - word.length) <= DEEP_FIX_MAX_LENGTH_DIFFERENCE &&
                        entry.word.first().equals(word.first(), ignoreCase = true)
                }
                .mapNotNull { entry ->
                    val cost = keyboardWeightedCost(word, entry.word, isNeighborKey)
                    if (cost > DEEP_FIX_MAX_COST) null
                    else entry.word to cost - FREQUENCY_WEIGHT * entry.frequency / MAX_FREQUENCY
                }
                .sortedBy { (_, score) -> score }
                .take(2)
                .toList()
            val (best, score) = ranked.firstOrNull() ?: return null
            val runnerUp = ranked.getOrNull(1)?.second
            return best.takeIf { runnerUp == null || runnerUp - score >= DEEP_FIX_MIN_MARGIN }
        }

        /** Best "left right" split of a word typed without its space, or null if none is convincing. */
        /**
         * A real word with its last letter held for emphasis ("sooo", "pleaseee"), not a slip. Three
         * letters where the doubled form is a word ("alll") is one extra key instead.
         */
        internal fun isStretchedForEmphasis(word: String, isKnown: (String) -> Boolean): Boolean {
            val last = word.lastOrNull()?.takeIf { it.isLetter() } ?: return false
            val run = word.takeLastWhile { it.equals(last, ignoreCase = true) }.length
            if (run < 3) return false
            val stem = word.dropLast(run)
            val doubled = isKnown(stem + last + last)
            return if (run == 3) isKnown(stem + last) && !doubled else isKnown(stem + last) || doubled
        }

        internal fun findWordSplit(
            word: String,
            frequencyOf: (String) -> Int,
            isKnownWord: (String) -> Boolean = { false },
            // Whether the two words are seen together in real text ("see you", not "con tuned").
            isAttested: (String, String) -> Boolean = { _, _ -> true }
        ): String? {
            if (word.length < MIN_SPLIT_WORD_LENGTH || !word.all { it.isLetter() }) return null
            var best: String? = null
            var bestScore = 0
            for (i in 2..word.length - 2) {
                val left = word.substring(0, i)
                val right = word.substring(i)
                if (!isAttested(left, right)) continue
                val score = minOf(splitPartScore(left, frequencyOf), splitPartScore(right, frequencyOf))
                if (score > bestScore) {
                    bestScore = score
                    best = "$left $right"
                }
            }
            if (best != null) return best
            // A very common little word glued to a rarer real one ("ofnausea", "tormentedby").
            return (2..word.length - 2).map { word.substring(0, it) to word.substring(it) }
                .filter { (left, right) ->
                    if (!isAttested(left, right)) return@filter false
                    val (common, other) = if (frequencyOf(left.lowercase()) >= frequencyOf(right.lowercase())) left to right else right to left
                    common.length <= GLUE_WORD_MAX_LENGTH && frequencyOf(common.lowercase()) >= GLUE_WORD_MIN_FREQUENCY &&
                        other.length >= GLUE_OTHER_MIN_LENGTH && isKnownWord(other)
                }
                .singleOrNull()
                ?.let { (left, right) -> "$left $right" }
        }

        private fun splitPartScore(part: String, frequencyOf: (String) -> Int): Int {
            val frequency = frequencyOf(part.lowercase())
            val minimum = if (part.length == 2) MIN_SPLIT_SHORT_PART_FREQUENCY else MIN_SPLIT_PART_FREQUENCY
            return if (frequency >= minimum) frequency else 0
        }

        /**
         * Whether the word is part of an email address or link: before or after "@", or after
         * "/" or "www." in the same unspaced chunk. Bare domains are not detected.
         */
        internal fun isInsideAddress(textBeforeCursor: String, boundaryChar: Char?): Boolean {
            if (boundaryChar == '@') return true
            val chunk = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
            // Also hashtags and code identifiers ("#nofilter", "my_var").
            return chunk.contains('@') || chunk.contains('/') || chunk.contains('#') || chunk.contains('_') ||
                chunk.startsWith("www.", ignoreCase = true)
        }

        internal data class ApostropheSplit(val prefix: String, val root: String)

        internal fun normalizeApostrophes(input: String): String {
            return WordNormalization.normalizeApostrophes(input)
        }

        internal fun stripAccents(input: String): String {
            return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
        }

        /**
         * Split a word with a single apostrophe into prefix (with apostrophe) and root.
         * Language-agnostic: only checks structure/length, not locale.
         */
        internal fun splitApostropheWord(word: String): ApostropheSplit? {
            val normalized = normalizeApostrophes(word)
            val apostropheCount = normalized.count { it == '\'' }
            if (apostropheCount != 1) return null
            val idx = normalized.indexOf('\'')
            if (idx <= 0 || idx >= normalized.lastIndex) return null

            val prefix = normalized.substring(0, idx + 1)
            val root = normalized.substring(idx + 1)
            val prefixRaw = prefix.dropLast(1)

            val isPrefixOk = prefixRaw.isNotEmpty() &&
                    isSupportedApostrophePrefix(prefixRaw) &&
                    prefixRaw.all { it.isLetter() }
            val isRootOk = root.length >= 3 && root.all { it.isLetter() }
            return if (isPrefixOk && isRootOk) ApostropheSplit(prefix, root) else null
        }

        private fun isSupportedApostrophePrefix(prefix: String): Boolean {
            if (prefix.length <= 3) return true
            return prefix.lowercase(Locale.ROOT) in setOf(
                "dall",
                "dell",
                "nell",
                "sull",
                "coll",
                "quell",
                "quest"
            )
        }

        internal fun isAccentOnlyVariant(input: String, candidate: String): Boolean {
            if (input.equals(candidate, ignoreCase = true)) return false
            val normalizedInput = WordNormalization.normalizeForDictionary(input, Locale.ROOT)
            val normalizedCandidate = WordNormalization.normalizeForDictionary(candidate, Locale.ROOT)
            return normalizedInput == normalizedCandidate
        }

        internal fun isCaseOnlyVariant(input: String, candidate: String): Boolean {
            if (input.isEmpty() || candidate.isEmpty()) return false
            return input != candidate && input.equals(candidate, ignoreCase = true)
        }

        /**
         * How plausible [candidate] is as the intended word for [input] on a physical keyboard,
         * ranked by how often each kind of slip happens.
         */
        internal fun typoLikelihood(input: String, candidate: String, isNeighborKey: (Char, Char) -> Boolean): Double {
            val typed = input.lowercase()
            val word = candidate.lowercase()
            val substitution = singleSubstitution(typed, word)
            return when {
                hasSingleRepeatedCharInsertion(typed, word) || hasSingleRepeatedCharInsertion(word, typed) -> 1.0
                isAdjacentTransposition(typed, word) -> 0.9
                isSingleInsertion(typed, word) -> 0.8
                substitution != null && isNeighborKey(substitution.first, substitution.second) -> 0.8
                isSingleInsertion(word, typed) && isFatFingerInsertion(typed, word, isNeighborKey) -> 0.65
                isSingleInsertion(word, typed) -> 0.5
                substitution != null -> 0.4
                else -> 0.3
            }
        }

        /** [typed] has one extra letter on a key next to a letter beside it. */
        private fun isFatFingerInsertion(typed: String, word: String, isNeighborKey: (Char, Char) -> Boolean): Boolean {
            val i = word.indices.firstOrNull { word[it] != typed[it] } ?: word.length
            val extra = typed[i]
            return listOfNotNull(typed.getOrNull(i - 1), typed.getOrNull(i + 1))
                .any { beside -> isNeighborKey(extra.lowercaseChar(), beside.lowercaseChar()) }
        }

        /** "willk" -> "will": a key next to the last letter was hit too. */
        private fun isTrailingFatFinger(input: String, candidate: String, isNeighborKey: (Char, Char) -> Boolean): Boolean {
            if (input.length != candidate.length + 1 || !input.startsWith(candidate, ignoreCase = true)) return false
            val extra = input.last().lowercaseChar()
            return extra !in SUFFIX_LETTERS && isNeighborKey(extra, input[input.length - 2].lowercaseChar())
        }

        /**
         * A known word, or a capitalised name when [rootMayBeName], plus an English clitic
         * ("waiter's"). The dictionary lists few of these, so they are kept as typed.
         */
        internal fun isWordWithClitic(word: String, isKnown: (String) -> Boolean, rootMayBeName: Boolean = false): Boolean {
            val normalized = normalizeApostrophes(word)
            val apostrophe = normalized.lastIndexOf('\'')
            if (apostrophe <= 0 || normalized.substring(apostrophe + 1).lowercase() !in CLITICS) return false
            val root = normalized.substring(0, apostrophe)
            return root.all { it.isLetter() } && (rootMayBeName && root.first().isUpperCase() || isKnown(root))
        }

        /** [long] is [short] with exactly one extra character somewhere. */
        private fun isSingleInsertion(short: String, long: String): Boolean {
            if (long.length != short.length + 1) return false
            val i = short.indices.firstOrNull { short[it] != long[it] } ?: short.length
            return short.substring(i) == long.substring(i + 1)
        }

        private fun singleSubstitution(a: String, b: String): Pair<Char, Char>? {
            if (a.length != b.length) return null
            val diffs = a.indices.filter { a[it] != b[it] }
            return diffs.singleOrNull()?.let { a[it] to b[it] }
        }

        private fun isAdjacentTransposition(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            val diffs = a.indices.filter { a[it] != b[it] }
            return diffs.size == 2 && diffs[1] == diffs[0] + 1 &&
                a[diffs[0]] == b[diffs[1]] && a[diffs[1]] == b[diffs[0]]
        }

        /** Optimal string alignment distance (a swap of neighbours counts as one edit). */
        internal fun editDistance(a: String, b: String): Int {
            val x = a.lowercase()
            val y = b.lowercase()
            val d = Array(x.length + 1) { IntArray(y.length + 1) }
            for (i in 0..x.length) d[i][0] = i
            for (j in 0..y.length) d[0][j] = j
            for (i in 1..x.length) {
                for (j in 1..y.length) {
                    val cost = if (x[i - 1] == y[j - 1]) 0 else 1
                    d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && x[i - 1] == y[j - 2] && x[i - 2] == y[j - 1]) {
                        d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                    }
                }
            }
            return d[x.length][y.length]
        }

        /** A first-letter mistake that is still a classic slip ("hte" -> "the"). */
        private fun isPlausibleFirstLetterTypo(input: String, candidate: String, isNeighborKey: (Char, Char) -> Boolean): Boolean {
            val typed = input.lowercase()
            val word = candidate.lowercase()
            if (isAdjacentTransposition(typed, word)) return true
            val substitution = singleSubstitution(typed, word) ?: return false
            return typed[0] != word[0] && isNeighborKey(substitution.first, substitution.second)
        }

        /**
         * Fixes Shift held too long ("THe" -> "The"). Two capitals before a single letter ("IDs")
         * are left alone unless the result is a very common word.
         */
        internal fun shiftHeldTooLongFix(word: String, frequencyOf: (String) -> Int): String? {
            if (word.length < 3 || !word.all { it.isLetter() }) return null
            if (!word[0].isUpperCase() || !word[1].isUpperCase()) return null
            val rest = word.substring(2)
            if (!rest.all { it.isLowerCase() }) return null
            val fixed = word[0] + word.substring(1).lowercase()
            val minimumFrequency = if (rest.length >= 2) 1 else FIRST_LETTER_FIX_MIN_FREQUENCY
            return fixed.takeIf { frequencyOf(it) >= minimumFrequency }
        }

        /**
         * A known word plus a plural or verb "s" ("autocorrects", "keycaps"). Not after an "s" or a
         * consonant + "y" ("wordss", "storys"), which are slips.
         */
        internal fun isPluralOfKnownWord(
            word: String,
            isKnown: (String) -> Boolean,
            frequencyOf: (String) -> Int = { 0 }
        ): Boolean {
            if (word.length < PLURAL_MIN_LENGTH || !word.all { it in 'a'..'z' } || !word.endsWith('s')) return false
            val base = word.dropLast(1)
            if (base.endsWith('s')) return false
            if (base.endsWith('y') && base.getOrNull(base.length - 2)?.let { it !in "aeiou" } == true) return false
            return isKnown(base) && frequencyOf(base) < PLURAL_BASE_MAX_FREQUENCY
        }

        /** A lowercase letter followed by a capital marks a name ("iPhone"). */
        internal fun isCamelCaseName(word: String): Boolean =
            word.zipWithNext().any { (a, b) -> a.isLowerCase() && b.isUpperCase() }

        /** A word capitalised mid-sentence is treated as a name. */
        internal fun isCapitalizedName(word: String, textBeforeWord: String): Boolean {
            if (word.firstOrNull()?.isUpperCase() != true) return false
            val previous = textBeforeWord.trimEnd(' ').lastOrNull() ?: return false
            return previous != '\n' && previous !in ".!?"
        }

        /**
         * Lowercase word before the one just typed, for word-pair lookups. Returns
         * [PairCounts.SENTENCE_START] at a sentence start and null after punctuation or symbols.
         */
        internal fun previousWordFor(textBeforeWord: String): String? {
            val trimmed = textBeforeWord.trimEnd(' ')
            // After a comma or colon a new clause starts, much like a sentence ("Ok, ill call").
            if (trimmed.isEmpty() || trimmed.last() == '\n' || trimmed.last() in ".!?,;:") return PairCounts.SENTENCE_START
            if (trimmed.length == textBeforeWord.length) return null // Glued to something.
            val token = normalizeApostrophes(trimmed.takeLastWhile { it.isLetter() || it == '\'' || it == '’' })
            if (token.isEmpty() || token.length == trimmed.length && textBeforeWord.length >= PREVIOUS_WORD_LOOKBACK) {
                return null // Not a word, or possibly cut off.
            }
            val beforeToken = trimmed.getOrNull(trimmed.length - token.length - 1)
            return token.lowercase().takeIf { beforeToken == null || beforeToken.isWhitespace() }
        }

        /** A capitalised word at the start of a sentence, as opposed to a name mid-sentence. */
        private fun isSentenceStartCapital(word: String, textBeforeWord: String): Boolean =
            word.first().isUpperCase() && word.drop(1).all { it.isLowerCase() } && !isCapitalizedName(word, textBeforeWord)

        internal fun isAcronymLike(candidate: String): Boolean {
            val letters = candidate.filter { it.isLetter() }
            return letters.length >= 2 && letters.all { it.isUpperCase() }
        }

        internal fun hasSingleRepeatedCharInsertion(input: String, candidate: String): Boolean {
            if (candidate.length != input.length + 1) return false
            var i = 0
            var j = 0
            var insertedIndex = -1
            while (i < input.length && j < candidate.length) {
                if (input[i].equals(candidate[j], ignoreCase = true)) {
                    i++
                    j++
                } else {
                    if (insertedIndex != -1) return false
                    insertedIndex = j
                    j++
                }
            }
            if (insertedIndex == -1) insertedIndex = candidate.lastIndex
            val inserted = candidate.getOrNull(insertedIndex) ?: return false
            val prev = candidate.getOrNull(insertedIndex - 1)
            val next = candidate.getOrNull(insertedIndex + 1)
            return prev?.equals(inserted, ignoreCase = true) == true ||
                next?.equals(inserted, ignoreCase = true) == true
        }

        private fun changesFirstLetter(input: String, candidate: String): Boolean {
            val inputIndex = input.indexOfFirst { it.isLetter() }
            val candidateIndex = candidate.indexOfFirst { it.isLetter() }
            if (inputIndex < 0 || candidateIndex < 0) return false
            return !input[inputIndex].equals(candidate[candidateIndex], ignoreCase = true)
        }

        /**
         * Aggressive mode ignores the distance slider and allows one edit, or two from 6 letters.
         * 0 still means off.
         */
        internal fun effectiveMaxDistance(input: String, settings: SuggestionSettings): Int {
            val configured = settings.maxAutoReplaceDistance
            if (!settings.aggressiveAutoReplace || configured == 0) return configured
            return if (input.length >= 6) 2 else 1
        }

        /** One word is the other plus an ending ("walk", "walks"). */
        private fun onlyChangesWordEnding(input: String, candidate: String): Boolean {
            val shorter = if (input.length < candidate.length) input else candidate
            val longer = if (input.length < candidate.length) candidate else input
            return longer.startsWith(shorter, ignoreCase = true)
        }

        /** "helpp" -> "help": the extra trailing letter repeats the one before it. */
        private fun isRepeatedLetterDeletion(input: String, candidate: String): Boolean {
            if (input.length != candidate.length + 1 || candidate.isEmpty()) return false
            return input.startsWith(candidate, ignoreCase = true) &&
                input.last().equals(candidate.last(), ignoreCase = true)
        }

        internal fun isSafeAutoReplaceCandidate(
            input: String,
            lookupWord: String,
            candidate: SuggestionResult?,
            settings: SuggestionSettings,
            isOrthographicVariant: Boolean,
            isNeighborKey: (Char, Char) -> Boolean = { _, _ -> false },
            frequencyOf: (String) -> Int = { 0 }
        ): Boolean {
            if (candidate == null) return false
            val isCaseVariant = isCaseOnlyVariant(input, candidate.candidate)
            if (candidate.kind != SuggestionKind.CURRENT_WORD) return false
            if (!isOrthographicVariant && !isCaseVariant && candidate.distance <= 0) return false
            if (candidate.distance > effectiveMaxDistance(lookupWord, settings)) return false
            if (input.all { it.isLowerCase() } && isAcronymLike(candidate.candidate)) return false
            // Aggressive mode accepts capitalized words for lowercase input ("frday" -> "Friday").
            if (!isCaseVariant && !settings.aggressiveAutoReplace &&
                input.firstOrNull()?.isLowerCase() == true &&
                candidate.candidate.firstOrNull()?.isUpperCase() == true
            ) {
                return false
            }
            val lengthDelta = candidate.candidate.length - input.length
            if (lengthDelta == 0) {
                // A far-key swap in a short word is rarely a slip, and short words have many such
                // neighbours ("pref" is not "prey").
                if (settings.aggressiveAutoReplace && !isOrthographicVariant && !isCaseVariant &&
                    input.length <= FAR_KEY_SWAP_MAX_LENGTH
                ) {
                    val substitution = singleSubstitution(input.lowercase(), candidate.candidate.lowercase())
                    if (substitution != null && !isNeighborKey(substitution.first, substitution.second)) return false
                }
                return isOrthographicVariant || isCaseVariant || !changesFirstLetter(input, candidate.candidate) ||
                    (settings.aggressiveAutoReplace && isPlausibleFirstLetterTypo(input, candidate.candidate, isNeighborKey) &&
                        frequencyOf(candidate.candidate) >= FIRST_LETTER_FIX_MIN_FREQUENCY)
            }
            if (lengthDelta == 1 && hasSingleRepeatedCharInsertion(input, candidate.candidate)) return true
            // Aggressive mode: one missing or extra letter inside the word is a typo, but a change
            // only at the end ("emails" -> "email") is morphology.
            if (settings.aggressiveAutoReplace && candidate.distance == 1 && lengthDelta == 1) {
                // The input is not a word, so a missed last letter is a slip ("mothe" -> "mother").
                if (candidate.candidate.startsWith(input, ignoreCase = true) && input.length >= 3) return true
                if (candidate.candidate.substring(1).equals(input, ignoreCase = true)) {
                    return input.length >= MISSED_FIRST_LETTER_MIN_LENGTH &&
                        frequencyOf(candidate.candidate) >= MISSED_FIRST_LETTER_MIN_FREQUENCY
                }
            }
            if (settings.aggressiveAutoReplace &&
                candidate.distance == 1 &&
                kotlin.math.abs(lengthDelta) == 1 &&
                !changesFirstLetter(input, candidate.candidate) &&
                (!onlyChangesWordEnding(input, candidate.candidate) || isRepeatedLetterDeletion(input, candidate.candidate) ||
                    isTrailingFatFinger(input, candidate.candidate, isNeighborKey))
            ) {
                return true
            }

            // Pure suffix/prefix growth or shortening is usually morphology/completion, not a typo.
            return isOrthographicVariant && lookupWord.length == candidate.candidate.length
        }

        internal fun recomposeApostropheCandidate(
            split: ApostropheSplit,
            candidate: String
        ): String? {
            val prefix = split.prefix
            val normalizedCandidate = normalizeApostrophes(candidate)
            val hasApostrophe = normalizedCandidate.contains('\'')
            val matchesPrefix = normalizedCandidate.length >= prefix.length &&
                    normalizedCandidate.substring(0, prefix.length).equals(prefix, ignoreCase = true)

            val rootPart = when {
                matchesPrefix -> candidate.substring(prefix.length)
                hasApostrophe -> return null // don't mix different apostrophe prefixes
                else -> candidate
            }
            val recasedRoot = CasingHelper.applyCasing(rootPart, split.root, forceLeadingCapital = false)
            return prefix + recasedRoot
        }
    }
    
    // Track last replacement for undo
    private data class LastReplacement(
        val originalWord: String,
        val replacedWord: String,
        /** Text before the cursor right after the correction; undo requires it unchanged. */
        val contextBefore: String? = null
    )
    private var lastReplacement: LastReplacement? = null
    private var lastUndoOriginalWord: String? = null
    /** Word restored by undo. It counts as kept only if a boundary is typed right after it. */
    private var pendingLearnWord: String? = null
    
    // Track rejected words to avoid auto-correcting them again
    private val rejectedWords = mutableSetOf<String>()

    private fun hasTrailingHardBoundary(textBeforeCursor: String): Boolean {
        var i = textBeforeCursor.length - 1
        while (i >= 0) {
            val normalized = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(textBeforeCursor[i])
            if (normalized.isWhitespace() || normalized in it.palsoftware.pastiera.core.Punctuation.BOUNDARY) {
                i--
                continue
            }
            if (normalized.isLetterOrDigit() || normalized == '\'') {
                return false
            }
            return true
        }
        return false
    }

    fun handleBoundary(
        keyCode: Int,
        event: KeyEvent?,
        tracker: CurrentWordTracker,
        inputConnection: InputConnection?,
        boundaryCharOverride: Char? = null
    ): ReplaceResult {
        fun ensureTrailingSpace(connection: InputConnection): Boolean {
            val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.endsWith(" ")) {
                return false
            }
            val committed = connection.commitText(" ", 1)
            // An editor that cannot report its text back must not get a second space.
            val afterCommit = connection.getTextBeforeCursor(2, 0)?.toString() ?: return committed
            if (afterCommit.endsWith(" ")) {
                return true
            }
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            val afterKey = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            return afterKey.endsWith(" ")
        }

        fun commitBoundary(connection: InputConnection, boundary: Char, settings: SuggestionSettings): Boolean {
            if (
                settings.commaSpace &&
                Punctuation.commitCommaSpace(connection, boundary)
            ) {
                return true
            }
            return when (boundary) {
                ' ' -> ensureTrailingSpace(connection).also { committed ->
                    if (committed) AutoSpaceTracker.markAutoSpace()
                }
                else -> {
                    connection.commitText(boundary.toString(), 1)
                    true
                }
            }
        }

        fun commitBoundaryAndReset(
            tracker: CurrentWordTracker,
            connection: InputConnection,
            boundary: Char?,
            settings: SuggestionSettings
        ): Boolean {
            if (boundary != null) {
                val committed = commitBoundary(connection, boundary, settings)
                tracker.reset()
                return committed
            }
            tracker.onBoundaryReached(null, connection)
            return false
        }

        val unicodeChar = event?.unicodeChar ?: 0
        val boundaryChar = boundaryCharOverride ?: when {
            unicodeChar != 0 -> unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            else -> punctuationBoundaryForKeyCode(keyCode)
        }
        val boundaryCommittedByTracker = boundaryChar != null && inputConnection != null

        // Aggressive mode is tuned for English only, and a distance of 0 turns it off too.
        val settings = settingsProvider().let {
            if (it.aggressiveAutoReplace && (!isEnglish || it.maxAutoReplaceDistance == 0)) it.copy(aggressiveAutoReplace = false) else it
        }
        val trigger = triggerFromBoundaryChar(boundaryChar)
        if (inputConnection == null) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = tracker.currentWord,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.NOT_APPLICABLE,
                reason = "no_input_connection"
            )
            return ReplaceResult(false, boundaryCommittedByTracker)
        }

        // If there's a non-word symbol between the last word and cursor (e.g., emoji), skip.
        val textBefore = inputConnection.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        if (hasTrailingHardBoundary(textBefore)) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = tracker.currentWord,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "hard_boundary_before_cursor"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        val word = tracker.currentWord
        // An undone word kept once stays uncorrected for the session (rejectedWords). Kept again
        // on a later occasion, it is saved as a real word.
        val restored = pendingLearnWord
        if (settings.aggressiveAutoReplace && restored != null) {
            if (restored == word) {
                val before = inputConnection.getTextBeforeCursor(word.length + SPLIT_LOOKBACK, 0)
                    ?.toString().orEmpty().dropLast(word.length)
                keepRestoredWord(word, before)
            } else {
                // Undone and then deleted: the user was just editing, so keep correcting the word.
                rejectedWords.remove(restored.lowercase())
            }
        }
        pendingLearnWord = null
        if (word.isBlank()) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.NOT_APPLICABLE,
                reason = "empty_word"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        if (isInsideAddress(inputConnection.getTextBeforeCursor(ADDRESS_LOOKBACK, 0)?.toString().orEmpty(), boundaryChar)) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "email_or_link"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        // Replacements delete word.length characters before the cursor, so they only run when
        // those characters are the tracked word. The tracker can hold a stale word.
        val typedWord = inputConnection.getTextBeforeCursor(word.length, 0)?.toString()
        if (typedWord == null || normalizeApostrophes(typedWord) != normalizeApostrophes(word)) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "tracker_out_of_sync"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        // Replaces the word with a fix found outside the suggestion engine; Backspace undoes it.
        fun commitDirectReplacement(replacement: String, source: String): ReplaceResult {
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            inputConnection.commitText(replacement, 1)
            lastReplacement = LastReplacement(originalWord = word, replacedWord = replacement)
            tracker.reset()
            inputConnection.endBatchEdit()
            val boundaryCommitted = boundaryChar != null && commitBoundary(inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = word,
                after = replacement,
                trigger = trigger,
                source = source,
                distance = 1,
                kind = SuggestionKind.CURRENT_WORD.name
            )
            return ReplaceResult(true, boundaryCommitted, replacement)
        }

        if (settings.aggressiveAutoReplace && isCamelCaseName(word)) {
            // The one camel-case slip worth fixing: a missed space before a name ("andMary").
            if (isEnglish && !rejectedWords.contains(word.lowercase()) &&
                !(knownWordProvider?.invoke(word) ?: repository.isKnownWord(word))
            ) {
                nameSplit(word)?.let { split -> return commitDirectReplacement(split, "NAME_SPLIT") }
            }
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "camel_case_name"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        // A word spelled exactly this way in a dictionary ("VMware") is not a slip.
        if (settings.aggressiveAutoReplace && !rejectedWords.contains(word.lowercase()) &&
            !primaryDictionaryHasExactCase(word)
        ) {
            shiftHeldTooLongFix(word, repository::getExactWordFrequency)?.let { fixed ->
                return commitDirectReplacement(fixed, "SHIFT_HELD")
            }
        }

        val apostropheSplit = splitApostropheWord(word)
        val lookupWord = apostropheSplit?.root ?: word
        val wordLower = word.lowercase()

        val exactReplacement = if (settings.textReplacementsEnabled) {
            exactReplacementProvider?.invoke(word, boundaryChar)
        } else {
            null
        }
        exactReplacement?.takeUnless { replacement ->
            // Known all-caps words ("ID") are not expanded, and a real word ("ill") is only
            // rewritten when the context says the contraction is meant.
            isAcronymLike(word) && (knownWordProvider?.invoke(word) ?: repository.isKnownWord(word)) &&
                !repository.isUserOrSlangWord(word) ||
                replacement.lowercase() != wordLower &&
                (knownWordProvider?.invoke(word) ?: repository.isKnownWord(word)) &&
                !realWordReplacementAllowed(
                    previousWordFor(inputConnection.getTextBeforeCursor(word.length + PREVIOUS_WORD_LOOKBACK, 0)
                        ?.toString().orEmpty().dropLast(word.length)),
                    word,
                    replacement
                )
        }?.let { replacement ->
            if (!rejectedWords.contains(wordLower)) {
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(word.length, 0)
                val shouldAppendBoundary = boundaryChar != null &&
                    !(boundaryChar == ' ' && replacement.endsWith("'"))
                inputConnection.commitText(replacement, 1)
                if (isLearningAllowed()) repository.markUsed(replacement)
                lastReplacement = LastReplacement(
                    originalWord = word,
                    replacedWord = replacement
                )
                tracker.reset()
                inputConnection.endBatchEdit()
                var boundaryCommitted = false
                if (shouldAppendBoundary) {
                    boundaryCommitted = commitBoundary(inputConnection, boundaryChar, settings)
                }
                DebugCaptureStore.recordAutoCorrectionCommit(
                    before = word,
                    after = replacement,
                    trigger = trigger,
                    source = "TEXT_REPLACEMENT",
                    distance = 0,
                    kind = SuggestionKind.CURRENT_WORD.name
                )
                Log.d("AutoReplaceController", "Committed exact replacement '$word' -> '$replacement'")
                return ReplaceResult(true, true, replacement)
            }
        }

        if (!settings.autoReplaceOnSpaceEnter) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.NOT_APPLICABLE,
                reason = "auto_replace_disabled"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        primaryDictionaryCaseVariant(lookupWord, word)?.takeUnless { variant ->
            // In aggressive mode, texting slang ("btw") and lowercase words that are also names
            // stay lowercase.
            settings.aggressiveAutoReplace && (isAcronymLike(variant) || repository.isRecognizedWord(word))
        }?.let { caseReplacement ->
            if (!rejectedWords.contains(wordLower)) {
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(word.length, 0)
                val shouldAppendBoundary = boundaryChar != null &&
                    !(boundaryChar == ' ' && caseReplacement.endsWith("'"))
                inputConnection.commitText(caseReplacement, 1)
                if (isLearningAllowed()) repository.markUsed(caseReplacement)
                lastReplacement = LastReplacement(
                    originalWord = word,
                    replacedWord = caseReplacement
                )
                tracker.reset()
                inputConnection.endBatchEdit()
                var boundaryCommitted = false
                if (shouldAppendBoundary) {
                    boundaryCommitted = commitBoundary(inputConnection, boundaryChar, settings)
                }
                DebugCaptureStore.recordAutoCorrectionCommit(
                    before = word,
                    after = caseReplacement,
                    trigger = trigger,
                    source = "PRIMARY_CASE",
                    distance = 0,
                    kind = SuggestionKind.CURRENT_WORD.name
                )
                Log.d("AutoReplaceController", "Committed primary dictionary case replacement '$word' -> '$caseReplacement'")
                return ReplaceResult(true, true, caseReplacement)
            }
        }

        val isKnown: (String) -> Boolean = { candidate ->
            knownWordProvider?.invoke(candidate) ?: repository.isKnownWord(candidate)
        }
        val textBeforeWord = inputConnection.getTextBeforeCursor(word.length + SPLIT_LOOKBACK, 0)
            ?.toString().orEmpty().dropLast(word.length)
        // The words before decide between equally likely slips ("some kilk" -> "milk", not "kill").
        val textBeforeForContext by lazy {
            inputConnection.getTextBeforeCursor(word.length + PREVIOUS_WORD_LOOKBACK, 0)?.toString().orEmpty().dropLast(word.length)
        }
        val previousWord by lazy { previousWordFor(textBeforeForContext) }
        val wordBeforePrevious by lazy {
            val previous = previousWord
            if (previous == null || previous == PairCounts.SENTENCE_START) {
                null
            } else {
                val trimmed = textBeforeForContext.trimEnd(' ')
                previousWordFor(trimmed.dropLast(trimmed.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }.length))
            }
        }
        // A slip on a word written twice in this conversation, such as a friend's name.
        val recentSlipFix: () -> String? = {
            if (settings.aggressiveAutoReplace && isEnglish && !rejectedWords.contains(wordLower) && word.all { it.isLetter() }) {
                recentWords.slipTarget(word) { typed, recent ->
                    // Names and words the dictionary lacks; common words already have their own fixes.
                    repository.getExactWordFrequency(recent) < RECENT_SLIP_MAX_COMMON_FREQUENCY &&
                        kotlin.math.abs(typed.length - recent.length) <= 1 &&
                        typoLikelihood(typed, recent, suggestionEngine::isNeighborKey) >= RECENT_SLIP_MIN_LIKELIHOOD
                }?.takeIf { !(knownWordProvider?.invoke(word) ?: repository.isKnownWord(word)) }
            } else {
                null
            }
        }
        val contextFit: (String) -> Double = { candidate ->
            val lower = candidate.lowercase()
            val pairs = previousWord?.let { pairCount(it, lower) } ?: 0
            val triples = previousWord?.let { previous -> wordBeforePrevious?.let { tripleCount(it, previous, lower) } } ?: 0
            saturating(pairs, CONTEXT_SATURATION_COUNT) * CONTEXT_WEIGHT + saturating(triples, TRIPLE_SATURATION_COUNT) * TRIPLE_WEIGHT
        }
        // A space typed for the apostrophe: "I ll" -> "I'll", "don t" -> "don't".
        if (settings.aggressiveAutoReplace && isEnglish && !rejectedWords.contains(wordLower)) {
            val previous = textBeforeForContext.removeSuffix(" ").takeLastWhile { it.isLetter() }
            val beforePrevious = textBeforeForContext.removeSuffix(" ").dropLast(previous.length).lastOrNull()
            if (textBeforeForContext.endsWith(" ") && previous.isNotEmpty() && (beforePrevious == null || !beforePrevious.isLetterOrDigit()) &&
                "${previous.lowercase()}'$wordLower" in SPACED_CONTRACTIONS
            ) {
                val typed = "$previous $word"
                val joined = "$previous'$word"
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(typed.length, 0)
                inputConnection.commitText(joined, 1)
                lastReplacement = LastReplacement(originalWord = typed, replacedWord = joined)
                tracker.reset()
                inputConnection.endBatchEdit()
                val boundaryCommitted = boundaryChar != null && commitBoundary(inputConnection, boundaryChar, settings)
                DebugCaptureStore.recordAutoCorrectionCommit(
                    before = typed, after = joined, trigger = trigger, source = "SPACED_CONTRACTION",
                    distance = 1, kind = SuggestionKind.CURRENT_WORD.name
                )
                return ReplaceResult(true, boundaryCommitted, joined)
            }
        }
        if (settings.aggressiveAutoReplace && isEnglish && !isKnown(word) && !repository.isFirstName(word) &&
            !rejectedWords.contains(wordLower) && word.all { it.isLetter() }
        ) {
            nameSplit(word)?.let { split -> return commitDirectReplacement(split, "NAME_SPLIT") }
        }
        val keepReason = when {
            settings.aggressiveAutoReplace && word.any { it.isDigit() } -> "contains_digit" // "3pm", "5km"
            settings.aggressiveAutoReplace && word.length >= 2 && word.all { it.isUpperCase() } -> "acronym" // "TXU", "LV"
            settings.aggressiveAutoReplace && isStretchedForEmphasis(word, isKnown) -> "emphasis" // "sooo", "pleaseee"
            settings.aggressiveAutoReplace && isPluralOfKnownWord(word, isKnown, repository::getExactWordFrequency) -> "plural_of_known_word" // "autocorrects"
            isEnglish && apostropheSplit == null &&
                isWordWithClitic(word, isKnown, rootMayBeName = isCapitalizedName(word, textBeforeWord)) -> "word_with_clitic"
            // A capitalized first name ("Ali") is meant, even where it could be a slip ("All").
            settings.aggressiveAutoReplace && word.first().isUpperCase() && word.drop(1).all { it.isLowerCase() } &&
                repository.isFirstName(word) && !isKnown(word) -> "first_name"
            settings.aggressiveAutoReplace && isCapitalizedName(word, textBeforeWord) -> "capitalized_name"
            settings.aggressiveAutoReplace && word.all { it.isLowerCase() } && repository.isRecognizedWord(word) -> "recognized_word"
            else -> null
        }
        if (keepReason == "capitalized_name") {
            recentSlipFix()?.let { fixed -> return commitDirectReplacement(fixed, "RECENT_WORD") }
            properNounSlipFix(word, settings)?.let { fixed -> return commitDirectReplacement(fixed, "PROPER_NOUN") }
        }
        if (keepReason != null) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = keepReason
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        // A well-known misspelling ("recieve") has one known fix; never applied to a real word.
        if (settings.aggressiveAutoReplace && isEnglish && !isKnown(word) && !rejectedWords.contains(wordLower)) {
            repository.commonMisspellingFix(word)?.let { fixed ->
                return commitDirectReplacement(applyCasing(fixed, word), "COMMON_MISSPELLING")
            }
        }
        if (settings.aggressiveAutoReplace && isEnglish && !isKnown(word) && !rejectedWords.contains(word.lowercase()) &&
            (word.all { it.isLowerCase() } || isSentenceStartCapital(word, textBeforeWord))
        ) {
            twoLetterFix(word.lowercase(), repository::getExactWordFrequency, suggestionEngine::isNeighborKey, contextFit)?.let { fixed ->
                return commitDirectReplacement(applyCasing(fixed, word), "TWO_LETTER")
            }
        }

        // A made-up word: the word most likely meant here, if it clearly beats the alternatives.
        var channelUnsure = false
        if (noisyChannel != null && settings.aggressiveAutoReplace && isEnglish && apostropheSplit == null &&
            !isKnown(word) && !rejectedWords.contains(wordLower) && word.length >= 2 && word.all { it.isLetter() } &&
            (word.all { it.isLowerCase() } || isSentenceStartCapital(word, textBeforeWord))
        ) {
            val pick = noisyChannel.pick(wordLower, previousWord, wordBeforePrevious, next = null)
            pick?.let(noisyChannel::confidentFix)
                // A capitalised first word may be a name ("Mennad"): only a likely slip to a common word.
                ?.takeIf { fixed -> word.first().isLowerCase() || isPlausibleForCasing(word, fixed) }
                ?.takeUnless { fixed -> isLikelyCompound(wordLower, fixed) }
                ?.let { fixed -> return commitDirectReplacement(applyCasing(fixed, word), "NOISY_CHANNEL") }
            if (NOISY_CHANNEL_DECIDES && pick != null) channelUnsure = true
        }

        val suggestions = suggestionEngine.suggest(
            lookupWord,
            limit = if (settings.aggressiveAutoReplace) AGGRESSIVE_CANDIDATE_LIMIT else 1,
            includeAccentMatching = settings.accentMatching,
            useKeyboardProximity = settings.useKeyboardProximity ||
                (settings.aggressiveAutoReplace && lookupWord.length <= PROXIMITY_FILTER_MAX_LENGTH),
            useEditTypeRanking = settings.useEditTypeRanking || settings.aggressiveAutoReplace
        )
        val recomposedCandidates = suggestions.map {
            if (apostropheSplit != null) {
                recomposeApostropheCandidate(apostropheSplit, it.candidate)?.let { recomposed ->
                    it.copy(candidate = recomposed)
                }
            } else {
                it
            }
        }
        // Aggressive mode picks the most likely typo fix among safe candidates. The engine's top
        // hit favours frequent words and completions, which suit the suggestion bar.
        val top = if (settings.aggressiveAutoReplace) {
            recomposedCandidates.filterNotNull()
                // The engine reports prefix completions as distance 0, so use the real edit distance.
                .map { candidate -> candidate.copy(distance = editDistance(word, candidate.candidate)) }
                .filter { candidate ->
                    isSafeAutoReplaceCandidate(
                        input = word,
                        lookupWord = lookupWord,
                        candidate = candidate,
                        settings = settings,
                        isOrthographicVariant = isAccentOnlyVariant(word, candidate.candidate),
                        isNeighborKey = suggestionEngine::isNeighborKey,
                        frequencyOf = repository::getExactWordFrequency
                    ) && isPlausibleForCasing(word, candidate.candidate)
                }
                .maxByOrNull { candidate ->
                    typoLikelihood(word, candidate.candidate, suggestionEngine::isNeighborKey) +
                        FREQUENCY_WEIGHT * repository.getExactWordFrequency(candidate.candidate) / MAX_FREQUENCY +
                        contextFit(candidate.candidate) +
                        if (recentWords.count(candidate.candidate) > 0) RECENT_WORD_BONUS else 0.0
                }
        } else {
            recomposedCandidates.firstOrNull()
        }
        
        // Safety checks for auto-replace
        val isOrthographicVariant = top != null && isAccentOnlyVariant(word, top.candidate)
        val isCaseVariant = top != null && isCaseOnlyVariant(word, top.candidate)
        val minWordLength = if (isOrthographicVariant) 2 else 3 // Allow short orthographic fixes (e.g., "ja" -> "já")
        val maxLengthRatio = 1.25 // Keep as a fallback guard for longer typo candidates.
        
        // Check if word has been rejected by user
        val isRejected = rejectedWords.contains(wordLower)
        
        // Check if word exists in dictionary
        val isKnownWord = knownWordProvider?.invoke(lookupWord) ?: repository.isKnownWord(lookupWord)
        val isExactKnownWord = repository.getExactWordFrequency(lookupWord) > 0
        val hasExactPrimaryCase = primaryDictionaryHasExactCase(lookupWord)

        // Only auto-replace if word is NOT known (i.e., it's a typo/unknown word)
        // Don't replace valid words with other valid words, even if they have higher frequency
        val isSafeCandidate = isSafeAutoReplaceCandidate(
            input = word,
            lookupWord = lookupWord,
            candidate = top,
            settings = settings,
            isOrthographicVariant = isOrthographicVariant,
            isNeighborKey = suggestionEngine::isNeighborKey,
            frequencyOf = repository::getExactWordFrequency
        )

        val shouldReplace = top != null && !channelUnsure
            && (!isKnownWord || (isCaseVariant && !hasExactPrimaryCase) || (isOrthographicVariant && !isExactKnownWord)) // Allow case/orthographic fixes
            && !isRejected // Don't auto-correct if user has rejected this word
            && isSafeCandidate
            && lookupWord.length >= minWordLength // Minimum word length check on root
            && (settings.aggressiveAutoReplace || // the safety check already limits length changes to one letter
                top.candidate.length <= (word.length * maxLengthRatio).toInt() ||
                hasSingleRepeatedCharInsertion(word, top.candidate)) // Max length ratio check on full text

        if (!shouldReplace && !isKnownWord && !isRejected) {
            recentSlipFix()?.let { fixed -> return commitDirectReplacement(applyCasing(fixed, word), "RECENT_WORD") }
        }

        // Aggressive mode splits a missed space ("hellohow" -> "hello how") when the one-word
        // fix is weak.
        val split = if (settings.aggressiveAutoReplace && isEnglish && !isKnownWord && !isRejected && apostropheSplit == null &&
            !isCapitalizedName(word, textBeforeWord)
        ) {
            findWordSplit(word, { part -> repository.getExactWordFrequency(part) }, isKnown) { left, right ->
                // A rare half must be seen next to the other ("con tuned" is no missed space), and a
                // capitalised word may be a name, so it always must ("Mansell" is not "Man sell").
                !pairsReady() || pairCount(left.lowercase(), right.lowercase()) > 0 ||
                    !word.first().isUpperCase() &&
                    minOf(repository.getExactWordFrequency(left.lowercase()), repository.getExactWordFrequency(right.lowercase())) >=
                    UNATTESTED_SPLIT_MIN_FREQUENCY
            }
        } else {
            null
        }
        val isWeakFix = !shouldReplace || top.distance >= 2 ||
            typoLikelihood(word, top.candidate, suggestionEngine::isNeighborKey) <= WEAK_FIX_LIKELIHOOD ||
            split != null && isMuchCommonerSplit(split, top.candidate)
        if (settings.aggressiveAutoReplace && isEnglish && !isKnownWord && !isRejected &&
            isLikelyCompound(wordLower, top?.candidate?.takeIf { shouldReplace })
        ) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "compound_word"
            )
            return ReplaceResult(false, boundaryCommitted)
        }
        if (split != null && isWeakFix) return commitDirectReplacement(split, "WORD_SPLIT")

        if (!shouldReplace && split == null && settings.aggressiveAutoReplace && isEnglish && !isKnownWord && !isRejected &&
            apostropheSplit == null && word.length >= DEEP_FIX_MIN_LENGTH && word.all { it.isLetter() } &&
            !isCapitalizedName(word, textBeforeWord)
        ) {
            deepFix(word, deepFixCandidates(word.first()), suggestionEngine::isNeighborKey)
                ?.takeIf { fixed -> isPlausibleForCasing(word, fixed) }
                ?.let { fixed -> return commitDirectReplacement(applyCasing(fixed, word), "DEEP_FIX") }
        }

	        if (shouldReplace) {
	            val replacement = applyCasing(top!!.candidate, word)
	            if (replacement == word) {
	                DebugCaptureStore.recordAutoCorrectionAttempt(
	                    before = word,
	                    trigger = trigger,
	                    source = top.source.name,
	                    after = top.candidate,
	                    outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
	                    reason = "same_replacement",
	                    distance = top.distance,
	                    kind = top.kind.name
	                )
	                val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
	                return ReplaceResult(false, boundaryCommitted)
	            }
	            val source = top.source.name
	            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            val shouldAppendBoundary = boundaryChar != null &&
                !(boundaryChar == ' ' && replacement.endsWith("'"))
            inputConnection.commitText(replacement, 1)
            if (isLearningAllowed()) repository.markUsed(replacement)
            
            // Store last replacement for undo
            lastReplacement = LastReplacement(
                originalWord = word,
                replacedWord = replacement
            )
            
            tracker.reset()
            inputConnection.endBatchEdit()
            var boundaryCommitted = false
            if (shouldAppendBoundary) {
                boundaryCommitted = commitBoundary(inputConnection, boundaryChar, settings)
            }
            val committedSuffix = if (boundaryCommitted && shouldAppendBoundary) boundaryChar.toString() else ""
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = word,
                after = replacement,
                trigger = trigger,
                source = source,
                distance = top.distance,
                kind = top.kind.name
            )
            Log.d("AutoReplaceController", "Committed text '${replacement + committedSuffix}', markAutoSpace=${boundaryCommitted && boundaryChar == ' '}")
            return ReplaceResult(true, true, replacement)
        }

        val skipReason = when {
            top == null -> "no_suggestion"
            isRejected -> "rejected_by_user"
            isKnownWord && !isCaseVariant && !(isOrthographicVariant && !isExactKnownWord) -> "known_word"
            top.distance > effectiveMaxDistance(lookupWord, settings) -> "distance_too_high"
            top.kind != SuggestionKind.CURRENT_WORD -> "not_current_word"
            !isOrthographicVariant && !isCaseVariant && top.distance <= 0 -> "not_edit_distance"
            word.all { it.isLowerCase() } && isAcronymLike(top.candidate) -> "acronym_candidate"
            !isSafeCandidate -> "unsafe_shape"
            lookupWord.length < minWordLength -> "word_too_short"
            !settings.aggressiveAutoReplace &&
                top.candidate.length > (word.length * maxLengthRatio).toInt() &&
                !hasSingleRepeatedCharInsertion(word, top.candidate) -> "candidate_too_long"
            else -> "constraints_not_met"
        }
        DebugCaptureStore.recordAutoCorrectionAttempt(
            before = word,
            trigger = trigger,
            source = top?.source?.name ?: "UNKNOWN",
            after = top?.candidate,
            outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
            reason = skipReason,
            distance = top?.distance,
            kind = top?.kind?.name
        )

        // Clear last replacement if no replacement happened
        lastReplacement = null
        val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
        return ReplaceResult(false, boundaryCommitted)
    }

    fun handleBackspaceUndo(
        keyCode: Int,
        inputConnection: InputConnection?
    ): Boolean {
        val settings = settingsProvider()
        if (!settings.autoReplaceOnSpaceEnter || keyCode != KeyEvent.KEYCODE_DEL || inputConnection == null) {
            return false
        }

        val replacement = lastReplacement ?: return false
        // The cursor must still be where the correction left it.
        replacement.contextBefore?.let { expected ->
            if (inputConnection.getTextBeforeCursor(expected.length, 0)?.toString() != expected) return false
        }
        
        // The fixed word, then what ended it: spaces ("back plate "), or one punctuation mark the
        // user typed and maybe a space ("back plate! ", "autocorrect, ").
        val textBeforeCursor = inputConnection.getTextBeforeCursor(
            replacement.replacedWord.length + UNDO_TRAILING_LOOKBACK,
            0
        )?.toString() ?: return false
        val spaces = textBeforeCursor.length - textBeforeCursor.trimEnd().length
        val withoutSpaces = textBeforeCursor.dropLast(spaces)
        // Spaces alone first: a replacement can end in its own punctuation ("On my way!").
        val keptPunctuation = when {
            withoutSpaces.endsWith(replacement.replacedWord) -> ""
            withoutSpaces.lastOrNull()?.let { it in UNDO_BOUNDARY_PUNCTUATION } == true &&
                withoutSpaces.dropLast(1).endsWith(replacement.replacedWord) -> withoutSpaces.takeLast(1)
            else -> return false
        }
        val charsToDelete = replacement.replacedWord.length + keptPunctuation.length + spaces

        // Only undo the replaced word itself, not a longer word ending with it.
        if (textBeforeCursor.getOrNull(textBeforeCursor.length - charsToDelete - 1)?.isLetterOrDigit() == true) {
            return false
        }

        inputConnection.beginBatchEdit()
        inputConnection.deleteSurroundingText(charsToDelete, 0)
        // The typed word comes back; punctuation the user typed after it stays.
        inputConnection.commitText(replacement.originalWord + keptPunctuation, 1)
        inputConnection.endBatchEdit()
        
        DebugCaptureStore.recordAutoCorrectionAttempt(
            before = replacement.replacedWord,
            after = replacement.originalWord,
            trigger = DebugCaptureStore.AutoCorrectionTrigger.OTHER,
            source = "UNDO",
            outcome = DebugCaptureStore.AutoCorrectionOutcome.APPLIED,
            reason = "backspace_undo"
        )
        // Mark word as rejected so it won't be auto-corrected again
        rejectedWords.add(replacement.originalWord.lowercase())
        splitApostropheWord(replacement.originalWord)?.root?.lowercase()?.let { rejectedWords.add(it) }
        lastUndoOriginalWord = replacement.originalWord
        // Aggressive mode learns an undone word only once the user keeps it (see handleBoundary).
        val learnable = settingsProvider().aggressiveAutoReplace &&
            replacement.originalWord.all { it.isLetter() || it == '\'' }
        if (keptPunctuation.isEmpty()) {
            pendingLearnWord = replacement.originalWord.takeIf { learnable }
        } else if (learnable) {
            // Punctuation already ends the word ("backplate!"): it is kept as it stands.
            val before = inputConnection.getTextBeforeCursor(
                replacement.originalWord.length + keptPunctuation.length + SPLIT_LOOKBACK, 0
            )?.toString().orEmpty().dropLast(replacement.originalWord.length + keptPunctuation.length)
            keepRestoredWord(replacement.originalWord, before)
        }
        
        // Clear last replacement after undo
        lastReplacement = null
        return true
    }

    /**
     * The user kept a word they undid: noted the first time, learned the second (see
     * KeptOnceStore). [before] is the text before the word.
     */
    private fun keepRestoredWord(word: String, before: String) {
        if (!isLearningAllowed()) return // Incognito field: remember nothing.
        if (keptOnceStore.contains(word)) {
            keptOnceStore.remove(word)
            // A sentence-start capital is learned lowercase; a capital mid-sentence is a name.
            learnWord(if (isSentenceStartCapital(word, before)) word.lowercase() else word)
        } else {
            keptOnceStore.add(word)
        }
    }

    /** Records the cursor context of the last correction so undo only fires there. */
    fun sealLastReplacement(inputConnection: InputConnection?) {
        val current = lastReplacement ?: return
        lastReplacement = current.copy(
            contextBefore = inputConnection?.getTextBeforeCursor(UNDO_CONTEXT_LENGTH, 0)?.toString()
        )
    }

    /**
     * A correction made at "." is put back when the next character continues the same chunk
     * ("jonh.smith", "teh.txt"): the "." was inside an address or file name, not a sentence end.
     * Called after [typed] was committed.
     */
    fun revertDotCorrection(typed: String, inputConnection: InputConnection?): Boolean {
        val last = lastReplacement ?: return false
        val context = last.contextBefore ?: return false
        val next = typed.singleOrNull() ?: return false
        if (!(next.isLetterOrDigit() || next == '@' || next == '_' || next == '-')) return false
        if (!context.endsWith(last.replacedWord + ".")) return false
        val connection = inputConnection ?: return false
        val expected = context + next
        val before = connection.getTextBeforeCursor(expected.length, 0)?.toString() ?: return false
        if (before.length < last.replacedWord.length + 2 || !expected.endsWith(before)) return false
        connection.beginBatchEdit()
        connection.deleteSurroundingText(last.replacedWord.length + 2, 0)
        connection.commitText(last.originalWord + "." + next, 1)
        connection.endBatchEdit()
        lastReplacement = null
        return true
    }

    fun clearLastReplacement() {
        lastReplacement = null
    }

    /** Called on a new field, where a pending undo no longer counts as kept. */
    fun clearPendingLearning() {
        pendingLearnWord = null
    }
    
    fun clearRejectedWords() {
        rejectedWords.clear()
    }

    private fun primaryDictionaryCaseVariant(lookupWord: String, originalWord: String): String? {
        if (!repository.isReady || lookupWord != originalWord) return null
        if (originalWord.none { it.isLetter() } || originalWord.any { it.isUpperCase() }) return null

        val normalized = WordNormalization.normalizeForDictionary(originalWord, Locale.ROOT)
        val entries = repository.topByNormalized(normalized, limit = 8)
        if (entries.any { it.word == originalWord }) return null

        return entries
            .firstOrNull { entry ->
                entry.word != originalWord &&
                    entry.word.equals(originalWord, ignoreCase = true) &&
                    entry.word.any { it.isUpperCase() }
            }
            ?.word
    }

    /** Two-letter fixes, missed-space splits and clitics are English-only. */
    private val isEnglish: Boolean get() = suggestionEngine.language == "en"

    /**
     * A lowercase slip only becomes a name when the name is common or the slip classic. A
     * capitalised word may itself be a name, so it needs a likely slip to a common word.
     */
    /**
     * A missed space next to a name: "andMary" (a lowercase word, then a capital) or "Tomis" (a
     * name, then a common word). Brand-style words ("iPhone", "YouTube") and surnames never split.
     */
    private fun nameSplit(word: String): String? {
        if (word.length < MIN_SPLIT_WORD_LENGTH + 1) return null
        val capitals = (1 until word.length).filter { word[it].isUpperCase() }
        fun isNameLike(part: String) = part.length >= 2 && part.first().isUpperCase() && part.drop(1).all { it.isLowerCase() } &&
            (repository.isFirstName(part) || repository.getExactWordFrequency(part) >= PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY &&
                repository.getExactWordFrequency(part.lowercase()) < repository.getExactWordFrequency(part)) // "Boston", not "Variable"
        fun isCommon(part: String) = repository.getExactWordFrequency(part) >=
            if (part.length <= 2) MIN_SPLIT_SHORT_PART_FREQUENCY else NAME_SPLIT_MIN_WORD_FREQUENCY
        if (word.first().isLowerCase() && capitals.size == 1) {
            val left = word.substring(0, capitals[0])
            val right = word.substring(capitals[0])
            return "$left $right".takeIf { left.length >= 2 && isCommon(left) && isNameLike(right) }
        }
        if (word.first().isUpperCase() && capitals.isEmpty()) {
            return (2..word.length - 2)
                .map { word.substring(0, it) to word.substring(it) }
                .filter { (name, rest) -> isNameLike(name) && isCommon(rest) && rest !in SURNAME_ENDINGS }
                .maxByOrNull { (_, rest) -> repository.getExactWordFrequency(rest) }
                ?.let { (name, rest) -> "$name $rest" }
        }
        return null
    }

    /**
     * A capitalized word mid-sentence that is no known name may be a slip on a common proper noun
     * ("next Sudnay" -> "Sunday"). Only a classic slip onto a single such word counts.
     */
    private fun properNounSlipFix(word: String, settings: SuggestionSettings): String? {
        if (!settings.aggressiveAutoReplace || !isEnglish || word.length < PROPER_NOUN_SLIP_MIN_LENGTH ||
            !word.all { it.isLetter() } || word.drop(1).any { it.isUpperCase() } || rejectedWords.contains(word.lowercase()) ||
            (knownWordProvider?.invoke(word) ?: repository.isKnownWord(word))
        ) {
            return null
        }
        return suggestionEngine.suggest(word, limit = AGGRESSIVE_CANDIDATE_LIMIT, useKeyboardProximity = true, useEditTypeRanking = true)
            .map { it.candidate }
            .filter { candidate ->
                candidate.first().isUpperCase() && candidate.drop(1).all { it.isLowerCase() } &&
                    !candidate.equals(word, ignoreCase = true) &&
                    repository.getExactWordFrequency(candidate) >= PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY &&
                    typoLikelihood(word, candidate, suggestionEngine::isNeighborKey) >= CLASSIC_SLIP_LIKELIHOOD
            }
            .distinct()
            .singleOrNull()
    }

    private fun isPlausibleForCasing(word: String, candidate: String): Boolean {
        val frequency = repository.getExactWordFrequency(candidate)
        val candidateIsName = candidate.firstOrNull()?.isUpperCase() == true
        val likelihood = typoLikelihood(word, candidate, suggestionEngine::isNeighborKey)
        if (candidateIsName && frequency < PROPER_NOUN_FIX_MIN_FREQUENCY &&
            !(likelihood >= CLASSIC_SLIP_LIKELIHOOD && frequency >= PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY)
        ) {
            return false
        }
        val typedIsCapitalized = word.firstOrNull()?.isUpperCase() == true && word.drop(1).all { !it.isUpperCase() }
        if (!typedIsCapitalized) return true
        if (candidateIsName) return true
        // A long everyday word one letter off ("Yesterdar") is a slip, not a name.
        if (candidate.length >= SENTENCE_START_LONG_WORD_LENGTH && frequency >= SENTENCE_START_LONG_FIX_MIN_FREQUENCY &&
            (likelihood >= SENTENCE_START_FIX_MIN_LIKELIHOOD || editDistance(word.lowercase(), candidate.lowercase()) == 1)
        ) {
            return true
        }
        return frequency >= SENTENCE_START_FIX_MIN_FREQUENCY && likelihood >= SENTENCE_START_FIX_MIN_LIKELIHOOD
    }

    /**
     * Two common words written as one ("backplate", "groupchat") and not a known phrase: a
     * closed compound, meant as typed unless the fix is a common word itself ("everbody" ->
     * "everybody" still is fixed; "backplate" -> "backplane" is not).
     */
    private fun isLikelyCompound(word: String, fix: String?): Boolean {
        if (!pairsReady() || word.length < 2 * COMPOUND_MIN_HALF || !word.all { it in 'a'..'z' }) return false
        val fixFrequency = fix?.let { repository.getExactWordFrequency(it.lowercase()) } ?: 0
        return (COMPOUND_MIN_HALF..word.length - COMPOUND_MIN_HALF).any { cut ->
            val left = word.substring(0, cut)
            val right = word.substring(cut)
            val weaker = minOf(repository.getExactWordFrequency(left), repository.getExactWordFrequency(right))
            weaker >= COMPOUND_HALF_MIN_FREQUENCY && fixFrequency < COMPOUND_FIX_MIN_FREQUENCY && pairCount(left, right) == 0
        }
    }

    /** Common words by first letter for deepFix. */
    private val deepFixCandidateCache = HashMap<Char, List<DictionaryEntry>>()

    private fun deepFixCandidates(first: Char): List<DictionaryEntry> {
        val key = first.lowercaseChar()
        deepFixCandidateCache[key]?.let { return it }
        val candidates = repository.lookupByPrefixMerged(first.toString(), DEEP_FIX_CANDIDATE_LIMIT)
            .filter { entry -> entry.frequency >= DEEP_FIX_MIN_FREQUENCY && entry.word.all { it.isLetter() } }
        // Don't cache while the dictionary is still loading.
        if (repository.isReady && candidates.isNotEmpty()) deepFixCandidateCache[key] = candidates
        return candidates
    }

    /** Both halves of [split] are far more common than [candidate] ("was the" vs "waste"). */
    private fun isMuchCommonerSplit(split: String, candidate: String): Boolean {
        val weakerHalf = split.split(' ').minOf { part -> repository.getExactWordFrequency(part) }
        return weakerHalf >= COMMON_SPLIT_MIN_FREQUENCY &&
            repository.getExactWordFrequency(candidate) + COMMON_SPLIT_MIN_GAIN <= weakerHalf
    }

    private fun primaryDictionaryHasExactCase(word: String): Boolean {
        if (!repository.isReady || word.isBlank()) return false
        val normalized = WordNormalization.normalizeForDictionary(word, Locale.ROOT)
        return repository.topByNormalized(normalized, limit = 8).any { it.word == word }
    }

    private fun applyCasing(candidate: String, original: String): String {
        return CasingHelper.applyCasing(candidate, original, forceLeadingCapital = false)
    }

    fun consumeLastUndoOriginalWord(): String? {
        val word = lastUndoOriginalWord
        lastUndoOriginalWord = null
        return word
    }
}
