package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.Punctuation
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import it.palsoftware.pastiera.inputmethod.DebugCaptureStore

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
    private val isLearningAllowed: () -> Boolean = { true }
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
        private const val AGGRESSIVE_CANDIDATE_LIMIT = 8
        private const val UNDO_CONTEXT_LENGTH = 64
        // Aggressive ranking: typo likelihood dominates, word frequency (0..~220) breaks ties.
        private const val FREQUENCY_WEIGHT = 0.6
        private const val MAX_FREQUENCY = 220.0
        // Long words are rarely mistyped into other words, so far-key substitutions are allowed.
        private const val PROXIMITY_FILTER_MAX_LENGTH = 7
        // A first-letter slip may only become a very common word, so "lol" never becomes "mol".
        private const val FIRST_LETTER_FIX_MIN_FREQUENCY = 160
        private const val ADDRESS_LOOKBACK = 64
        private const val MIN_SPLIT_WORD_LENGTH = 4
        // deepFix tuning: fix badly mangled common words but leave rare unknown words alone.
        private const val DEEP_SLIP_COST = 0.5
        private const val DEEP_MISSED_COST = 0.7
        private const val DEEP_EXTRA_COST = 0.9
        private const val DEEP_FIX_MIN_LENGTH = 7
        private const val DEEP_FIX_MIN_FREQUENCY = 100
        private const val DEEP_FIX_MAX_COST = 1.25
        private const val DEEP_FIX_MIN_MARGIN = 0.3
        private const val DEEP_FIX_CANDIDATE_LIMIT = 6000
        private const val TWO_LETTER_FIX_MIN_FREQUENCY = 170
        private const val TWO_LETTER_FIX_MIN_MARGIN = 0.15
        // A missed first letter is only fixed in longer words ("ubject" -> "subject"), since
        // short words lose too much of their shape.
        private const val MISSED_FIRST_LETTER_MIN_LENGTH = 5
        private const val MISSED_FIRST_LETTER_MIN_FREQUENCY = 110
        // A capitalised word may be a name, so at a sentence start it is only fixed by a likely
        // slip to a common lowercase word.
        private const val SENTENCE_START_FIX_MIN_FREQUENCY = 120
        private const val SENTENCE_START_FIX_MIN_LIKELIHOOD = 0.7
        // A lowercase slip only becomes a name or place when that name is common.
        private const val PROPER_NOUN_FIX_MIN_FREQUENCY = 130
        private const val PROPER_NOUN_CLASSIC_SLIP_MIN_FREQUENCY = 100
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

        /**
         * Fixes a two-letter non-word ("si" -> "is") towards a very common word, only when that
         * fix clearly beats every other.
         */
        internal fun twoLetterFix(word: String, frequencyOf: (String) -> Int, isNeighborKey: (Char, Char) -> Boolean): String? {
            if (word.length != 2 || !word.all { it in 'a'..'z' }) return null
            val candidates = buildSet {
                add(word.reversed())
                for (i in 0..1) for (c in 'a'..'z') if (c != word[i] && isNeighborKey(word[i], c)) add(word.replaceRange(i, i + 1, c.toString()))
                for (i in 0..2) for (c in 'a'..'z') add(word.substring(0, i) + c + word.substring(i))
            }
            val ranked = candidates
                .filter { candidate -> frequencyOf(candidate) >= TWO_LETTER_FIX_MIN_FREQUENCY }
                .map { candidate ->
                    candidate to typoLikelihood(word, candidate, isNeighborKey) + FREQUENCY_WEIGHT * frequencyOf(candidate) / MAX_FREQUENCY
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
                        else -> 1.0
                    }
                    var cost = minOf(previous[j] + extra, current[j - 1] + DEEP_MISSED_COST, previous[j - 1] + substitution)
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
                        kotlin.math.abs(entry.word.length - word.length) <= 1 &&
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
        internal fun findWordSplit(word: String, frequencyOf: (String) -> Int): String? {
            if (word.length < MIN_SPLIT_WORD_LENGTH || !word.all { it.isLetter() }) return null
            var best: String? = null
            var bestScore = 0
            for (i in 2..word.length - 2) {
                val left = word.substring(0, i)
                val right = word.substring(i)
                val score = minOf(splitPartScore(left, frequencyOf), splitPartScore(right, frequencyOf))
                if (score > bestScore) {
                    bestScore = score
                    best = "$left $right"
                }
            }
            return best
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
                substitution != null && isNeighborKey(substitution.first, substitution.second) -> 0.7
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
            if (trimmed.isEmpty() || trimmed.last() == '\n' || trimmed.last() in ".!?") return PairCounts.SENTENCE_START
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
                settings.frenchPunctuationSpacing &&
                Punctuation.commitFrenchSpacedPunctuation(connection, boundary)
            ) {
                return true
            }
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
                if (!isLearningAllowed()) {
                    // Incognito field: remember nothing.
                } else if (keptOnceStore.contains(word)) {
                    keptOnceStore.remove(word)
                    // A sentence-start capital is learned lowercase; a capital mid-sentence is a name.
                    val before = inputConnection.getTextBeforeCursor(word.length + SPLIT_LOOKBACK, 0)
                        ?.toString().orEmpty().dropLast(word.length)
                    learnWord(if (isSentenceStartCapital(word, before)) word.lowercase() else word)
                } else {
                    keptOnceStore.add(word)
                }
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

        if (settings.aggressiveAutoReplace && isCamelCaseName(word)) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "camel_case_name"
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
        val keepReason = when {
            settings.aggressiveAutoReplace && word.any { it.isDigit() } -> "contains_digit" // "3pm", "5km"
            isEnglish && apostropheSplit == null &&
                isWordWithClitic(word, isKnown, rootMayBeName = isCapitalizedName(word, textBeforeWord)) -> "word_with_clitic"
            settings.aggressiveAutoReplace && isCapitalizedName(word, textBeforeWord) -> "capitalized_name"
            settings.aggressiveAutoReplace && word.all { it.isLowerCase() } && repository.isRecognizedWord(word) -> "recognized_word"
            else -> null
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

        if (settings.aggressiveAutoReplace && isEnglish && !isKnown(word) && !rejectedWords.contains(word.lowercase()) &&
            (word.all { it.isLowerCase() } || isSentenceStartCapital(word, textBeforeWord))
        ) {
            twoLetterFix(word.lowercase(), repository::getExactWordFrequency, suggestionEngine::isNeighborKey)?.let { fixed ->
                return commitDirectReplacement(applyCasing(fixed, word), "TWO_LETTER")
            }
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
                        FREQUENCY_WEIGHT * repository.getExactWordFrequency(candidate.candidate) / MAX_FREQUENCY
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

        val shouldReplace = top != null
            && (!isKnownWord || (isCaseVariant && !hasExactPrimaryCase) || (isOrthographicVariant && !isExactKnownWord)) // Allow case/orthographic fixes
            && !isRejected // Don't auto-correct if user has rejected this word
            && isSafeCandidate
            && lookupWord.length >= minWordLength // Minimum word length check on root
            && (settings.aggressiveAutoReplace || // the safety check already limits length changes to one letter
                top.candidate.length <= (word.length * maxLengthRatio).toInt() ||
                hasSingleRepeatedCharInsertion(word, top.candidate)) // Max length ratio check on full text

        // Aggressive mode splits a missed space ("hellohow" -> "hello how") when the one-word
        // fix is weak.
        val split = if (settings.aggressiveAutoReplace && isEnglish && !isKnownWord && !isRejected && apostropheSplit == null &&
            !isCapitalizedName(word, textBeforeWord)
        ) {
            findWordSplit(word) { part -> repository.getExactWordFrequency(part) }
        } else {
            null
        }
        val isWeakFix = !shouldReplace || top.distance >= 2 ||
            typoLikelihood(word, top.candidate, suggestionEngine::isNeighborKey) <= WEAK_FIX_LIKELIHOOD ||
            split != null && isMuchCommonerSplit(split, top.candidate)
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
        
        // Get text before cursor (need extra chars to check for boundary char)
        val textBeforeCursor = inputConnection.getTextBeforeCursor(
            replacement.replacedWord.length + 3, // boundary char, safety, and the char before the word
            0
        ) ?: return false

        if (textBeforeCursor.length < replacement.replacedWord.length) {
            return false
        }

        // Check if text ends with replaced word (with or without boundary char)
        val lastChars = textBeforeCursor.substring(
            maxOf(0, textBeforeCursor.length - replacement.replacedWord.length - 1)
        )

        val matchesReplacement = lastChars.endsWith(replacement.replacedWord) ||
            lastChars.trimEnd().endsWith(replacement.replacedWord)

        if (!matchesReplacement) {
            return false
        }

        // The replaced word plus the whitespace typed after it. Counting only whitespace keeps a
        // replacement's own trailing punctuation ("On my way!") from eating the character before it.
        val charsToDelete = replacement.replacedWord.length + (lastChars.length - lastChars.trimEnd().length)

        // Only undo the replaced word itself, not a longer word ending with it.
        if (textBeforeCursor.getOrNull(textBeforeCursor.length - charsToDelete - 1)?.isLetterOrDigit() == true) {
            return false
        }

        inputConnection.beginBatchEdit()
        inputConnection.deleteSurroundingText(charsToDelete, 0)
        inputConnection.commitText(replacement.originalWord, 1)
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
        pendingLearnWord = replacement.originalWord.takeIf { word ->
            settingsProvider().aggressiveAutoReplace && word.all { it.isLetter() || it == '\'' }
        }
        
        // Clear last replacement after undo
        lastReplacement = null
        return true
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
        return candidateIsName ||
            frequency >= SENTENCE_START_FIX_MIN_FREQUENCY &&
            likelihood >= SENTENCE_START_FIX_MIN_LIKELIHOOD
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
