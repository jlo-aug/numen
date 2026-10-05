package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test

class PairCountsTest {
    @Test
    fun parsesCountsAndSkipsCommentsAndMalformedLines() {
        val pairs = PairCounts.parse(
            sequenceOf("# header", "get some\t60", "some food\t46", "<s> won\t58", "broken line", "a b\tx", "")
        )
        assertEquals(3, pairs.size)
        assertEquals(60, pairs.count("get", "some"))
        assertEquals(46, pairs.count("some", "food"))
        assertEquals(58, pairs.count(PairCounts.SENTENCE_START, "won"))
        assertEquals(0, pairs.count("some", "get"))
        assertEquals(0, pairs.count("dome", "food"))
    }
}
