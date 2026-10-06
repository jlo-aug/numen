package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test

class TripleCountsTest {
    @Test
    fun parsesCountsAndSkipsCommentsAndMalformedLines() {
        val triples = TripleCounts.parse(
            sequenceOf("# header", "up some milk\t12", "<s> i think\t300", "two words\t5", "a b c\tx", "")
        )
        assertEquals(2, triples.size)
        assertEquals(12, triples.count("up", "some", "milk"))
        assertEquals(300, triples.count(PairCounts.SENTENCE_START, "i", "think"))
        assertEquals(0, triples.count("up", "some", "kill"))
        assertEquals(0, triples.count("some", "up", "milk"))
    }

    @Test
    fun hugeCountsAreCapped() {
        val triples = TripleCounts.parse(sequenceOf("<s> i am\t100000"))
        assertEquals(511, triples.count(PairCounts.SENTENCE_START, "i", "am"))
    }
}
