package it.palsoftware.pastiera.core.suggestions

/**
 * Word-pair counts for sentence-aware correction. Words become ids and pairs a sorted LongArray
 * searched by binary search, which keeps the table to a few MB.
 */
class PairCounts private constructor(
    private val vocabulary: Map<String, Int>,
    private val keys: LongArray,
    private val counts: IntArray
) {
    val size: Int get() = keys.size

    /** How often [second] followed [first]; 0 when the pair is unknown. Inputs are lowercase. */
    fun count(first: String, second: String): Int {
        val a = vocabulary[first] ?: return 0
        val b = vocabulary[second] ?: return 0
        val index = keys.binarySearch(key(a, b))
        return if (index >= 0) counts[index] else 0
    }

    companion object {
        /** Sentence boundaries, as written in the pairs file. */
        const val SENTENCE_START = "<s>"
        const val SENTENCE_END = "</s>"

        // A pair packs [first id: 20 bits][second id: 20 bits][count: 23 bits] into 63 bits, so
        // the table sorts as one LongArray in key order. Larger counts are capped and larger
        // vocabularies rejected.
        private const val ID_BITS = 20
        private const val COUNT_BITS = 23
        private const val MAX_ID = (1 shl ID_BITS) - 1
        private const val MAX_COUNT = (1 shl COUNT_BITS) - 1

        private fun key(first: Int, second: Int): Long = (first.toLong() shl ID_BITS) or second.toLong()

        /** Parses "first second<TAB>count" lines; '#' lines and malformed lines are skipped. */
        fun parse(lines: Sequence<String>): PairCounts {
            val vocabulary = HashMap<String, Int>()
            var packed = LongArray(1 shl 16)
            var size = 0
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                val space = line.indexOf(' ')
                if (tab <= 0 || space <= 0 || space > tab) continue
                val count = line.substring(tab + 1).trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
                val first = vocabulary.getOrPut(line.substring(0, space)) { vocabulary.size }
                val second = vocabulary.getOrPut(line.substring(space + 1, tab)) { vocabulary.size }
                require(vocabulary.size <= MAX_ID) { "Pair vocabulary too large: ${vocabulary.size}" }
                if (size == packed.size) packed = packed.copyOf(size * 2)
                packed[size++] = (key(first, second) shl COUNT_BITS) or minOf(count, MAX_COUNT).toLong()
            }
            packed = packed.copyOf(size)
            packed.sort()
            return PairCounts(
                vocabulary,
                LongArray(size) { packed[it] ushr COUNT_BITS },
                IntArray(size) { (packed[it] and MAX_COUNT.toLong()).toInt() }
            )
        }
    }
}
