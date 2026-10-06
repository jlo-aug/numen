package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentWordsTest {
    private var clock = 0L
    private val words = RecentWords(now = { clock }, capacity = 3, idleResetMs = 1_000)
    private val oneLetterOff: (String, String) -> Boolean = { typed, word ->
        typed.length == word.length && typed.lowercase().zip(word).count { (a, b) -> a != b } == 1
    }

    @Test
    fun countsIgnoreCase() {
        words.record("Jonathan")
        words.record("jonathan")
        assertEquals(2, words.count("JONATHAN"))
        assertEquals(0, words.count("Jon"))
    }

    @Test
    fun aWordWrittenTwiceBecomesASlipTargetInTheUsersSpelling() {
        words.record("Ximena")
        assertNull(words.slipTarget("Ximwna", oneLetterOff))
        words.record("Ximena")
        assertEquals("Ximena", words.slipTarget("Ximwna", oneLetterOff))
        // A word already written as typed is never a slip.
        assertNull(words.slipTarget("ximena", oneLetterOff))
    }

    @Test
    fun twoPossibleTargetsMeanNoFix() {
        repeat(2) { words.record("cart"); words.record("card") }
        assertNull(words.slipTarget("carx", oneLetterOff))
    }

    @Test
    fun forgetsAfterAPauseAndDropsTheOldestWord() {
        repeat(2) { words.record("Ximena") }
        clock += 1_001
        assertEquals(0, words.count("Ximena"))

        listOf("one", "two", "three", "four").forEach(words::record)
        assertEquals(0, words.count("one"))
        assertEquals(1, words.count("four"))
    }

    @Test
    fun ignoresNumbersAndSymbols() {
        words.record("3pm")
        words.record("a@b")
        assertEquals(0, words.count("3pm"))
    }
}
