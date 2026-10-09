package it.palsoftware.pastiera.core.suggestions

import kotlin.math.ln

/**
 * Picks the word most likely meant, from how likely the typed letters are as a slip of it on the
 * Titan 2 Elite's keys and how well it fits the words around it (the "noisy channel" model:
 * context x error model). Weights were fitted on real typing: physical-keyboard keystrokes (Aalto
 * 136M keystrokes) and phone typing (Aalto ITE typing dataset), held-out data checked separately.
 */
class NoisyChannel(
    /** Lowercase dictionary words within two edits of the typed word. */
    private val candidatesOf: (String) -> List<String>,
    private val isNeighborKey: (Char, Char) -> Boolean,
    private val pairCount: (first: String, second: String) -> Int,
    private val tripleCount: (first: String, second: String, third: String) -> Int
) {
    data class Pick(val word: String, val score: Double, val runnerUp: Double, val typedScore: Double)

    /** Candidates for [typed]; a real word is never swapped for a contraction ("were" -> "we're"). */
    private fun candidates(typed: String, allowContractions: Boolean): List<String> =
        candidatesOf(typed).filter { allowContractions || '\'' !in it }

    /**
     * The best candidate for [typed] (lowercase) between [previous] / [beforePrevious] and [next]
     * (null when unknown), or null when no candidate is a likely slip.
     */
    fun pick(
        typed: String,
        previous: String?,
        beforePrevious: String?,
        next: String?,
        allowContractions: Boolean = true
    ): Pick? {
        // Texting shorthand has no vowels ("lmk", "smh") and is never a slip.
        if (typed.none { it in "aeiouy" }) return null
        val scored = candidates(typed, allowContractions)
            .asSequence()
            .filter { it != typed }
            .mapNotNull { word ->
                val cost = cost(typed, word, isNeighborKey)
                if (cost > MAX_COST) null else word to score(word, cost, previous, beforePrevious, next)
            }
            .sortedByDescending { it.second }
            .take(2)
            .toList()
        val (best, bestScore) = scored.firstOrNull() ?: return null
        return Pick(
            word = best,
            score = bestScore,
            runnerUp = scored.getOrNull(1)?.second ?: Double.NEGATIVE_INFINITY,
            typedScore = score(typed, 0.0, previous, beforePrevious, next)
        )
    }

    /**
     * A made-up word: the pick if it is a likely enough slip and clearly beats every other
     * candidate. [next] is known when the word is looked at again after the next one.
     */
    fun fixForUnknown(typed: String, previous: String?, beforePrevious: String?, next: String? = null): String? =
        pick(typed, previous, beforePrevious, next)?.let(::confidentFix)

    /** The pick's word when it is a confident fix for a made-up word, else null. */
    fun confidentFix(pick: Pick): String? =
        pick.word.takeIf { pick.score > UNKNOWN_WORD_SCORE && pick.score - pick.runnerUp >= MIN_MARGIN }

    /** A real word that may be a slip: the pick if it fits clearly better than the typed word. */
    fun fixForRealWord(
        typed: String,
        previous: String?,
        beforePrevious: String?,
        next: String,
        bias: Double = REAL_WORD_BIAS
    ): String? =
        pick(typed, previous, beforePrevious, next, allowContractions = false)?.takeIf {
            it.score - (it.typedScore + bias) > 0 && it.score - it.runnerUp >= MIN_MARGIN
        }?.word

    /**
     * An earlier fix of [typed] to [current], seen again now that [next] is known: the word to
     * switch to, if one clearly beats [current] ("ths" -> "the", then "ths is" -> "this").
     */
    fun reconsider(typed: String, current: String, previous: String?, next: String): String? {
        val pick = pick(typed, previous, null, next) ?: return null
        if (pick.word == current || pick.score <= UNKNOWN_WORD_SCORE || pick.score - pick.runnerUp < MIN_MARGIN) return null
        val currentScore = score(current, cost(typed, current, isNeighborKey), previous, null, next)
        return pick.word.takeIf { pick.score - currentScore >= RECONSIDER_MARGIN }
    }

    private fun score(word: String, cost: Double, previous: String?, beforePrevious: String?, next: String?): Double {
        var s = -CHANNEL_WEIGHT * cost
        if (previous != null) s += PAIR_WEIGHT * ln(1.0 + pairCount(previous, word))
        if (previous != null && beforePrevious != null) s += TRIPLE_WEIGHT * ln(1.0 + tripleCount(beforePrevious, previous, word))
        if (next != null) s += NEXT_WEIGHT * ln(1.0 + pairCount(word, next))
        return s
    }

    companion object {
        // Fitted on real typing; see the class comment.
        private const val CHANNEL_WEIGHT = 7.578
        private const val PAIR_WEIGHT = 0.568
        private const val TRIPLE_WEIGHT = 0.533
        private const val NEXT_WEIGHT = 0.777
        private const val MAX_COST = 1.4
        private const val MIN_MARGIN = 2.0
        private const val REAL_WORD_BIAS = 3.0
        // How much better a word must fit to replace an earlier fix.
        private const val RECONSIDER_MARGIN = 3.5
        // How likely a made-up word is meant as typed (a name or new word) rather than a slip.
        private const val UNKNOWN_WORD_SCORE = -8.0

        // Error costs (lower = likelier slip).
        private const val NEAR_KEY = 0.5
        private const val FAR_KEY = 1.2
        private const val VOWEL = 0.6
        private const val MISSED = 0.7
        private const val MISSED_DOUBLE = 0.4
        // The apostrophe needs Alt on the Titan, so it is often left out.
        private const val MISSED_APOSTROPHE = 0.2
        private const val EXTRA = 0.9
        private const val EXTRA_FAT_FINGER = 0.5
        private const val SWAPPED = 0.5
        private const val VOWELS = "aeiou"

        /** Contractions whose form without the apostrophe is no word ("doesnt", unlike "cant" or "well"). */
        private val UNAMBIGUOUS_CONTRACTIONS = setOf(
            "don't", "doesn't", "didn't", "isn't", "aren't", "wasn't", "weren't", "haven't", "hasn't",
            "hadn't", "couldn't", "wouldn't", "shouldn't", "mustn't", "needn't", "you're", "they're",
            "you've", "we've", "they've", "you'll", "they'll", "it'll", "that'll", "you'd", "they'd",
            "that's", "what's", "there's", "here's", "who's", "where's", "let's"
        )

        fun isUnambiguousContraction(word: String): Boolean = word in UNAMBIGUOUS_CONTRACTIONS

        /** Weighted edit cost of [typed] against [word]: how unlikely a slip it is. */
        internal fun cost(typed: String, word: String, isNeighborKey: (Char, Char) -> Boolean): Double {
            val t = typed
            val w = word
            var twoBack = DoubleArray(w.length + 1)
            var previous = DoubleArray(w.length + 1)
            for (j in 1..w.length) previous[j] = previous[j - 1] + if (w[j - 1] == '\'') MISSED_APOSTROPHE else MISSED
            var current = DoubleArray(w.length + 1)
            for (i in 1..t.length) {
                val letter = t[i - 1]
                val before = t.getOrNull(i - 2)
                val after = t.getOrNull(i)
                val fatFinger = (before != null && (before == letter || isNeighborKey(letter, before))) ||
                    (after != null && (after == letter || isNeighborKey(letter, after)))
                val extra = if (fatFinger) EXTRA_FAT_FINGER else EXTRA
                current[0] = previous[0] + EXTRA
                for (j in 1..w.length) {
                    val target = w[j - 1]
                    val substitution = when {
                        letter == target -> 0.0
                        isNeighborKey(letter, target) -> NEAR_KEY
                        letter in VOWELS && target in VOWELS -> VOWEL
                        else -> FAR_KEY
                    }
                    val missed = when {
                        w[j - 1] == '\'' -> MISSED_APOSTROPHE
                        j > 1 && w[j - 1] == w[j - 2] -> MISSED_DOUBLE
                        else -> MISSED
                    }
                    var c = minOf(previous[j] + extra, current[j - 1] + missed, previous[j - 1] + substitution)
                    if (i > 1 && j > 1 && letter == w[j - 2] && before == target && letter != target) {
                        c = minOf(c, twoBack[j - 2] + SWAPPED)
                    }
                    current[j] = c
                }
                val recycled = twoBack
                twoBack = previous
                previous = current
                current = recycled
            }
            return previous[w.length]
        }
    }
}
