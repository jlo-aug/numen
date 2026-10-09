package it.palsoftware.pastiera.core.suggestions

import kotlin.math.ln

/**
 * Fixes a real word that is the wrong word in context ("get dome food" -> "some").
 *
 * A wrong change costs far more than a missed fix, so only rare words with no known pair on
 * either side are reviewed, and a replacement must be a one-key slip that is clearly more
 * common and seen next to both neighbours.
 */
class ContextCorrector(
    private val frequencyOf: (String) -> Int,
    private val isNeighborKey: (Char, Char) -> Boolean,
    /** How often the second word followed the first in real text (see PairCounts). */
    private val pairCount: (first: String, second: String) -> Int,
    /** Words meant exactly as typed, such as user words and slang. */
    private val isProtected: (String) -> Boolean = { false },
    /** Rare real words outside the main dictionary ("fir", "gad"): only the channel may review them. */
    private val isRecognized: (String) -> Boolean = { false },
    /** Words whose fix the user undid; shared across language switches. */
    private val rejected: MutableSet<String> = mutableSetOf(),
    /** Context x error-model second opinion once the next word is known (see NoisyChannel). */
    private val noisyChannel: NoisyChannel? = null,
    /** Real words often typed for another real word ("he" for "the"). */
    private val confusions: ConfusionTable? = null,
    /** How often [third] followed [first] [second] (see TripleCounts). */
    private val tripleCount: (first: String, second: String, third: String) -> Int = { _, _, _ -> 0 },
    /** Common words of up to three letters, with how many word pairs each starts. */
    private val shortWords: Map<String, Int> = emptyMap(),
    /** Words the dictionary holds as a name or acronym ("uk", "kb"). */
    private val isNameOrAcronym: (String) -> Boolean = { false }
) {
    /**
     * Words around the boundary just typed. [before] is [PairCounts.SENTENCE_START] at a
     * sentence start, and [tail] is the boundary itself.
     */
    data class Trailing(
        val before: String,
        val word: String,
        val next: String,
        val tail: String,
        /** The word before [before], when there is one. */
        val beforePrevious: String? = null
    )

    /** Returns the word that should replace [word] between [before] and [after], or null. */
    fun review(
        before: String,
        word: String,
        after: String,
        beforePrevious: String? = null,
        /** False for a word autocorrect just wrote: it fixed a typo, not a mix-up of real words. */
        typedAsIs: Boolean = true
    ): String? {
        CONFUSABLES[word.lowercase().replace('’', '\'')]?.let { other ->
            return reviewConfusable(before, word, after, other)
        }
        return reviewSlip(before, word, after) ?: reviewWithChannel(before, word, after) ?:
            if (typedAsIs) reviewConfusion(before, word, after, beforePrevious) ?: reviewShortWord(before, word, after, beforePrevious)
            else null
    }

    /**
     * A lone letter or two-letter non-word ("o tired", "te best") is a short word missing a
     * letter or hit off-key: the one that fits its neighbours clearly best. Texting shorthand
     * ("u", "r", "k") and known abbreviations stay.
     */
    private fun reviewShortWord(before: String, word: String, after: String, beforePrevious: String?): String? {
        if (shortWords.isEmpty() || after == PairCounts.SENTENCE_END || word.length > 2 || !word.all { it in 'a'..'z' }) return null
        if (word == "a" || word == "i" || word.length == 1 && word[0] in TEXTING_LETTERS) return null
        if (word.length == 2 && frequencyOf(word) >= SHORT_REAL_WORD_FREQUENCY) return null
        if (word in rejected || isProtected(word) || isNameOrAcronym(word)) return null
        val previous = before.lowercase()
        val next = after.lowercase()
        val earlier = beforePrevious?.lowercase()
        val ranked = shortWords.entries
            .filter { (candidate, _) -> candidate != word && candidate.length in word.length..word.length + 1 }
            .mapNotNull { (candidate, starts) ->
                val cost = NoisyChannel.cost(word, candidate, isNeighborKey)
                if (cost > SHORT_WORD_MAX_COST) return@mapNotNull null
                if (pairCount(previous, candidate) == 0 && pairCount(candidate, next) == 0) return@mapNotNull null
                candidate to fit(previous, candidate, next, earlier, starts) - SHORT_WORD_CHANNEL_WEIGHT * cost
            }
            .sortedByDescending { (_, score) -> score }
        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.second ?: Double.NEGATIVE_INFINITY
        return best.first.takeIf { best.second - runnerUp >= SHORT_WORD_MIN_MARGIN }
    }

    /** How well [candidate] fits between [previous] and [next] (see reviewConfusion). */
    private fun fit(previous: String, candidate: String, next: String, earlier: String?, starts: Int): Double {
        var fit = ln(pairCount(previous, candidate) + PAIR_SMOOTHING) +
            ln((pairCount(candidate, next) + PAIR_SMOOTHING) / (starts + STARTS_SMOOTHING))
        fit += CONFUSION_TRIPLE_WEIGHT * ln(1.0 + tripleCount(previous, candidate, next))
        if (earlier != null) fit += CONFUSION_TRIPLE_WEIGHT * ln(1.0 + tripleCount(earlier, previous, candidate))
        return fit
    }

    /**
     * A real word typed for another real word ("he best" for "the best"): the mix-up's prior
     * odds plus how much better the other word fits its neighbours must clear a high bar.
     */
    private fun reviewConfusion(before: String, word: String, after: String, beforePrevious: String?): String? {
        val table = confusions ?: return null
        if (after == PairCounts.SENTENCE_END || !word.all { it.isLowerCase() }) return null
        if (word in rejected || isProtected(word)) return null
        val previous = before.lowercase()
        val next = after.lowercase()
        val earlier = beforePrevious?.lowercase()
        fun fit(candidate: String, starts: Int) = fit(previous, candidate, next, earlier, starts)
        return table.of(word)
            .filter { it.meant !in rejected }
            // Two common words fit almost anywhere ("you the invite" / "you to invite"), so the
            // fix must be a phrase people actually write.
            .filter { minOf(it.typedStarts, it.meantStarts) <= CONFUSION_COMMON_STARTS || tripleCount(previous, it.meant, next) > 0 }
            .map { it.meant to minOf(it.priorLogOdds, CONFUSION_MAX_PRIOR_LOG_ODDS) + fit(it.meant, it.meantStarts) - fit(word, it.typedStarts) }
            .filter { (_, odds) -> odds > CONFUSION_MIN_LOG_ODDS }
            .maxByOrNull { (_, odds) -> odds }
            ?.first
    }

    /** The noisy-channel model's verdict now that both neighbours are known. */
    private fun reviewWithChannel(before: String, word: String, after: String): String? {
        val channel = noisyChannel ?: return null
        // Lowercase words with a real word after them: capitals may be names, and a sentence end
        // says too little ("too heavy to lift." is no slip for "left").
        if (word.length < 2 || !word.all { it.isLowerCase() } || after == PairCounts.SENTENCE_END) return null
        val typed = word.lowercase()
        if (typed in rejected || isProtected(word)) return null
        val previous = before.lowercase()
        val next = after.lowercase()
        val fixed = if (frequencyOf(typed) > 0) {
            // Two-letter words are too alike to tell apart ("said hi" is no slip for "said he").
            if (typed.length < 3) return null
            channel.fixForRealWord(typed, previous, null, next)
        } else if (isRecognized(typed)) {
            if (typed.length < 3) return null
            channel.fixForRealWord(typed, previous, null, next, RECOGNIZED_WORD_BIAS)
        } else {
            if (typed.length < 3) return null
            channel.fixForUnknown(typed, previous, null, next)
        }
        return fixed?.let { recase(it, word) }
    }

    /**
     * [current] replaced the typo [typed] at the last space; with [after] known, the word that
     * fits better, or null to keep [current].
     */
    fun reconsiderFix(before: String, typed: String, current: String, after: String): String? {
        val channel = noisyChannel ?: return null
        if (after == PairCounts.SENTENCE_END || typed.length < 3 || !typed.all { it.isLetter() }) return null
        // Lowercase, or capitalised only for the sentence start.
        if (typed.drop(1).any { it.isUpperCase() } || typed.first().isUpperCase() && before != PairCounts.SENTENCE_START) return null
        val t = typed.lowercase()
        val c = current.lowercase()
        if (t in rejected || c in rejected) return null
        return channel.reconsider(t, c, before.lowercase(), after.lowercase())?.let { recase(it, current) }
    }

    private fun reviewSlip(before: String, word: String, after: String): String? {
        if (word.length < MIN_WORD_LENGTH || !word.all { it.isLetter() } || word.drop(1).any { it.isUpperCase() }) {
            return null
        }
        // A capital mid-sentence marks a name.
        if (word.first().isUpperCase() && before != PairCounts.SENTENCE_START) return null
        val typed = word.lowercase()
        if (typed in rejected || isProtected(word) || isRecognized(typed)) return null
        val typedFrequency = frequencyOf(typed)
        val previous = before.lowercase()
        val next = after.lowercase()
        // A short non-word left as typed ("install hs been"): both neighbours pick the word.
        if (typedFrequency <= 0 && typed.length in 2..3) return reviewShortSlip(typed, previous, next)?.let { recase(it, word) }
        if (typedFrequency <= 0 || typedFrequency >= MAX_REVIEWED_FREQUENCY) return null
        if (pairCount(previous, typed) > 0 || pairCount(typed, next) > 0) return null

        return slips(typed)
            .filter { candidate -> candidate.length >= MIN_WORD_LENGTH && keepsFirstLetter(typed, candidate) }
            .filter { candidate ->
                val frequency = frequencyOf(candidate)
                frequency >= MIN_REPLACEMENT_FREQUENCY && frequency >= typedFrequency + MIN_FREQUENCY_GAIN
            }
            .map { candidate -> candidate to pairCount(previous, candidate).toLong() * pairCount(candidate, next) }
            .filter { (_, evidence) -> evidence > 0 }
            .maxByOrNull { (_, evidence) -> evidence }
            ?.first
            ?.let { replacement -> if (word.first().isUpperCase()) replacement.replaceFirstChar { it.uppercase() } else replacement }
    }

    /**
     * Swaps a confusable pair ("its" / "it's", "butt" / "but") when real text clearly prefers
     * the other form between these neighbours.
     */
    private fun reviewConfusable(before: String, word: String, after: String, other: String): String? {
        val typed = word.lowercase().replace('’', '\'')
        if (typed in rejected || other in rejected || isProtected(word) || isRecognized(typed)) return null
        if (word.first().isUpperCase() && before != PairCounts.SENTENCE_START) return null
        val previous = before.lowercase()
        val next = after.lowercase()
        // A contraction's next word is often only counted after its subject ("I quit", not "I'll quit").
        fun usage(form: String) =
            (pairCount(previous, form) + 1L) *
                (maxOf(pairCount(form, next), CONTRACTION_SUBJECTS[form]?.let { pairCount(it, next) } ?: 0) + 1L)
        val otherUsage = usage(other)
        // Never seen before the next word, while the other form often is ("its mostly").
        val nextOnly = pairCount(typed, next) == 0 && pairCount(other, next) >= CONFUSABLE_NEXT_ONLY_MIN
        if (!nextOnly && (otherUsage < CONFUSABLE_MIN_USAGE || otherUsage < usage(typed) * CONFUSABLE_RATIO)) return null
        // A noun stays after words that introduce one ("kick his butt").
        if (typed in NOUN_CONFUSABLES &&
            (previous in NOUN_MARKERS || pairCount(previous, typed) >= pairCount(previous, other))
        ) {
            return null
        }
        return if (word.first().isUpperCase() || other.startsWith("i'")) other.replaceFirstChar { it.uppercase() } else other
    }

    /** The one common word, a slip away, that clearly fits between both neighbours. */
    private fun reviewShortSlip(typed: String, previous: String, next: String): String? {
        if (!typed.all { it in 'a'..'z' }) return null
        val candidates = slips(typed).filter { candidate -> frequencyOf(candidate) >= MIN_REPLACEMENT_FREQUENCY }
        // A word before with no counts at all ("install") says nothing; otherwise it must fit too.
        val previousSpeaks = candidates.any { pairCount(previous, it) > 0 }
        val ranked = candidates
            .map { candidate ->
                val after = pairCount(candidate, next).toLong()
                candidate to if (previousSpeaks) pairCount(previous, candidate) * after else after
            }
            .filter { (_, evidence) -> evidence > 0 }
            .sortedByDescending { (_, evidence) -> evidence }
        val (best, evidence) = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.second ?: 0L
        return best.takeIf { pairCount(best, next) >= SHORT_SLIP_MIN_NEXT_PAIR && evidence >= runnerUp * SHORT_SLIP_RATIO }
    }

    private fun recase(fixed: String, typed: String): String =
        if (typed.first().isUpperCase()) fixed.replaceFirstChar { it.uppercase() } else fixed

    /** A slip may change the first letter but never drop or add one ("hone" is not "one"). */
    private fun keepsFirstLetter(typed: String, candidate: String): Boolean =
        candidate.first() == typed.first() || candidate.length == typed.length

    /**
     * One-key slips: a neighbouring key, swapped letters, a missed letter, or an extra letter a
     * finger could plausibly add.
     */
    private fun slips(word: String): Set<String> = buildSet {
        for (i in word.indices) {
            for (c in 'a'..'z') {
                if (c != word[i] && isNeighborKey(word[i], c)) add(word.replaceRange(i, i + 1, c.toString()))
            }
            if (i + 1 < word.length) add(word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2))
            if (isPlausibleExtraLetter(word, i)) add(word.removeRange(i, i + 1))
        }
        for (i in 0..word.length) {
            for (c in 'a'..'z') add(word.substring(0, i) + c + word.substring(i))
        }
        remove(word)
    }

    private fun isPlausibleExtraLetter(word: String, index: Int): Boolean {
        val letter = word[index]
        return listOfNotNull(word.getOrNull(index - 1), word.getOrNull(index + 1))
            .any { beside -> beside == letter || isNeighborKey(letter, beside) }
    }

    companion object {
        // How much more a rare recognised word must lose by before the channel replaces it
        // (common real words use NoisyChannel's own, larger bias).
        private const val RECOGNIZED_WORD_BIAS = 1.0
        // Real-word confusions (tuned on held-out real typing, see tools/build_confusions.py).
        private const val CONFUSION_MIN_LOG_ODDS = 6.0
        // Words that start more word pairs than this are common enough to fit almost anywhere.
        private const val CONFUSION_COMMON_STARTS = 50_000
        // The typing data overstates some mix-ups ("if" for "of"); no prior counts for more than this.
        private const val CONFUSION_MAX_PRIOR_LOG_ODDS = -1.0
        private const val CONFUSION_TRIPLE_WEIGHT = 0.5
        private const val PAIR_SMOOTHING = 0.5
        // Lone letters and two-letter non-words (tuned on held-out real typing).
        private const val TEXTING_LETTERS = "urkycbwnx"
        private const val SHORT_REAL_WORD_FREQUENCY = 100
        private const val SHORT_WORD_MAX_COST = 1.0
        private const val SHORT_WORD_CHANNEL_WEIGHT = 3.0
        private const val SHORT_WORD_MIN_MARGIN = 3.0
        private const val STARTS_SMOOTHING = 50.0
        /** Typed form -> the form it is often a mistake for; "but" never becomes "butt". */
        private val CONFUSABLES = mapOf(
            "its" to "it's", "it's" to "its", "butt" to "but", "ill" to "i'll", "id" to "i'd"
        )
        private val CONTRACTION_SUBJECTS = mapOf("i'll" to "i", "i'd" to "i")
        /** Confusables that are nouns in their own right, kept wherever a noun fits. */
        private val NOUN_CONFUSABLES = setOf("butt")
        private val NOUN_MARKERS = setOf(
            "a", "the", "my", "your", "his", "her", "its", "our", "their", "this", "that", "whose"
        )
        private const val CONFUSABLE_RATIO = 20
        private const val CONFUSABLE_NEXT_ONLY_MIN = 20
        private const val SHORT_SLIP_MIN_NEXT_PAIR = 20
        private const val SHORT_SLIP_RATIO = 5
        private const val CONFUSABLE_MIN_USAGE = 50L
        private const val MIN_WORD_LENGTH = 3
        private const val MAX_REVIEWED_FREQUENCY = 160
        private const val MIN_REPLACEMENT_FREQUENCY = 140
        private const val MIN_FREQUENCY_GAIN = 25

        // The two words before the boundary just typed. Newlines are not reviewed because apps
        // often act on Enter before an edit could land.
        private val LAST_TWO_WORDS = Regex("""(\p{L}+(?:['’]\p{L}+)?) (\p{L}+(?:['’]\p{L}+)?)([.,!?;:] ?| )$""")
        private val SENTENCE_END = setOf('.', '!', '?')

        /**
         * Finds the words around the boundary just typed, or null when anything is unclear, such
         * as a number, punctuation or a word cut off by the editor ([truncated]).
         */
        fun findTrailing(text: String, truncated: Boolean): Trailing? {
            val match = LAST_TWO_WORDS.find(text) ?: return null
            val (word, next, tail) = match.destructured
            val start = match.range.first
            var beforePrevious: String? = null
            val before = when {
                start == 0 -> if (truncated) return null else PairCounts.SENTENCE_START
                text[start - 1] == '\n' -> PairCounts.SENTENCE_START
                text[start - 1] != ' ' -> return null
                start == 1 -> PairCounts.SENTENCE_START
                else -> {
                    val preceding = text.substring(0, start - 1)
                    val token = preceding.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }
                    when {
                        token.isNotEmpty() -> {
                            val tokenStart = preceding.length - token.length
                            if (tokenStart == 0 && truncated) return null
                            if (tokenStart > 0 && !preceding[tokenStart - 1].isWhitespace()) return null
                            beforePrevious = earlierWord(preceding.substring(0, tokenStart))
                            token.replace('’', '\'')
                        }
                        preceding.last() in SENTENCE_END -> PairCounts.SENTENCE_START
                        else -> return null
                    }
                }
            }
            return Trailing(before, word, next, tail, beforePrevious)
        }

        /** The word ending [text] after a single space, if it is a whole word. */
        private fun earlierWord(text: String): String? {
            if (!text.endsWith(' ')) return null
            val trimmed = text.dropLast(1)
            val token = trimmed.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }
            if (token.isEmpty()) return null
            val start = trimmed.length - token.length
            return token.replace('’', '\'').takeIf { start == 0 || trimmed[start - 1].isWhitespace() }
        }
    }
}
