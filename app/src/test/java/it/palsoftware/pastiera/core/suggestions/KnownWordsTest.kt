package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownWordsTest {
    @Test
    fun findsListedWordsInAnyCase() {
        val words = KnownWords.parse(sequenceOf("# header", "nosy", "bothers", "", "congrats", "who're", "a"))
        assertEquals(5, words.size)
        listOf("nosy", "Bothers", "CONGRATS", "who're", "a").forEach { assertTrue(it, it in words) }
        listOf("nos", "nosey", "bother", "", "zzz", "aa", "who").forEach { assertFalse(it, it in words) }
        assertFalse("x" in KnownWords.EMPTY)
    }
}
