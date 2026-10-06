package it.palsoftware.pastiera.core.suggestions

/**
 * Word-triple counts ("pick up some" -> "milk") for ranking autocorrect candidates by the two
 * words before. Same layout as [PairCounts]: words become ids, and each triple packs into one Long
 * of a sorted array searched by binary search.
 */
class TripleCounts private constructor(
    private val vocabulary: Map<String, Int>,
    private val keys: LongArray,
    private val counts: ShortArray
) {
    val size: Int get() = keys.size

    /** How often [third] followed [first] [second]; 0 when unknown. Inputs are lowercase. */
    fun count(first: String, second: String, third: String): Int {
        val a = vocabulary[first] ?: return 0
        val b = vocabulary[second] ?: return 0
        val c = vocabulary[third] ?: return 0
        val index = keys.binarySearch(key(a, b, c))
        return if (index >= 0) counts[index].toInt() else 0
    }

    companion object {
        // [first: 18 bits][second: 18 bits][third: 18 bits][count: 9 bits] = 63 bits. Counts are
        // capped: ranking only needs to know a triple is common, not how common.
        private const val ID_BITS = 18
        private const val COUNT_BITS = 9
        private const val MAX_ID = (1 shl ID_BITS) - 1
        private const val MAX_COUNT = (1 shl COUNT_BITS) - 1

        private fun key(a: Int, b: Int, c: Int): Long =
            (a.toLong() shl (2 * ID_BITS)) or (b.toLong() shl ID_BITS) or c.toLong()

        /** Parses "first second third<TAB>count" lines; '#' lines and malformed lines are skipped. */
        fun parse(lines: Sequence<String>): TripleCounts {
            val vocabulary = HashMap<String, Int>()
            var packed = LongArray(1 shl 16)
            var size = 0
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                val space1 = line.indexOf(' ')
                val space2 = if (space1 > 0) line.indexOf(' ', space1 + 1) else -1
                if (tab <= 0 || space1 <= 0 || space2 <= space1 || space2 > tab) continue
                val count = line.substring(tab + 1).trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
                val a = vocabulary.getOrPut(line.substring(0, space1)) { vocabulary.size }
                val b = vocabulary.getOrPut(line.substring(space1 + 1, space2)) { vocabulary.size }
                val c = vocabulary.getOrPut(line.substring(space2 + 1, tab)) { vocabulary.size }
                require(vocabulary.size <= MAX_ID) { "Triple vocabulary too large: ${vocabulary.size}" }
                if (size == packed.size) packed = packed.copyOf(size * 2)
                packed[size++] = (key(a, b, c) shl COUNT_BITS) or minOf(count, MAX_COUNT).toLong()
            }
            packed = packed.copyOf(size)
            packed.sort()
            return TripleCounts(
                vocabulary,
                LongArray(size) { packed[it] ushr COUNT_BITS },
                ShortArray(size) { (packed[it] and MAX_COUNT.toLong()).toInt().toShort() }
            )
        }
    }
}
