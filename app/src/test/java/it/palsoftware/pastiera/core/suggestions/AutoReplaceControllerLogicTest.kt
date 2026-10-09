package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoReplaceControllerLogicTest {

    @Test
    fun testSplitApostropheWord() {
        // Italian elisions
        // "l'amico" -> prefix="l'", root="amico"
        val split1 = AutoReplaceController.splitApostropheWord("l'amico")
        assertNotNull("l'amico should be split", split1)
        assertEquals("l'", split1!!.prefix)
        assertEquals("amico", split1.root)

        // Too short (root < 3)
        val split2 = AutoReplaceController.splitApostropheWord("l'a")
        assertNull("Root too short, should be null", split2)

        // No apostrophe
        val split3 = AutoReplaceController.splitApostropheWord("hallo")
        assertNull(split3)
        
        // Typographic apostrophe
        val split4 = AutoReplaceController.splitApostropheWord("l’amico")
        assertNotNull("l’amico should be split", split4)
        assertEquals("l'", split4!!.prefix)

        val split5 = AutoReplaceController.splitApostropheWord("dell'amico")
        assertNotNull("dell'amico should be split", split5)
        assertEquals("dell'", split5!!.prefix)
        assertEquals("amico", split5.root)

        val split6 = AutoReplaceController.splitApostropheWord("nell'amico")
        assertNotNull("nell'amico should be split", split6)
        assertEquals("nell'", split6!!.prefix)
        assertEquals("amico", split6.root)

        val split7 = AutoReplaceController.splitApostropheWord("rock'nroll")
        assertNull("Arbitrary longer prefixes should not be treated as elisions", split7)
    }

    @Test
    fun testIsAccentOnlyVariant() {
        // "perche" vs "perché" -> true
        val res1 = AutoReplaceController.isAccentOnlyVariant("perche", "perché")
        assertEquals(true, res1)

        // Ligature variant should also be considered equivalent ("oeil" vs "œil")
        val resLigature = AutoReplaceController.isAccentOnlyVariant("oeil", "œil")
        assertEquals(true, resLigature)

        // "hallo" vs "halle" -> false (different letter)
        val res2 = AutoReplaceController.isAccentOnlyVariant("hallo", "halle")
        assertEquals(false, res2)
        
        // Identical -> false (per the implementation)
        val res3 = AutoReplaceController.isAccentOnlyVariant("hallo", "hallo")
        assertEquals(false, res3)
    }

    @Test
    fun testStripAccents() {
        assertEquals("perche", AutoReplaceController.stripAccents("perché"))
        assertEquals("a", AutoReplaceController.stripAccents("á"))
        assertEquals("hallo", AutoReplaceController.stripAccents("hallo"))
    }

    @Test
    fun safeAutoReplaceRequiresCurrentWordEditDistanceCandidate() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "ich",
                lookupWord = "ich",
                candidate = SuggestionResult("bin", 0, 1.0, SuggestionSource.USER, SuggestionKind.NEXT_WORD),
                settings = settings,
                isOrthographicVariant = false
            )
        )

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "hal",
                lookupWord = "hal",
                candidate = SuggestionResult("hallo", 0, 1.0, SuggestionSource.MAIN, SuggestionKind.CURRENT_WORD),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceAllowsRepeatedLetterInsertionButRejectsMorphology() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            true,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "wil",
                lookupWord = "wil",
                candidate = SuggestionResult("will", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "kaputte",
                lookupWord = "kaputte",
                candidate = SuggestionResult("kaputt", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceRejectsAcronymCandidateForLowercaseInput() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "idk",
                lookupWord = "idk",
                candidate = SuggestionResult("IFK", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceRejectsProperNounTypoForLowercaseInput() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "hallo",
                lookupWord = "hallo",
                candidate = SuggestionResult("Halle", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceRejectsSameLengthFirstLetterSubstitution() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "ding",
                lookupWord = "ding",
                candidate = SuggestionResult("fing", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )

        assertEquals(
            true,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "fihg",
                lookupWord = "fihg",
                candidate = SuggestionResult("fing", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceAllowsCaseOnlyVariant() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(true, AutoReplaceController.isCaseOnlyVariant("problem", "Problem"))
        assertEquals(
            true,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "problem",
                lookupWord = "problem",
                candidate = SuggestionResult("Problem", 0, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun customAutoSubstitutionAppliesToKnownWordAndPreservesCapitalization() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, mapOf("ur" to "your"))
        AutoCorrector.loadCorrections(context.assets, context)

        val correction = AutoCorrector.processText("Ur ")

        assertNotNull(correction)
        assertEquals("Ur", correction!!.first)
        assertEquals("Your", correction.second)
    }

    @Test
    fun customCorrectionDoesNotRemoveBundledDefaults() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, mapOf("teh" to "the"))
        AutoCorrector.loadCorrections(context.assets, context)

        val correction = AutoCorrector.processText("Dont ")

        assertNotNull(correction)
        assertEquals("Dont", correction!!.first)
        assertEquals("Don't", correction.second)
    }

    @Test
    fun bundledAutoSubstitutionAppliesToKnownWords() {
        val context = RuntimeEnvironment.getApplication()
        AutoCorrector.loadCorrections(context.assets, context)

        val dontCorrection = AutoCorrector.processText("Dont ")
        val imCorrection = AutoCorrector.processText("im ")

        assertNotNull(dontCorrection)
        assertEquals("Dont", dontCorrection!!.first)
        assertEquals("Don't", dontCorrection.second)
        assertNotNull(imCorrection)
        assertEquals("im", imCorrection!!.first)
        assertEquals("I'm", imCorrection.second)
    }

    @Test
    fun exactReplacementRunsBeforeSuggestionSafetyChecks() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("da", 255)
            addTestEntry("ca", 50)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            exactReplacementProvider = { word, _ ->
                if (word == "Ca") "Ça" else null
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("Ca")
        val inputConnection = FakeInputConnection(context, "Ca")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("Ça ", inputConnection.text)
        assertEquals("Ça", result.replacement)
    }

    @Test
    fun exactReplacementRunsEvenWhenFuzzyAutoReplaceIsDisabled() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    maxAutoReplaceDistance = 1
                )
            },
            exactReplacementProvider = { word, _ ->
                if (word == "tssst") "totallynewsubstitution" else null
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("tssst")
        val inputConnection = FakeInputConnection(context, "tssst")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("totallynewsubstitution ", inputConnection.text)
        assertEquals("totallynewsubstitution", result.replacement)
    }

    @Test
    fun exactReplacementSupportsApostropheWords() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    maxAutoReplaceDistance = 1
                )
            },
            exactReplacementProvider = { word, _ ->
                if (word == "qu'on") "qu'on" else null
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("qu'on")
        val inputConnection = FakeInputConnection(context, "qu'on")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("qu'on ", inputConnection.text)
        assertEquals("qu'on", result.replacement)
    }

    @Test
    fun autoReplaceOnSpaceAppliesAccentOnlyDictionaryVariant() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("derrière", 200)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.FRENCH),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    accentMatching = true,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("derriere")
        val inputConnection = FakeInputConnection(context, "derriere")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("derrière ", inputConnection.text)
        assertEquals("derrière", result.replacement)
    }

    @Test
    fun boundaryCharOverrideAutoReplacesBeforeEveryRequestedPunctuation() {
        val context = RuntimeEnvironment.getApplication()
        val punctuation = listOf('?', '!', ':', ',', '.', ';')

        punctuation.forEach { boundary ->
            val repository = FakeDictionaryRepository().apply {
                isReady = true
                addTestEntry("hello", 200)
            }
            val controller = AutoReplaceController(
                repository = repository,
                suggestionEngine = SuggestionEngine(repository),
                settingsProvider = {
                    SuggestionSettings(
                        autoReplaceOnSpaceEnter = true,
                        maxAutoReplaceDistance = 1
                    )
                }
            )
            val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
            tracker.setWord("hellp")
            val inputConnection = FakeInputConnection(context, "hellp")

            val result = controller.handleBoundary(
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                event = null,
                tracker = tracker,
                inputConnection = inputConnection,
                boundaryCharOverride = boundary
            )

            assertTrue("Expected replacement before '$boundary'", result.replaced)
            assertEquals("hello$boundary", inputConnection.text)
            assertEquals("hello", result.replacement)
        }
    }

    @Test
    fun boundaryCommitAddsCommaSpaceWhenNoReplacementHappens() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("hello", 100)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    commaSpace = true
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("hello")
        val inputConnection = FakeInputConnection(context, "hello")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertFalse(result.replaced)
        assertTrue(result.committed)
        assertEquals("hello, ", inputConnection.text)
    }

    @Test
    fun softwareSpaceBoundaryReportsCommittedWhenNoReplacementHappens() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("hello")
        val inputConnection = FakeInputConnection(context, "hello")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = null,
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertTrue(result.committed)
        assertEquals("hello ", inputConnection.text)
    }

    @Test
    fun exactReplacementAllowsTwoLetterCustomSubstitutionWithApostropheBeforeDictionaryCaseCandidate() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, mapOf("ct" to "can't"))
        AutoCorrector.loadCorrections(context.assets, context)
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("CT", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            knownWordProvider = { word -> word.equals("ct", ignoreCase = true) },
            exactReplacementProvider = { word, boundaryChar ->
                AutoCorrector.processText(word + (boundaryChar ?: ' '))?.takeIf { (original, replacement) ->
                    original == word && replacement != word
                }?.second
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("Ct")
        val inputConnection = FakeInputConnection(context, "Ct")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("Can't ", inputConnection.text)
        assertEquals("Can't", result.replacement)
    }

    @Test
    fun activeDictionaryKnownWordProviderPreventsFuzzyReplacement() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("trat", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            knownWordProvider = { word -> word.equals("that", ignoreCase = true) }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("that")
        val inputConnection = FakeInputConnection(context, "that")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertEquals("that ", inputConnection.text)
    }

    @Test
    fun autoReplaceDoesNotCommitWhenFinalReplacementEqualsOriginalWord() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("und", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("Und")
        val inputConnection = FakeInputConnection(context, "Und")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertEquals("Und ", inputConnection.text)
    }

    @Test
    fun primaryDictionaryCaseVariantRunsBeforeMultiDictionaryKnownWordGuard() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("Problem", 220)
            addTestEntry("problemlos", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            knownWordProvider = { word -> word.equals("problem", ignoreCase = true) }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("problem")
        val inputConnection = FakeInputConnection(context, "problem")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("Problem ", inputConnection.text)
        assertEquals("Problem", result.replacement)
    }

    @Test
    fun primaryDictionaryCaseVariantDoesNotReplaceWhenExactLowercaseExists() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("und", 255)
            addTestEntry("Und", 200)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("und")
        val inputConnection = FakeInputConnection(context, "und")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertEquals("und ", inputConnection.text)
    }

    private fun typeWordAndSpace(
        word: String,
        aggressive: Boolean,
        vararg dictionary: Pair<String, Int>,
        before: String = ""
    ): String {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            dictionary.forEach { (entry, frequency) -> addTestEntry(entry, frequency) }
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1,
                    aggressiveAutoReplace = aggressive
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord(word)
        val inputConnection = FakeInputConnection(context, before + word)
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )
        return inputConnection.text.removePrefix(before)
    }

    @Test
    fun aStrayKeyAfterTheLastLetterIsASlipNotAnEnding() {
        val words = arrayOf("will" to 165, "Willi" to 69, "email" to 150)
        assertEquals("will ", typeWordAndSpace("willk", aggressive = true, *words))
        // A real ending letter is not dropped: "emails" stays.
        assertEquals("emails ", typeWordAndSpace("emails", aggressive = true, *words))
    }

    @Test
    fun missedFirstAndLastLettersOfNonWordsAreFixed() {
        val words = arrayOf("mother" to 160, "subject" to 150, "ore" to 80, "more" to 190)
        assertEquals("mother ", typeWordAndSpace("mothe", aggressive = true, *words))
        assertEquals("subject ", typeWordAndSpace("ubject", aggressive = true, *words))
        // Short words lose too much of their shape: "ore" is left alone.
        assertEquals("ore ", typeWordAndSpace("ore", aggressive = true, *words))
    }

    @Test
    fun twoLetterSlipsBecomeTheClearlyMeantWord() {
        val words = arrayOf("is" to 210, "as" to 200, "his" to 190, "has" to 182, "he" to 200, "the" to 222, "bf" to 100)
        assertEquals("is ", typeWordAndSpace("si", aggressive = true, *words))
        assertEquals("the ", typeWordAndSpace("th", aggressive = true, *words))
        // Too close to call, or a texting abbreviation: left alone.
        assertEquals("hs ", typeWordAndSpace("hs", aggressive = true, *words))
        assertEquals("bf ", typeWordAndSpace("bf", aggressive = true, *words))
        assertEquals("si ", typeWordAndSpace("si", aggressive = false, *words))
    }

    @Test
    fun namesAreLeftAloneButSentenceStartSlipsAreFixed() {
        val words = arrayOf("swami" to 69, "Roma" to 97, "thanks" to 180)
        assertEquals("Sami ", typeWordAndSpace("Sami", aggressive = true, *words, before = "I met "))
        assertEquals("Sami ", typeWordAndSpace("Sami", aggressive = true, *words))
        assertEquals("Rima ", typeWordAndSpace("Rima", aggressive = true, *words))
        assertEquals("Thanks ", typeWordAndSpace("Thnaks", aggressive = true, *words))
        assertEquals("Thanks ", typeWordAndSpace("Thnaks", aggressive = true, *words, before = "Ok. "))
    }

    @Test
    fun aLowercaseSlipBecomesARareNameOnlyByAClassicSlip() {
        val words = arrayOf("Derek" to 100, "Friday" to 113, "Oman" to 88, "plan" to 170)
        assertEquals("Friday ", typeWordAndSpace("frday", aggressive = true, *words))
        assertEquals("plan ", typeWordAndSpace("olan", aggressive = true, *words))
    }

    @Test
    fun contractionsAndPossessivesOfKnownWordsAreMeant() {
        val words = arrayOf("who" to 200, "whore" to 50, "waiter" to 100, "waiters" to 90, "where" to 200, "whereas" to 120)
        assertEquals("who're ", typeWordAndSpace("who're", aggressive = true, *words))
        assertEquals("waiter's ", typeWordAndSpace("waiter's", aggressive = true, *words))
        assertEquals("Where's ", typeWordAndSpace("Where's", aggressive = true, *words))
        assertTrue(AutoReplaceController.isWordWithClitic("Sami's", { false }, rootMayBeName = true))
        assertFalse(AutoReplaceController.isWordWithClitic("Freind's", { false }, rootMayBeName = false))
        assertFalse(AutoReplaceController.isWordWithClitic("tehy're", { it == "they" }))
    }

    @Test
    fun aMissedSpaceBeatsAWeakOrRareOneWordFix() {
        val words = arrayOf("can" to 200, "you" to 215, "canyon" to 90, "was" to 201, "the" to 222, "waste" to 120)
        assertEquals("Can you ", typeWordAndSpace("Canyou", aggressive = true, *words))
        assertEquals("was the ", typeWordAndSpace("wasthe", aggressive = true, *words))
    }

    @Test
    fun aggressiveAutoReplaceFixesMissingAndExtraLetters() {
        assertEquals("world ", typeWordAndSpace("wrld", aggressive = true, "world" to 150, "would" to 500))
        assertEquals("the ", typeWordAndSpace("thhe", aggressive = true, "the" to 1000))
        assertEquals("thanks ", typeWordAndSpace("thnks", aggressive = true, "thanks" to 300))
        // Default mode keeps its conservative behaviour for the same inputs.
        assertEquals("wrld ", typeWordAndSpace("wrld", aggressive = false, "world" to 150, "would" to 500))
        assertEquals("thhe ", typeWordAndSpace("thhe", aggressive = false, "the" to 1000))
    }

    @Test
    fun aggressiveAutoReplaceFixesTwoTyposOnlyInLongWords() {
        assertEquals("tomorrow ", typeWordAndSpace("tommorow", aggressive = true, "tomorrow" to 300))
        assertEquals(1, AutoReplaceController.effectiveMaxDistance("wrld", SuggestionSettings(aggressiveAutoReplace = true)))
        assertEquals(2, AutoReplaceController.effectiveMaxDistance("tommorow", SuggestionSettings(aggressiveAutoReplace = true)))
        // A maxed-out slider must not let aggressive mode rewrite short words.
        assertEquals(
            1,
            AutoReplaceController.effectiveMaxDistance(
                "wrld",
                SuggestionSettings(aggressiveAutoReplace = true, maxAutoReplaceDistance = 3)
            )
        )
        assertEquals(
            0,
            AutoReplaceController.effectiveMaxDistance(
                "tommorow",
                SuggestionSettings(aggressiveAutoReplace = true, maxAutoReplaceDistance = 0)
            )
        )
    }

    @Test
    fun aggressiveAutoReplaceKeepsWordEndingsAndUnknownLongWords() {
        // Plural/inflection endings are morphology, not typos.
        assertEquals("emails ", typeWordAndSpace("emails", aggressive = true, "email" to 500))
        assertEquals("emojis ", typeWordAndSpace("emojis", aggressive = true, "emoji" to 500))
        // A doubled last letter is still a typo.
        assertEquals("help ", typeWordAndSpace("helpp", aggressive = true, "help" to 500))
        // Two edits must keep the word length.
        assertEquals("config ", typeWordAndSpace("config", aggressive = true, "confirm" to 500))
    }

    @Test
    fun aggressiveUndoLearnsOnlyWordsKeptTwice() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("world", 300)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = {
                SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true)
            }
        )
        fun space(word: String, inputConnection: FakeInputConnection) {
            controller.handleBoundary(
                keyCode = KeyEvent.KEYCODE_SPACE,
                event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
                tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord(word) },
                inputConnection = inputConnection
            )
        }
        fun typeWrld() = FakeInputConnection(context, "wrld").also { space("wrld", it) }

        // Undo alone learns nothing; the user may be deleting the word.
        val first = typeWrld()
        assertEquals("world ", first.text)
        assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, first))
        assertEquals("wrld", first.text)
        // ...the user keeps deleting and types something else instead.
        space("hello", FakeInputConnection(context, "hello"))
        assertEquals("world ", typeWrld().text)

        // Undo + keep once: the word stays as typed but is not saved yet.
        val second = typeWrld()
        assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, second))
        space("wrld", second)
        assertEquals("wrld ", second.text)
        assertEquals(0, repository.getExactWordFrequency("wrld"))
    }

    @Test
    fun aggressiveUndoSavesWordKeptOnTwoOccasions() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("world", 300)
        }
        val keptOnce = KeptOnceStore.inMemory()
        // Each controller is a separate session (IME restart) sharing the persisted kept-once store.
        fun session() = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = {
                SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true)
            },
            keptOnceStore = keptOnce
        )
        fun AutoReplaceController.space(word: String, inputConnection: FakeInputConnection) {
            handleBoundary(
                keyCode = KeyEvent.KEYCODE_SPACE,
                event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
                tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord(word) },
                inputConnection = inputConnection
            )
        }
        fun AutoReplaceController.undoAndKeep() {
            val field = FakeInputConnection(context, "wrld").also { space("wrld", it) }
            assertEquals("world ", field.text)
            assertTrue(handleBackspaceUndo(KeyEvent.KEYCODE_DEL, field))
            space("wrld", field)
            assertEquals("wrld ", field.text)
        }

        val first = session()
        first.undoAndKeep()
        assertEquals(0, repository.getExactWordFrequency("wrld"))
        // Same session: left alone, and typing it again does not save it.
        assertEquals("wrld ", FakeInputConnection(context, "wrld").also { first.space("wrld", it) }.text)
        assertEquals(0, repository.getExactWordFrequency("wrld"))

        // Later session: corrected again until the user rejects it a second time; then it is saved.
        session().undoAndKeep()
        assertTrue(repository.getExactWordFrequency("wrld") > 0)
        assertEquals("wrld ", FakeInputConnection(context, "wrld").also { session().space("wrld", it) }.text)
    }

    @Test
    fun wordKeptAtSentenceStartIsLearnedLowercase() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("world", 300)
        }
        val keptOnce = KeptOnceStore.inMemory()
        val learned = mutableListOf<String>()
        fun session() = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = {
                SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true)
            },
            keptOnceStore = keptOnce,
            learnWord = { learned += it }
        )
        fun AutoReplaceController.space(word: String, inputConnection: FakeInputConnection) {
            handleBoundary(
                keyCode = KeyEvent.KEYCODE_SPACE,
                event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
                tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord(word) },
                inputConnection = inputConnection
            )
        }
        // "Wrld" at the start of the text, undone and kept on two occasions.
        repeat(2) {
            val controller = session()
            val field = FakeInputConnection(context, "Wrld").also { controller.space("Wrld", it) }
            assertEquals("World ", field.text)
            assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, field))
            controller.space("Wrld", field)
        }
        // Saved lowercase, without the sentence-start capital.
        assertEquals(listOf("wrld"), learned)
    }

    @Test
    fun aggressiveUndoThenDeletingTheWordLearnsNothing() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("world", 300)
            addTestEntry("hello", 300)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = {
                SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true)
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord("wrld") }
        val inputConnection = FakeInputConnection(context, "wrld")
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )
        assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, inputConnection))
        // The user keeps deleting and types a different word instead.
        tracker.setWord("hello")
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = FakeInputConnection(context, "hello")
        )
        assertEquals(0, repository.getExactWordFrequency("wrld"))
    }

    @Test
    fun aggressiveAutoReplaceSplitsMissedSpaces() {
        val words = arrayOf("hello" to 120, "how" to 149, "of" to 214, "the" to 222, "user" to 132, "name" to 166, "username" to 77, "ow" to 20)
        assertEquals("hello how ", typeWordAndSpace("hellohow", aggressive = true, *words))
        assertEquals("Hello how ", typeWordAndSpace("Hellohow", aggressive = true, *words))
        assertEquals("of the ", typeWordAndSpace("ofthe", aggressive = true, *words))
        // Known compound words and rare fragments are never split; default mode never splits.
        assertEquals("username ", typeWordAndSpace("username", aggressive = true, *words))
        assertNull(AutoReplaceController.findWordSplit("hellow", { mapOf("hell" to 110, "ow" to 20)[it] ?: 0 }))
        assertEquals("hellohow ", typeWordAndSpace("hellohow", aggressive = false, *words))
    }

    @Test
    fun capitalizedWordsMidSentenceAreNamesNotMissedSpaces() {
        fun isName(word: String, before: String) = AutoReplaceController.isCapitalizedName(word, before)
        assertTrue(isName("Dropbox", "saved in "))
        assertTrue(isName("Seaton", "I live in "))
        // A capital that only marks the start of a sentence still allows the split.
        assertFalse(isName("Hellohow", ""))
        assertFalse(isName("Hellohow", "Yes. "))
        assertFalse(isName("Hellohow", "Hi!\n"))
        assertFalse(isName("hellohow", "said "))
    }

    @Test
    fun staleTrackerWordNeverDeletesOtherText() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("the", 1000)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = { SuggestionSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1) }
        )
        // Cursor at the start of a line: the tracker still holds "teh" from after the cursor.
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord("teh") }
        val inputConnection = FakeInputConnection(context, "Buy milk\n")
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )
        assertEquals("Buy milk\n ", inputConnection.text)
    }

    @Test
    fun backspaceUndoOnlyMatchesTheWholeCorrectedWord() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("the", 1000)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = { SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true) }
        )
        val inputConnection = FakeInputConnection(context, "thhe")
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord("thhe") },
            inputConnection = inputConnection
        )
        assertEquals("the ", inputConnection.text)
        // The user typed on, even if the undo window is still open...
        inputConnection.commitText("bathe", 1)
        assertFalse(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, inputConnection))
        assertEquals("the bathe", inputConnection.text)
    }

    @Test
    fun backspaceUndoesAFixEndedByPunctuationAndKeepsThePunctuation() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("world", 300)
        }
        val keptOnce = KeptOnceStore.inMemory()
        val learned = mutableListOf<String>()
        fun session() = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = { SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true) },
            keptOnceStore = keptOnce,
            learnWord = { learned += it }
        )
        fun AutoReplaceController.type(word: String, mark: Char): FakeInputConnection =
            FakeInputConnection(context, "hello $word").also { field ->
                handleBoundary(
                    keyCode = KeyEvent.KEYCODE_UNKNOWN,
                    event = null,
                    tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {}).apply { setWord(word) },
                    inputConnection = field,
                    boundaryCharOverride = mark
                )
            }
        // A user report: "backplate!" and "autocorrects," could not be undone.
        for (mark in listOf('!', ',', '?', ':')) {
            val controller = session()
            val field = controller.type("wrld", mark)
            assertEquals("hello world$mark", field.text)
            assertTrue(controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, field))
            assertEquals("hello wrld$mark", field.text)
        }
        // Undoing ends the word as it stands, so it is learned like any word kept twice (here
        // the fake dictionary never learns it, so four undos learn it twice).
        assertEquals(listOf("wrld", "wrld"), learned)
    }

    @Test
    fun aKnownWordPlusSIsMeantButNotAfterSOrAConsonantY() {
        val known = setOf("autocorrect", "keycap", "word", "story", "again")
        val frequency = mapOf("again" to 156, "word" to 150)
        fun plural(word: String) = AutoReplaceController.isPluralOfKnownWord(word, { it in known }, { frequency[it] ?: 0 })
        // A user report: "autocorrects" became "autocorrect".
        assertTrue(plural("autocorrects"))
        assertTrue(plural("keycaps"))
        assertFalse(plural("wordss"))
        assertFalse(plural("storys"))
        // Plurals of very common words are in the dictionary already: "agains" is a slip.
        assertFalse(plural("agains"))
        assertFalse(plural("cats"))
    }

    @Test
    fun allCapsWordsStayAllCaps() {
        assertEquals("MONDAY", CasingHelper.applyCasing("Monday", "MONDY"))
        assertEquals("Monday", CasingHelper.applyCasing("Monday", "mondy"))
    }

    @Test
    fun keptOnceNotesExpireAfterThirtyDays() {
        var day = 1000L
        val store = KeptOnceStore(null, today = { day })
        store.add("Wrld")
        assertTrue(store.contains("wrld"))
        day += 30
        assertTrue(store.contains("wrld"))
        day += 1
        assertFalse(store.contains("wrld"))
    }

    @Test
    fun emailAndLinkWordsAreNeverCorrected() {
        assertTrue(AutoReplaceController.isInsideAddress("write to alex", '@'))
        assertTrue(AutoReplaceController.isInsideAddress("mail alex@domian", ' '))
        assertTrue(AutoReplaceController.isInsideAddress("see https://github", '.'))
        assertTrue(AutoReplaceController.isInsideAddress("open www.exmple", '.'))
        assertTrue(AutoReplaceController.isInsideAddress("site.com/wrld", ' '))
        assertFalse(AutoReplaceController.isInsideAddress("hello wrld", ' '))
        assertFalse(AutoReplaceController.isInsideAddress("see you tomrrow", '.'))

        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("meter", 300)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.ENGLISH),
            settingsProvider = { SuggestionSettings(autoReplaceOnSpaceEnter = true, aggressiveAutoReplace = true) }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("metre")
        val inputConnection = FakeInputConnection(context, "alex@domian")
        controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )
        assertEquals("alex@domian ", inputConnection.text)
    }

    @Test
    fun aggressiveAutoReplaceMeasuresApostropheRoot() {
        val settings = SuggestionSettings(aggressiveAutoReplace = true)
        // "dell'amico" is 10 chars but its root "amico" is short: one edit only.
        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "dell'amxxo",
                lookupWord = "amxxo",
                candidate = SuggestionResult("dell'amico", 2, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun aggressiveAutoReplaceStillProtectsKnownWordsAndFirstLetters() {
        assertEquals("form ", typeWordAndSpace("form", aggressive = true, "form" to 100, "from" to 1000))
        // A first-letter slip is only fixed toward a common word...
        assertEquals("qorld ", typeWordAndSpace("qorld", aggressive = true, "world" to 100))
        assertEquals("world ", typeWordAndSpace("qorld", aggressive = true, "world" to 120))
        // ...and only when it is a classic slip (neighbouring key or swapped first letters).
        assertEquals("porld ", typeWordAndSpace("porld", aggressive = true, "world" to 200))
        // Names stay untouched.
        assertEquals("SwiftKey ", typeWordAndSpace("SwiftKey", aggressive = true, "swift" to 150, "key" to 150))
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return buffer.takeLast(n)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }

        override fun beginBatchEdit(): Boolean = true

        override fun endBatchEdit(): Boolean = true
    }
}
