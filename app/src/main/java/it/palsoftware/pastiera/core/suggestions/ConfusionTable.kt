package it.palsoftware.pastiera.core.suggestions

/**
 * Real words often typed for another real word ("he" for "the", "you" for "your"), with how
 * likely each mix-up is (see tools/build_confusions.py). ContextCorrector weighs these against the
 * words around them.
 */
class ConfusionTable private constructor(private val byTyped: Map<String, List<Confusion>>) {
    /**
     * [meant] instead of the typed word; [priorLogOdds] is how likely that is before looking at
     * the words around it. [typedStarts] and [meantStarts] count the word pairs each word starts.
     */
    data class Confusion(val meant: String, val priorLogOdds: Double, val typedStarts: Int, val meantStarts: Int)

    val size: Int get() = byTyped.values.sumOf { it.size }

    fun of(typed: String): List<Confusion> = byTyped[typed].orEmpty()

    companion object {
        /** Parses "typed<TAB>meant<TAB>log-odds<TAB>typed starts<TAB>meant starts"; skips '#' and bad lines. */
        fun parse(lines: Sequence<String>): ConfusionTable {
            val byTyped = HashMap<String, MutableList<Confusion>>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val cells = line.split('\t')
                if (cells.size != 5) continue
                val odds = cells[2].toDoubleOrNull() ?: continue
                val typedStarts = cells[3].toIntOrNull() ?: continue
                val meantStarts = cells[4].toIntOrNull() ?: continue
                byTyped.getOrPut(cells[0]) { mutableListOf() } += Confusion(cells[1], odds, typedStarts, meantStarts)
            }
            return ConfusionTable(byTyped)
        }
    }
}
