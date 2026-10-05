package it.palsoftware.pastiera.core.suggestions

import android.content.SharedPreferences

/**
 * Words the user kept once after undoing an autocorrection, remembered for [TTL_DAYS]. Keeping a
 * word again on a later occasion saves it to the personal dictionary (see AutoReplaceController).
 */
class KeptOnceStore(
    private val prefs: SharedPreferences?,
    private val today: () -> Long = { System.currentTimeMillis() / DAY_MILLIS }
) {
    private val memory = mutableSetOf<String>()

    /** Stored as "word|epochDay" so entries can expire and the oldest can be dropped first. */
    private fun entries(): Map<String, Long> =
        (prefs?.getStringSet(KEY, emptySet()) ?: memory).mapNotNull { raw ->
            val separator = raw.lastIndexOf('|')
            if (separator <= 0) return@mapNotNull null
            raw.substring(separator + 1).toLongOrNull()?.let { day -> raw.substring(0, separator) to day }
        }.toMap()

    fun contains(word: String): Boolean {
        val day = entries()[word.lowercase()] ?: return false
        return today() - day <= TTL_DAYS
    }

    fun add(word: String) = update { it + (word.lowercase() to today()) }

    fun remove(word: String) = update { it - word.lowercase() }

    private fun update(change: (Map<String, Long>) -> Map<String, Long>) {
        val now = today()
        val next = change(entries())
            .filterValues { day -> now - day <= TTL_DAYS }
            .entries.sortedByDescending { it.value }
            .take(MAX_WORDS)
            .map { "${it.key}|${it.value}" }
            .toSet()
        if (prefs != null) {
            prefs.edit().putStringSet(KEY, next).apply()
        } else {
            memory.clear()
            memory.addAll(next)
        }
    }

    companion object {
        const val PREFS_NAME = "autocorrect_learning"
        private const val KEY = "kept_once_words"
        private const val MAX_WORDS = 200
        private const val TTL_DAYS = 30L
        private const val DAY_MILLIS = 86_400_000L

        fun inMemory() = KeptOnceStore(null)
    }
}
