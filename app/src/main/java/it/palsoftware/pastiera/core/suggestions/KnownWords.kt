package it.palsoftware.pastiera.core.suggestions

/**
 * Real words the suggestion dictionary lacks, from common/dictionaries/<lang>_known.txt.
 * Autocorrect leaves them alone and nothing suggests them. Stored as one string of sorted words
 * plus start offsets, searched by binary search.
 */
class KnownWords private constructor(private val text: String, private val starts: IntArray) {
    val size: Int get() = starts.size

    /** Whether [word] (any case) is listed. */
    operator fun contains(word: String): Boolean {
        val target = word.lowercase()
        var low = 0
        var high = starts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val order = compareAt(mid, target)
            when {
                order < 0 -> low = mid + 1
                order > 0 -> high = mid - 1
                else -> return true
            }
        }
        return false
    }

    /** Compares the listed word at [index] with [target], like String.compareTo. */
    private fun compareAt(index: Int, target: String): Int {
        val start = starts[index]
        val end = if (index + 1 < starts.size) starts[index + 1] - 1 else text.length
        val length = end - start
        for (i in 0 until minOf(length, target.length)) {
            val difference = text[start + i] - target[i]
            if (difference != 0) return difference
        }
        return length - target.length
    }

    companion object {
        val EMPTY = KnownWords("", IntArray(0))

        /** Parses one word per line, in sorted order; '#' lines and blank lines are skipped. */
        fun parse(lines: Sequence<String>): KnownWords {
            val words = lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSortedSet().toList()
            val text = StringBuilder()
            val starts = IntArray(words.size)
            words.forEachIndexed { i, word ->
                if (i > 0) text.append('\n')
                starts[i] = text.length
                text.append(word)
            }
            return KnownWords(text.toString(), starts)
        }
    }
}
