package it.palsoftware.pastiera.core.suggestions

/**
 * Words the user has just written, for this conversation only: kept in memory, never saved, and
 * forgotten after [idleResetMs] without typing. A word used twice here is trusted like a
 * dictionary word, so a slip on a friend's name ("Jonahtan") can be fixed.
 */
class RecentWords(
    private val now: () -> Long = System::currentTimeMillis,
    private val capacity: Int = 300,
    private val idleResetMs: Long = 30 * 60 * 1000L
) {
    // Insertion-ordered so the oldest word is dropped first. Keys keep the user's casing.
    private val counts = LinkedHashMap<String, Int>()
    private var lastWrittenAt = Long.MIN_VALUE / 2

    @Synchronized
    fun record(word: String) {
        if (word.length < 2 || !word.all { it.isLetter() || it == '\'' }) return
        expireIfIdle()
        lastWrittenAt = now()
        val count = (counts.remove(word) ?: 0) + 1
        counts[word] = count
        if (counts.size > capacity) counts.remove(counts.keys.first())
    }

    /** Times [word] was written in this conversation, ignoring case. */
    @Synchronized
    fun count(word: String): Int {
        expireIfIdle()
        return counts.entries.sumOf { (key, value) -> if (key.equals(word, ignoreCase = true)) value else 0 }
    }

    /**
     * The word written at least [MIN_USES] times that [typed] is one likely slip away from, or null.
     * Only an unambiguous match counts.
     */
    @Synchronized
    fun slipTarget(typed: String, isLikelySlip: (typed: String, word: String) -> Boolean): String? {
        expireIfIdle()
        if (counts.keys.any { it.equals(typed, ignoreCase = true) }) return null
        val matches = counts.entries
            .groupBy({ it.key.lowercase() }, { it.key to it.value })
            .filter { (lower, spellings) ->
                spellings.sumOf { it.second } >= MIN_USES && lower.length >= MIN_LENGTH && isLikelySlip(typed, lower)
            }
        // The spelling the user wrote most ("Jonathan", not "jonathan").
        return matches.values.singleOrNull()?.maxByOrNull { it.second }?.first
    }

    @Synchronized
    fun clear() {
        counts.clear()
    }

    private fun expireIfIdle() {
        if (now() - lastWrittenAt > idleResetMs) counts.clear()
    }

    companion object {
        const val MIN_USES = 2
        const val MIN_LENGTH = 3
    }
}
