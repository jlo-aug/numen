package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SuggestionEngineTest {

    private val locale = Locale.ITALIAN
    private val fakeRepo = FakeDictionaryRepository()
    private val engine = SuggestionEngine(fakeRepo, locale = locale)

    @Test
    fun testIsReadyCheck() {
        fakeRepo.addTestEntry("hallo", 100)
        fakeRepo.isReady = false
        
        val results = engine.suggest("hallo")
        assertTrue("Should return an empty list when the repo isn't ready", results.isEmpty())
        
        fakeRepo.isReady = true
        val resultsReady = engine.suggest("hall") // Prefix search
        assertTrue("Should return results when the repo is ready", resultsReady.isNotEmpty())
    }

    @Test
    fun testKeyboardProximity_QWERTY() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("hallo", 200)

        // 'hsllo' (S is next to A -> distance 1.0)
        val resultsNear = engine.suggest("hsllo")
        val scoreNear = resultsNear.find { it.candidate == "hallo" }?.score ?: 0.0

        // 'hmllo' (M is far from A -> distance ~8.0)
        val resultsFar = engine.suggest("hmllo")
        val scoreFar = resultsFar.find { it.candidate == "hallo" }?.score ?: 0.0

        assertTrue("Nearby key ($scoreNear) should score higher than a distant key ($scoreFar)", scoreNear > scoreFar)
    }

    @Test
    fun testProximityFiltering() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("hallo", 200)

        // 'hmllo' -> 'hallo' is a substitution of 'm' for 'a'.
        // The distance is > 2.5, so it should be filtered when proximity is active.
        val results = engine.suggest("hmllo", useKeyboardProximity = true)
        assertTrue("Should filter the distant substitution 'm' -> 'a'", results.none { it.candidate == "hallo" })

        // Without proximity filtering it should be found
        val resultsNoFilter = engine.suggest("hmllo", useKeyboardProximity = false)
        assertTrue("Should be found without proximity filtering", resultsNoFilter.any { it.candidate == "hallo" })
    }

    @Test
    fun testAccentMatching() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("perché", 200)
        
        // User types "perche" (without the accent)
        val results = engine.suggest("perche", includeAccentMatching = true)
        assertEquals("Should find 'perché' as the top suggestion", "perché", results.firstOrNull()?.candidate)
    }

    @Test
    fun accentlessFrenchWordSuggestsAccentedDictionaryEntry() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("derrière", 200)

        val results = engine.suggest("derriere", includeAccentMatching = true)

        assertEquals("derrière", results.firstOrNull()?.candidate)
        assertEquals(0, results.firstOrNull()?.distance)
    }

    @Test
    fun testCaseOnlyVariantIsSuggestedForLowercaseInput() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("Problem", 200)
        fakeRepo.addTestEntry("Probleme", 250)

        val results = engine.suggest("problem")

        assertTrue("Should suggest the same-length case variant 'Problem'", results.any { it.candidate == "Problem" })
    }

    @Test
    fun testUserDictionaryRanking() {
        fakeRepo.isReady = true
        // "hallo" in the main dictionary
        fakeRepo.addTestEntry("hallo", 100, SuggestionSource.MAIN)
        // "hallx" in the user dictionary
        fakeRepo.addTestEntry("hallx", 100, SuggestionSource.USER)
        
        val results = engine.suggest("hall")
        
        assertEquals("User word should come first", "hallx", results.firstOrNull()?.candidate)
        assertEquals("User word should be marked as USER", SuggestionSource.USER, results.first().source)
    }

    @Test
    fun testDynamicDownloadSimulation() {
        fakeRepo.isReady = true
        
        // Empty at first
        assertTrue(engine.suggest("hallo").isEmpty())

        // "Download" dynamically
        fakeRepo.addTestEntry("hallo", 200)
        
        val results = engine.suggest("hall")
        assertEquals(1, results.size)
        assertEquals("hallo", results[0].candidate)
    }

    @Test
    fun testLigatureFoldPrefersOeilOverNeil() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("œil", 116)
        fakeRepo.addTestEntry("Neil", 97)

        val results = engine.suggest("oeil")
        assertEquals("œil", results.firstOrNull()?.candidate)
    }

    @Test
    fun testApostropheLigatureRecompose() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("œil", 116)
        fakeRepo.addTestEntry("Neil", 97)

        val results = engine.suggest("l'oeil")
        assertEquals("l'œil", results.firstOrNull()?.candidate)
    }

    @Test
    fun testLongItalianElisionRecompose() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("amico", 120)

        val results = engine.suggest("dell'amivo")
        assertEquals("dell'amico", results.firstOrNull()?.candidate)
    }

    @Test
    fun testProperNameScenario_LorealVsLoral() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("L'Oréal", 120)
        fakeRepo.addTestEntry("l'oral", 220)

        val results = engine.suggest("l'oreal")
        assertEquals("l'Oréal", results.firstOrNull()?.candidate)
    }

    @Test
    fun testProperNameScenario_GenericCloserThanProperName() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("L'Oréal", 120)
        fakeRepo.addTestEntry("l'oral", 220)

        // Typo closer to generic word: "l'orak" -> "l'oral" (distance 1)
        // while "L'Oréal" requires more edits.
        val results = engine.suggest("l'orak")
        assertEquals("l'oral", results.firstOrNull()?.candidate)
    }
}
