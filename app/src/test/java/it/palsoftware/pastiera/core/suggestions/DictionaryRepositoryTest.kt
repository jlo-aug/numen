package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.math.pow
import org.mockito.Mockito.mock

class DictionaryRepositoryTest {

    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var userDictionaryStore: UserDictionaryStore
    private lateinit var repository: AndroidDictionaryRepository

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        assets = mock(AssetManager::class.java)
        userDictionaryStore = mock(UserDictionaryStore::class.java)
        repository = AndroidDictionaryRepository(
            context = context,
            assets = assets,
            userDictionaryStore = userDictionaryStore,
            baseLocale = Locale.ITALIAN
        )
        // Set isReady to true to bypass load checks in methods
        repository.isReady = true
    }

    @Test
    fun testFrequencyScaling() {
        // Test values based on pow(0.75) * 1600.0
        // freq 0 -> (0/255)^0.75 * 1600 = 0 -> coerced to 1
        // freq 255 -> (255/255)^0.75 * 1600 = 1600
        // freq 127 -> (127/255)^0.75 * 1600 approx 951
        
        val entry0 = DictionaryEntry("test", 0)
        val entry127 = DictionaryEntry("test", 127)
        val entry255 = DictionaryEntry("test", 255)

        assertEquals(1, repository.effectiveFrequency(entry0))
        assertEquals(1600, repository.effectiveFrequency(entry255))
        
        val expected127 = ((127.0 / 255.0).pow(0.75) * 1600.0).toInt()
        assertEquals(expected127, repository.effectiveFrequency(entry127))
    }

    @Test
    fun learningAWordOnlyTouchesItsOwnListsAndNeverEditsThemInPlace() {
        repository.index(
            listOf(
                DictionaryEntry("apple", 200, SuggestionSource.MAIN),
                DictionaryEntry("apply", 100, SuggestionSource.MAIN),
                DictionaryEntry("zebra", 100, SuggestionSource.MAIN)
            )
        )
        val aBefore = repository.lookupByPrefix("a")
        val zBefore = repository.lookupByPrefix("z")

        repository.index(listOf(DictionaryEntry("appz", 255, SuggestionSource.USER)), keepExisting = true)

        // Other letters' lists are not re-sorted, and the touched list is replaced, not edited.
        assertSame(zBefore, repository.lookupByPrefix("z"))
        assertEquals(listOf("apple", "apply"), aBefore.map { it.word })
        assertEquals(listOf("appz", "apple", "apply"), repository.lookupByPrefix("a").map { it.word })
    }

    @Test
    fun lookupsWhileWordsAreLearnedNeverFail() {
        val letters = "abcdefghijklmnopqrstuvwxyz"
        val random = java.util.Random(7)
        fun word() = (1..(4 + random.nextInt(6))).map { letters[random.nextInt(26)] }.joinToString("")
        repository.index((1..20_000).map { DictionaryEntry(word(), random.nextInt(255), SuggestionSource.MAIN) })
        val learned = (1..500).map { word() }
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val reader = Thread {
            while (running.get()) {
                try {
                    for (c in letters) repository.lookupByPrefixMerged("$c${letters[random.nextInt(26)]}", 50)
                    repository.allCandidates().size
                } catch (t: Throwable) {
                    failures += t
                }
            }
        }
        reader.start()
        learned.forEach { repository.index(listOf(DictionaryEntry(it, 1, SuggestionSource.USER)), keepExisting = true) }
        running.set(false)
        reader.join()

        assertTrue("lookups failed while learning: ${failures.take(3)}", failures.isEmpty())
        assertTrue(repository.lookupByPrefixMerged(learned.last(), 500).any { it.word == learned.last() })
    }

    @Test
    fun testPrefixCache() {
        val entries = listOf(
            DictionaryEntry("ciao", 200, SuggestionSource.MAIN),
            DictionaryEntry("casa", 150, SuggestionSource.MAIN),
            DictionaryEntry("cielo", 100, SuggestionSource.MAIN),
            DictionaryEntry("come", 50, SuggestionSource.MAIN)
        )
        
        repository.index(entries)
        
        // "ci" should find "ciao" and "cielo"
        val resultsCi = repository.lookupByPrefixMerged("ci", 10)
        // resultsCi should contain ciao and cielo, but might also contain casa and come if they are in the 'c' bucket
        // and lookupByPrefixMerged iterates down to length 1.
        assertTrue(resultsCi.any { it.word == "ciao" })
        assertTrue(resultsCi.any { it.word == "cielo" })
        
        // "ca" should find "casa"
        val resultsCa = repository.lookupByPrefixMerged("ca", 10)
        assertTrue(resultsCa.any { it.word == "casa" })

        // "c" should find all 
        val resultsC = repository.lookupByPrefixMerged("c", 10)
        assertEquals(4, resultsC.size)
    }

    @Test
    fun testMergeLogic() {
        // Scenario: A word is in both Main and User dictionary.
        // User dictionary entry has higher frequency.
        val mainEntry = DictionaryEntry("test", 100, SuggestionSource.MAIN)
        val userEntry = DictionaryEntry("test", 200, SuggestionSource.USER)
        
        // Indexing both. The order of indexing matters for bucket.removeAll
        repository.index(listOf(mainEntry, userEntry), keepExisting = true)
        
        // normalizedIndex["test"] should contain both entries
        val normalized = repository.normalize("test")
        // We can't access private members directly easily, but we can use bestEntryForNormalized
        val best = repository.bestEntryForNormalized(normalized)
        
        assertEquals("test", best?.word)
        assertEquals(SuggestionSource.USER, best?.source)
        assertEquals(200, best?.frequency)
        
        // Test with Main having higher frequency
        val userEntryLow = DictionaryEntry("hello", 50, SuggestionSource.USER)
        val mainEntryHigh = DictionaryEntry("hello", 150, SuggestionSource.MAIN)
        
        repository.index(listOf(userEntryLow, mainEntryHigh), keepExisting = true)
        val bestHello = repository.bestEntryForNormalized(repository.normalize("hello"))
        assertEquals(SuggestionSource.MAIN, bestHello?.source)
        assertEquals(150, bestHello?.frequency)
    }
}
