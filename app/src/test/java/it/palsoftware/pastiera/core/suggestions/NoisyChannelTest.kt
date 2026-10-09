package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoisyChannelTest {
    // The Titan 2 Elite grid: Z under S, no gap between V and B.
    private val grid = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, 0 to i) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, 1 to i) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, 2 to i + 1) }
    }
    private val near: (Char, Char) -> Boolean = { a, b ->
        val p = grid[a]; val q = grid[b]
        p != null && q != null && Math.hypot((p.first - q.first).toDouble(), (p.second - q.second).toDouble()) <= 1.5
    }

    @Test
    fun aNeighbouringKeyIsACheaperSlipThanAFarOne() {
        assertTrue(NoisyChannel.cost("vrown", "brown", near) < NoisyChannel.cost("prown", "brown", near))
        assertTrue(NoisyChannel.cost("teh", "the", near) <= 0.5)
        assertTrue(NoisyChannel.cost("acomodation", "accommodation", near) < 1.0)
    }

    @Test
    fun theWordsAroundPickBetweenEquallyLikelySlips() {
        val pairs = mapOf("some milk" to 0, "some kill" to 0, "up some" to 50, "some" to 0, "buy milk" to 90)
        val channel = NoisyChannel(
            candidatesOf = { listOf("milk", "kill", "silk") },
            isNeighborKey = near,
            pairCount = { a, b -> pairs["$a $b"] ?: 0 },
            tripleCount = { a, b, c -> if ("$a $b $c" == "up some milk") 60 else 0 }
        )
        assertEquals("milk", channel.fixForUnknown("kilk", "some", "up"))
    }

    @Test
    fun noClearWinnerMeansNoFix() {
        val channel = NoisyChannel({ listOf("cat", "car") }, near, { _, _ -> 0 }, { _, _, _ -> 0 })
        assertNull(channel.fixForUnknown("cax", "the", null))
    }

    @Test
    fun aRealWordNeedsTheNextWordToClearlyPreferTheFix() {
        val pairs = mapOf("from my" to 400, "a letter" to 80, "letter from" to 60)
        val channel = NoisyChannel({ listOf("from", "foam") }, near, { a, b -> pairs["$a $b"] ?: 0 }, { _, _, _ -> 0 })
        assertEquals("from", channel.fixForRealWord("form", "letter", "a", "my"))
        assertNull(channel.fixForRealWord("form", "the", null, "is"))
    }

    @Test
    fun aMisspelledContractionIsFixedButARealWordNeverBecomesOne() {
        val pairs = mapOf("it doesn't" to 300, "doesn't matter" to 200, "we were" to 500, "we're here" to 400)
        val channel = NoisyChannel(
            { typed -> if (typed == "deosnt") listOf("doesn't", "doesn") else listOf("we're", "wire") },
            near, { a, b -> pairs["$a $b"] ?: 0 }, { _, _, _ -> 0 }
        )
        // Leaving out the apostrophe costs little; the swapped letters are the real slip.
        assertTrue(NoisyChannel.cost("deosnt", "doesn't", near) <= 0.7)
        assertEquals("doesn't", channel.fixForUnknown("deosnt", "it", null, "matter"))
        assertNull(channel.fixForRealWord("were", "we", null, "here"))
    }

    @Test
    fun anEarlierFixIsRevisitedOnceTheNextWordShowsABetterOne() {
        val pairs = mapOf("this is" to 900, "the is" to 0, "the cat" to 800, "this cat" to 20)
        val channel = NoisyChannel({ listOf("the", "this", "thus") }, near, { a, b -> pairs["$a $b"] ?: 0 }, { _, _, _ -> 0 })
        assertEquals("this", channel.reconsider("thes", "the", null, "is"))
        assertNull(channel.reconsider("thes", "the", null, "cat"))
    }
}
