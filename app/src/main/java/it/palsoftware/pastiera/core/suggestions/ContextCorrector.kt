package it.palsoftware.pastiera.core.suggestions

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
    /** Words whose fix the user undid; shared across language switches. */
    private val rejected: MutableSet<String> = mutableSetOf()
) {
    /**
     * Words around the boundary just typed. [before] is [PairCounts.SENTENCE_START] at a
     * sentence start, and [tail] is the boundary itself.
     */
    data class Trailing(val before: String, val word: String, val next: String, val tail: String)

    /** Returns the word that should replace [word] between [before] and [after], or null. */
    fun review(before: String, word: String, after: String): String? {
        CONFUSABLES[word.lowercase().replace('’', '\'')]?.let { other ->
            return reviewConfusable(before, word, after, other)
        }
        if (word.length < MIN_WORD_LENGTH || !word.all { it.isLetter() } || word.drop(1).any { it.isUpperCase() }) {
            return null
        }
        // A capital mid-sentence marks a name.
        if (word.first().isUpperCase() && before != PairCounts.SENTENCE_START) return null
        val typed = word.lowercase()
        if (typed in rejected || isProtected(word)) return null
        val typedFrequency = frequencyOf(typed)
        if (typedFrequency <= 0 || typedFrequency >= MAX_REVIEWED_FREQUENCY) return null
        val previous = before.lowercase()
        val next = after.lowercase()
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
        if (typed in rejected || other in rejected || isProtected(word)) return null
        if (word.first().isUpperCase() && before != PairCounts.SENTENCE_START) return null
        val previous = before.lowercase()
        val next = after.lowercase()
        // A contraction's next word is often only counted after its subject ("I quit", not "I'll quit").
        fun usage(form: String) =
            (pairCount(previous, form) + 1L) *
                (maxOf(pairCount(form, next), CONTRACTION_SUBJECTS[form]?.let { pairCount(it, next) } ?: 0) + 1L)
        val otherUsage = usage(other)
        if (otherUsage < CONFUSABLE_MIN_USAGE || otherUsage < usage(typed) * CONFUSABLE_RATIO) return null
        // A noun stays after words that introduce one ("kick his butt").
        if (typed in NOUN_CONFUSABLES &&
            (previous in NOUN_MARKERS || pairCount(previous, typed) >= pairCount(previous, other))
        ) {
            return null
        }
        return if (word.first().isUpperCase() || other.startsWith("i'")) other.replaceFirstChar { it.uppercase() } else other
    }

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
        /** Typed form -> the form it is often a mistake for; "but" never becomes "butt". */
        private val CONFUSABLES = mapOf("its" to "it's", "it's" to "its", "butt" to "but", "ill" to "i'll")
        private val CONTRACTION_SUBJECTS = mapOf("i'll" to "i")
        /** Confusables that are nouns in their own right, kept wherever a noun fits. */
        private val NOUN_CONFUSABLES = setOf("butt")
        private val NOUN_MARKERS = setOf(
            "a", "the", "my", "your", "his", "her", "its", "our", "their", "this", "that", "whose"
        )
        private const val CONFUSABLE_RATIO = 20
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
                            token.replace('’', '\'')
                        }
                        preceding.last() in SENTENCE_END -> PairCounts.SENTENCE_START
                        else -> return null
                    }
                }
            }
            return Trailing(before, word, next, tail)
        }
    }
}
