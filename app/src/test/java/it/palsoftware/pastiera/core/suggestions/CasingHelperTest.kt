package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test

class CasingHelperTest {

    @Test
    fun testApplyCasing_StandardTransformations() {
        // Keep lowercase
        assertEquals("apple", CasingHelper.applyCasing("apple", "app"))

        // Carry over Title Case
        assertEquals("Apple", CasingHelper.applyCasing("apple", "App"))

        // Carry over All Caps
        assertEquals("APPLE", CasingHelper.applyCasing("apple", "APP"))
    }

    @Test
    fun testApplyCasing_DictionaryPriority() {
        // Internal capitals in the dictionary must be kept (e.g. McCartney)
        // when the user types lowercase but the dictionary word has special casing
        assertEquals("McCartney", CasingHelper.applyCasing("McCartney", "mcc"))

        // Special cases like iPhone
        assertEquals("iPhone", CasingHelper.applyCasing("iPhone", "iph"))

        // But if the user types EVERYTHING in caps, the dictionary word should be all caps too
        assertEquals("MCCARTNEY", CasingHelper.applyCasing("McCartney", "MCC"))
    }

    @Test
    fun testApplyCasing_ForceLeadingCapital() {
        // Capital first letter even when the user types lowercase (e.g. sentence start)
        assertEquals("Apple", CasingHelper.applyCasing("apple", "app", forceLeadingCapital = true))

        // Should also work for dictionary entries that are already capitalized
        assertEquals("McCartney", CasingHelper.applyCasing("McCartney", "mcc", forceLeadingCapital = true))
    }

    @Test
    fun testApplyCasing_EdgeCases() {
        // Empty strings
        assertEquals("", CasingHelper.applyCasing("", "abc"))
        assertEquals("apple", CasingHelper.applyCasing("apple", ""))

        // No letters (numbers/symbols)
        assertEquals("123", CasingHelper.applyCasing("123", "12"))

        // One-letter words (checks the allUpper logic)
        assertEquals("A", CasingHelper.applyCasing("a", "A"))
        assertEquals("a", CasingHelper.applyCasing("a", "a"))
    }

    @Test
    fun testApplyCasing_PunctuationInOriginal() {
        // Apostrophes in the original should be ignored when deciding the casing
        // l'am -> l'amico (lowercase)
        assertEquals("l'amico", CasingHelper.applyCasing("l'amico", "l'am"))

        // L'am -> L'amico (capitalized)
        assertEquals("L'amico", CasingHelper.applyCasing("l'amico", "L'am"))
    }
}

