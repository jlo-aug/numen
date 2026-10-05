package it.palsoftware.pastiera.core.suggestions

import android.content.SharedPreferences

/**
 * Private typing stats for the home screen. Only counts are kept, on this device only, and the
 * caller skips restricted fields such as passwords.
 *
 * Speed counts active typing only: a gap over [MAX_ACTIVE_GAP_MS] between keys is a pause.
 * Words per minute use the standard five characters per word.
 */
class TypingStats(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis
) {
    data class Summary(
        val words: Long,
        val typosFixed: Long,
        val wordsPerMinute: Int?,
        val favoriteWord: String?
    )

    private var words = prefs.getLong(KEY_WORDS, 0)
    private var typosFixed = prefs.getLong(KEY_TYPOS_FIXED, 0)
    private var characters = prefs.getLong(KEY_CHARACTERS, 0)
    private var activeMs = prefs.getLong(KEY_ACTIVE_MS, 0)
    private val wordCounts: MutableMap<String, Int> = decodeCounts(prefs.getString(KEY_WORD_COUNTS, null))
    private var lastKeyAt = 0L
    private var unsavedWords = 0
    private var dirty = false
    private var resetAt = prefs.getLong(KEY_RESET_AT, 0)

    /** Called when the home screen resets the stats, so old numbers are not written back. */
    private fun followReset() {
        val latest = prefs.getLong(KEY_RESET_AT, 0)
        if (latest == resetAt) return
        resetAt = latest
        words = 0
        typosFixed = 0
        characters = 0
        activeMs = 0
        wordCounts.clear()
    }

    /** Records a key press for active typing time; characters come from [onWord]. */
    fun onKey() {
        followReset()
        val time = now()
        val gap = time - lastKeyAt
        lastKeyAt = time
        if (gap in 1..MAX_ACTIVE_GAP_MS) {
            activeMs += gap
            dirty = true
        }
    }

    /** A finished word, after any correction. */
    fun onWord(word: String) {
        if (word.isBlank() || word.none { it.isLetter() }) return
        followReset()
        words++
        characters += word.length + 1 // The word and the space or punctuation after it.
        dirty = true
        val key = word.lowercase()
        if (isFavoriteCandidate(key)) {
            if (key !in wordCounts && wordCounts.size >= MAX_TRACKED_WORDS) {
                // Halve every count and drop words that reach zero. Evicting only the least used
                // word would keep evicting new words and freeze the favourite.
                val iterator = wordCounts.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    entry.setValue(entry.value / 2)
                    if (entry.value == 0) iterator.remove()
                }
            }
            wordCounts[key] = (wordCounts[key] ?: 0) + 1
        }
        if (++unsavedWords >= SAVE_EVERY_WORDS) save()
    }

    fun onTypoFixed() {
        followReset()
        typosFixed++
        dirty = true
    }

    /** The user undid a correction, so it no longer counts as a fixed typo. */
    fun onFixUndone() {
        followReset()
        if (typosFixed > 0) typosFixed--
        dirty = true
    }

    /** Writes the stats if anything changed since the last save. */
    fun save() {
        if (!dirty) return
        dirty = false
        followReset()
        unsavedWords = 0
        prefs.edit()
            .putLong(KEY_WORDS, words)
            .putLong(KEY_TYPOS_FIXED, typosFixed)
            .putLong(KEY_CHARACTERS, characters)
            .putLong(KEY_ACTIVE_MS, activeMs)
            .putString(KEY_WORD_COUNTS, encodeCounts(wordCounts))
            .apply()
    }

    companion object {
        const val PREFS_NAME = "typing_stats"
        private const val KEY_WORDS = "words"
        private const val KEY_TYPOS_FIXED = "typos_fixed"
        private const val KEY_CHARACTERS = "characters"
        private const val KEY_ACTIVE_MS = "active_ms"
        private const val KEY_WORD_COUNTS = "word_counts"
        private const val KEY_RESET_AT = "reset_at"
        const val MAX_ACTIVE_GAP_MS = 2_000L
        private const val MIN_FAVORITE_LENGTH = 4
        private const val MIN_FAVORITE_USES = 3
        private const val MAX_TRACKED_WORDS = 300
        private const val SAVE_EVERY_WORDS = 10
        // Speed needs some typing behind it before it means anything.
        private const val MIN_CHARACTERS_FOR_SPEED = 100
        private const val MIN_ACTIVE_MS_FOR_SPEED = 20_000L

        /** Common words nobody would call a favourite. */
        private val FILLER_WORDS = setOf(
            "that", "this", "with", "have", "from", "they", "will", "would", "there", "their", "what",
            "about", "which", "when", "your", "were", "been", "just", "like", "then", "them", "than",
            "some", "into", "more", "also", "only", "very", "much", "really", "because", "should",
            "could", "here", "where", "these", "those", "does", "doing", "going", "being", "want", "know", "think", "make", "well", "even", "back", "still", "okay", "yeah"
        )

        /** A word worth calling a favourite: no contractions ("i've") and no filler. */
        private fun isFavoriteCandidate(key: String): Boolean =
            key.length >= MIN_FAVORITE_LENGTH && key.all { it.isLetter() } && key !in FILLER_WORDS

        /** Reads the summary without keeping a live store (the home screen). */
        fun summary(prefs: SharedPreferences): Summary {
            val characters = prefs.getLong(KEY_CHARACTERS, 0)
            val activeMs = prefs.getLong(KEY_ACTIVE_MS, 0)
            val wpm = if (characters >= MIN_CHARACTERS_FOR_SPEED && activeMs >= MIN_ACTIVE_MS_FOR_SPEED) {
                (characters / 5.0 / (activeMs / 60_000.0)).toInt()
            } else {
                null
            }
            val favorite = decodeCounts(prefs.getString(KEY_WORD_COUNTS, null))
                .filter { (word, uses) -> uses >= MIN_FAVORITE_USES && isFavoriteCandidate(word) }
                .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                ?.key
            return Summary(prefs.getLong(KEY_WORDS, 0), prefs.getLong(KEY_TYPOS_FIXED, 0), wpm, favorite)
        }

        fun reset(prefs: SharedPreferences, now: Long = System.currentTimeMillis()) {
            prefs.edit().clear().putLong(KEY_RESET_AT, now).apply()
        }

        private fun encodeCounts(counts: Map<String, Int>): String =
            counts.entries.joinToString("\n") { "${it.key}\t${it.value}" }

        private fun decodeCounts(encoded: String?): MutableMap<String, Int> {
            val counts = HashMap<String, Int>()
            encoded?.lineSequence()?.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) line.substring(tab + 1).toIntOrNull()?.let { counts[line.substring(0, tab)] = it }
            }
            return counts
        }
    }
}
