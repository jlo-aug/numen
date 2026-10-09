package it.palsoftware.pastiera.core.suggestions

import it.palsoftware.pastiera.core.suggestions.PairCounts.Companion.SENTENCE_END
import it.palsoftware.pastiera.core.suggestions.PairCounts.Companion.SENTENCE_START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextCorrectorTest {
    private val frequencies = mapOf(
        "some" to 169, "dome" to 115, "come" to 160, "get" to 175, "food" to 144, "the" to 222,
        "tie" to 120, "they" to 174, "end" to 161, "soome" to 90, "locale" to 92, "local" to 159,
        "to" to 210, "ids" to 81, "is" to 200, "when" to 190, "tho" to 100, "though" to 170
    )
    private val neighbors = setOf("ds", "sd", "dc", "cd", "rt", "tr", "gh", "hg")
    private fun corrector(
        counts: Map<String, Int>,
        protected: Set<String> = emptySet(),
        rejected: MutableSet<String> = mutableSetOf()
    ) = ContextCorrector(
        frequencyOf = { frequencies[it] ?: 0 },
        isNeighborKey = { a, b -> "$a$b" in neighbors },
        pairCount = { a, b -> counts["$a $b"] ?: 0 },
        isProtected = { it.lowercase() in protected },
        rejected = rejected
    )

    @Test
    fun fixesARealWordSeenNowhereWhenTheSlipFitsBothNeighbours() {
        val counts = mapOf("get some" to 60, "some food" to 46)
        assertEquals("some", corrector(counts).review("get", "dome", "food"))
        assertEquals("Some", corrector(mapOf("$SENTENCE_START some" to 9, "some food" to 46)).review(SENTENCE_START, "Dome", "food"))
        assertEquals("some", corrector(mapOf("get some" to 60, "some $SENTENCE_END" to 66)).review("get", "dome", SENTENCE_END))
    }

    @Test
    fun typedWordSeenNextToEitherNeighbourStays() {
        assertNull(corrector(mapOf("get some" to 60, "some food" to 46, "get dome" to 1)).review("get", "dome", "food"))
        assertNull(corrector(mapOf("get some" to 60, "some food" to 46, "dome food" to 1)).review("get", "dome", "food"))
    }

    @Test
    fun oneSidedEvidenceIsNotEnough() {
        assertNull(corrector(mapOf("get some" to 60)).review("get", "dome", "food"))
        assertNull(corrector(mapOf("some food" to 46)).review(SENTENCE_START, "Dome", "food"))
    }

    @Test
    fun veryCommonWordsAreNeverSecondGuessed() {
        assertNull(corrector(mapOf("$SENTENCE_START the" to 1000, "the end" to 8718)).review(SENTENCE_START, "They", "end"))
    }

    @Test
    fun rejectedUnknownAndNameLikeWordsAreLeftAlone() {
        val counts = mapOf("get some" to 60, "some food" to 46)
        val rejected = mutableSetOf<String>()
        val corrector = corrector(counts, rejected = rejected)
        assertNull(corrector.review("get", "domx", "food"))
        assertNull(corrector.review("get", "DOME", "food"))
        rejected += "dome"
        assertNull(corrector.review("get", "dome", "food"))
    }

    @Test
    fun userWordsAndSlangAreNeverRewritten() {
        val counts = mapOf("get some" to 60, "some food" to 46, "good though" to 80, "though $SENTENCE_END" to 90)
        assertNull(corrector(counts, protected = setOf("dome")).review("get", "dome", "food"))
        assertNull(corrector(counts, protected = setOf("tho")).review("good", "tho", SENTENCE_END))
    }

    @Test
    fun anExtraLetterMustBeOneAFingerCouldAdd() {
        // A doubled key is a classic slip...
        assertEquals("some", corrector(mapOf("get some" to 60, "some food" to 46)).review("get", "soome", "food"))
        // ...but "e" is nowhere near "l", so "locale" stays.
        assertNull(corrector(mapOf("the local" to 2972, "local to" to 21)).review("the", "locale", "to"))
    }

    @Test
    fun capitalisedWordsMidSentenceAreNames() {
        val counts = mapOf("get some" to 60, "some food" to 46, "$SENTENCE_START some" to 9)
        assertNull(corrector(counts).review("get", "Dome", "food"))
        // At the start of a sentence a capital is just the sentence start.
        assertEquals("Some", corrector(counts).review(SENTENCE_START, "Dome", "food"))
    }

    @Test
    fun itsAndItsAreChosenByUsage() {
        val counts = mapOf(
            "its tail" to 253, "its own" to 3241, "it's own" to 16, "it's raining" to 507,
            "$SENTENCE_START its" to 9972, "$SENTENCE_START it's" to 50935, "of its" to 7755, "of it's" to 61,
            "it's a" to 8921, "its a" to 43
        )
        val corrector = corrector(counts)
        assertEquals("its", corrector.review("wagged", "it's", "tail"))
        assertEquals("its", corrector.review("wagged", "it’s", "tail"))
        assertEquals("It's", corrector.review(SENTENCE_START, "Its", "raining"))
        // Right as typed: left alone.
        assertNull(corrector.review("of", "its", "own"))
        assertNull(corrector.review(SENTENCE_START, "It's", "a"))
        // No evidence either way: left alone.
        assertNull(corrector.review("the", "its", "zebra"))
    }

    @Test
    fun buttIsButOnlyWhereTheSentenceWantsBut() {
        // Real counts from en_pairs.txt.
        val counts = mapOf(
            "sorry but" to 744, "sorry butt" to 2, "but i" to 9270, "$SENTENCE_START but" to 11296,
            "$SENTENCE_START butt" to 7, "but the" to 8254, "butt the" to 2, "her but" to 535, "her butt" to 17,
            "but is" to 1636, "butt is" to 6, "his butt" to 61, "but $SENTENCE_END" to 108,
            "butt $SENTENCE_END" to 135, "but was" to 2379, "butt was" to 2, "cigarette butt" to 17
        )
        val corrector = corrector(counts)
        assertEquals("but", corrector.review("sorry", "butt", "i"))
        assertEquals("But", corrector.review(SENTENCE_START, "Butt", "the"))
        // A noun where a noun belongs: left alone.
        assertNull(corrector.review("his", "butt", SENTENCE_END))
        assertNull(corrector.review("her", "butt", "is"))
        assertNull(corrector.review("cigarette", "butt", "was"))
        // Never the other way round.
        assertNull(corrector.review("sorry", "but", "i"))
    }

    @Test
    fun replacementsAreNeverShortFunctionWords() {
        assertNull(corrector(mapOf("$SENTENCE_START is" to 30713, "is when" to 1538)).review(SENTENCE_START, "ids", "when"))
    }

    @Test
    fun findsTheWordsAroundTheBoundaryJustTyped() {
        fun find(text: String, truncated: Boolean = false) = ContextCorrector.findTrailing(text, truncated)
        assertEquals(ContextCorrector.Trailing("get", "dome", "food", " ", "Let's"), find("Let's get dome food "))
        assertEquals(ContextCorrector.Trailing("Let's", "dome", "food", ". "), find("Let’s dome food. "))
        assertEquals(ContextCorrector.Trailing(SENTENCE_START, "Dome", "food", " "), find("Dome food "))
        assertEquals(ContextCorrector.Trailing(SENTENCE_START, "Dome", "food", ", "), find("I ate. Dome food, "))
        assertEquals(ContextCorrector.Trailing(SENTENCE_START, "dome", "food", " "), find("Hi\ndome food "))
        assertEquals(ContextCorrector.Trailing("wagged", "it's", "tail", " ", "dog"), find("The dog wagged it's tail "))
    }

    @Test
    fun anythingUnclearIsNotReviewed() {
        fun find(text: String, truncated: Boolean = false) = ContextCorrector.findTrailing(text, truncated)
        assertNull(find("mail alex@dome food "))
        assertNull(find("see example.com/dome food "))
        assertNull(find("get dome  food "))
        assertNull(find("get dome food2 "))
        assertNull(find("dome food bar"))
        assertNull(find("get dome food\n"))
        assertNull(find("Yes, dome food "))
        assertNull(find("Day 3 dome food "))
        // Text cut off by the editor: the first word may be a fragment.
        assertNull(find("ey end ", truncated = true))
        assertNull(find("hey end up ", truncated = true))
    }

    @Test
    fun aRealWordTypedForAnotherIsFixedOnlyWhenTheNeighboursClearlyAgree() {
        val confusions = ConfusionTable.parse(sequenceOf("he\tthe\t-1.7\t1000\t5000"))
        val pairs = mapOf("is the" to 900, "the best" to 800, "is he" to 40, "he best" to 0, "said he" to 500, "he was" to 900)
        val corrector = ContextCorrector(
            frequencyOf = { 100 },
            isNeighborKey = { _, _ -> false },
            pairCount = { a, b -> pairs["$a $b"] ?: 0 },
            confusions = confusions
        )
        assertEquals("the", corrector.review("is", "he", "best"))
        assertNull(corrector.review("said", "he", "was"))
        // Capitals and the end of a sentence say too little.
        assertNull(corrector.review("is", "He", "best"))
        assertNull(corrector.review("is", "he", PairCounts.SENTENCE_END))
    }

    @Test
    fun trailingWordsIncludeTheWordTwoBack() {
        val trailing = requireNotNull(ContextCorrector.findTrailing("I think he best ", truncated = false))
        assertEquals("he", trailing.word)
        assertEquals("think", trailing.before)
        assertEquals("i", trailing.beforePrevious?.lowercase())
    }

    @Test
    fun aLoneLetterBecomesTheShortWordItsNeighboursClearlyWant() {
        val pairs = mapOf("going to" to 900, "to the" to 800, "going on" to 5, "on the" to 600, "so tired" to 300, "am so" to 200)
        val corrector = ContextCorrector(
            frequencyOf = { 0 },
            isNeighborKey = { _, _ -> false },
            pairCount = { a, b -> pairs["$a $b"] ?: 0 },
            shortWords = mapOf("to" to 5000, "on" to 3000, "so" to 2000, "of" to 6000)
        )
        assertEquals("to", corrector.review("going", "o", "the"))
        assertEquals("so", corrector.review("am", "o", "tired"))
        // Texting shorthand stays.
        assertNull(corrector.review("love", "u", "too"))
        // No clear winner: left alone.
        assertNull(corrector.review("the", "o", "cat"))
    }
}
