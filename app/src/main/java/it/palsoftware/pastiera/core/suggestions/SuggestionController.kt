package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.inputmethod.DebugCaptureStore
import it.palsoftware.pastiera.inputmethod.NotificationHelper

class SuggestionController(
    context: Context,
    private val assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    private val onSuggestionsUpdated: (List<SuggestionResult>) -> Unit,
    private val currentLocale: Locale = Locale.US,
    private val dictionaryRepositoryFactory: ((
        Context,
        AssetManager,
        UserDictionaryStore,
        Locale,
        Boolean
    ) -> DictionaryRepository)? = null,
    nextWordPredictorOverride: NextWordPredictor? = null,
    /** Home-screen typing stats; null in tests. */
    private val typingStats: TypingStats? = null,
    /** False for passwords, emails, addresses and incognito fields. */
    private val isStatsAllowed: () -> Boolean = { false },
    /** False in fields that ask the keyboard not to learn, such as incognito tabs. */
    private val isLearningAllowed: () -> Boolean = { true }
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val userDictionaryStore = UserDictionaryStore()
    private val dictionaryRepositoryCache = mutableMapOf<String, DictionaryRepository>()
    private var dictionaryRepository: DictionaryRepository = createDictionaryRepository(currentLocale)
    private var suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging)
    private var tracker = CurrentWordTracker(
        onWordChanged = { word ->
            updateSuggestionsForWord(word)
        },
        onWordReset = {
            cancelPendingWordSuggestions()
            latestSuggestions.set(emptyList())
            pendingAddUserWord = null
            suggestionsListener?.invoke(emptyList())
        },
        autoSpacePunctuationProvider = { settingsProvider().autoSpacePunctuation }
    )
    /** Words written in this conversation; never saved. Declared before the controller that reads it. */
    private val recentWords = RecentWords()
    /** Context x Titan 2 Elite error model; reads the current language's dictionary and word data. */
    private val noisyChannel: NoisyChannel by lazy {
        NoisyChannel(
            candidatesOf = { typed ->
                // Words written in lowercase; names and places ("Friday") are left to the older fixes.
                dictionaryRepository.symSpellLookup(typed, NOISY_CHANNEL_CANDIDATES)
                    .mapNotNull { dictionaryRepository.bestEntryForNormalized(it.term)?.word }
                    .filter { word ->
                        (word.all { it in 'a'..'z' } || NoisyChannel.isUnambiguousContraction(word)) &&
                            dictionaryRepository.getExactWordFrequency(word) >= NOISY_CHANNEL_MIN_FREQUENCY
                    }
            },
            isNeighborKey = { a, b -> suggestionEngine.isNeighborKey(a, b) },
            pairCount = { first, second ->
                contextState.get()?.takeIf { it.language == currentLocale.language }?.pairs?.count(first, second) ?: 0
            },
            tripleCount = { first, second, third ->
                contextState.get()?.takeIf { it.language == currentLocale.language }?.triples?.count(first, second, third) ?: 0
            }
        )
    }
    private var autoReplaceController = createAutoReplaceController()
    private val nextWordPredictor = nextWordPredictorOverride ?: NextWordPredictor(UserNGramStore(appContext))

    private fun createDictionaryRepository(locale: Locale): DictionaryRepository {
        val cacheKey = dictionaryCacheKey(locale)
        return dictionaryRepositoryCache.getOrPut(cacheKey) {
            dictionaryRepositoryFactory?.invoke(appContext, assets, userDictionaryStore, locale, debugLogging)
                ?: AndroidDictionaryRepository(
                appContext,
                assets,
                userDictionaryStore,
                baseLocale = locale,
                debugLogging = debugLogging
            )
        }
    }

    private fun dictionaryCacheKey(locale: Locale): String {
        return locale.language
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
            ?: locale.toLanguageTag().lowercase(Locale.ROOT)
    }

    private fun createAutoReplaceController(): AutoReplaceController {
        return AutoReplaceController(
            repository = dictionaryRepository,
            suggestionEngine = suggestionEngine,
            settingsProvider = settingsProvider,
            knownWordProvider = { word -> isKnownWordInActiveDictionaries(word) },
            exactReplacementProvider = { word, boundaryChar ->
                val boundary = boundaryChar ?: ' '
                AutoCorrector.processText(word + boundary)?.takeIf { (original, replacement) ->
                    original == word && replacement != word
                }?.second
            },
            keptOnceStore = KeptOnceStore(
                appContext.getSharedPreferences(KeptOnceStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            ),
            learnWord = ::addUserWord,
            realWordReplacementAllowed = ::allowsRealWordReplacement,
            isLearningAllowed = isLearningAllowed,
            pairCount = { first, second ->
                contextState.get()?.takeIf { it.language == currentLocale.language }?.pairs?.count(first, second) ?: 0
            },
            recentWords = recentWords,
            tripleCount = { first, second, third ->
                contextState.get()?.takeIf { it.language == currentLocale.language }?.triples?.count(first, second, third) ?: 0
            },
            pairsReady = { contextState.get()?.takeIf { it.language == currentLocale.language }?.pairs != null },
            noisyChannel = noisyChannel
        )
    }
    
    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())
    // Dedicated IO scope so dictionary preload never blocks the main thread.
    private val loadScope = CoroutineScope(Dispatchers.IO)
    private val suggestionScope = CoroutineScope(Dispatchers.Default)
    private var currentLoadJob: Job? = null
    private var suggestionJob: Job? = null
    private val cursorHandler = Handler(Looper.getMainLooper())
    private var cursorRunnable: Runnable? = null
    private val cursorDebounceMs = 120L
    private var pendingAddUserWord: String? = null
    private var previousCompletedWord: String? = null
    private var pendingInitialContextConnection: InputConnection? = null
    @Volatile private var pendingPrimaryRefreshAfterLoad: Boolean = false
    @Volatile private var pendingExtraRefreshAfterLoad: Boolean = false
    @Volatile private var suggestionGeneration: Int = 0
    private var sentenceStartPending: Boolean = true

    private fun cancelPendingWordSuggestions() {
        suggestionGeneration += 1
        suggestionJob?.cancel()
        suggestionJob = null
    }

    private fun updateSuggestionsForWord(word: String) {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled) {
            cancelPendingWordSuggestions()
            pendingAddUserWord = null
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }
        if (debugLogging) {
            Log.d("PastieraIME", "trackerWordChanged='$word' len=${word.length}")
        }

        val generation = suggestionGeneration + 1
        suggestionGeneration = generation
        suggestionJob?.cancel()

        val wordSnapshot = word
        val localeSnapshot = currentLocale
        val primaryRepository = dictionaryRepository

        suggestionJob = suggestionScope.launch {
            try {
                computeSuggestions(wordSnapshot, settings, localeSnapshot, primaryRepository, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Last-resort guard: a failed lookup skips one suggestion update, never the keyboard.
                Log.e("PastieraIME", "Suggestion lookup failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun computeSuggestions(
        wordSnapshot: String,
        settings: SuggestionSettings,
        localeSnapshot: Locale,
        primaryRepository: DictionaryRepository,
        generation: Int
    ) {
        val primary = if (primaryRepository.isReady) {
            SuggestionEngine(primaryRepository, locale = localeSnapshot, debugLogging = debugLogging).suggest(
                wordSnapshot,
                settings.maxSuggestions,
                settings.accentMatching,
                settings.useKeyboardProximity,
                settings.useEditTypeRanking
            )
        } else {
            emptyList()
        }

        val next = mergeSuggestionResults(primary, emptyList(), settings.maxSuggestions, localeSnapshot)
        val pendingCandidate = addWordCandidateFor(wordSnapshot, primaryRepository)

        cursorHandler.post {
            if (generation != suggestionGeneration || tracker.currentWord != wordSnapshot) {
                return@post
            }
            pendingAddUserWord = pendingCandidate
            latestSuggestions.set(next)
            suggestionsListener?.invoke(next)
        }
    }

    private fun addWordCandidateFor(word: String?, repository: DictionaryRepository = dictionaryRepository): String? {
        val candidate = word?.trim() ?: return null
        if (candidate.isEmpty() || candidate.none { it.isLetterOrDigit() }) return null
        if (!repository.isReady) return null
        return if (repository.isKnownWord(candidate)) null else candidate
    }
    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    fun onCharacterCommitted(text: CharSequence, inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (debugLogging) {
            val caller = Throwable().stackTrace.getOrNull(1)?.let { "${it.className}#${it.methodName}:${it.lineNumber}" }
            Log.d("PastieraIME", "SuggestionController.onCharacterCommitted('$text') caller=$caller")
        }
        ensureDictionaryLoaded()

        // Normalize curly/variant apostrophes to straight for tracking and suggestions.
        val normalizedText = text
            .toString()
            .replace("'", "'")
            .replace("'", "'")
            .replace("ʼ", "'")
        
        autoReplaceController.revertDotCorrection(normalizedText, inputConnection)
        // Typing ends the chance to undo the last correction.
        clearLastReplacement()
        
        // Clear rejected words when user types a new letter (allows re-correction)
        if (normalizedText.isNotEmpty() && normalizedText.any { it.isLetterOrDigit() }) {
            autoReplaceController.clearRejectedWords()
            pendingAddUserWord = null
        }
        
        tracker.onCharacterCommitted(normalizedText)
    }

    fun refreshFromInputConnection(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        tracker.onBackspace()
    }

    fun onBoundaryKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        boundaryCharOverride: Char? = null
    ): AutoReplaceController.ReplaceResult {
        if (debugLogging) {
            Log.d(
                "PastieraIME",
                "SuggestionController.onBoundaryKey keyCode=$keyCode char=${event?.unicodeChar}"
            )
        }
        ensureDictionaryLoaded()

        // Exact text replacements do not depend on dictionary suggestions. In particular,
        // onCharacterCommitted intentionally does not track characters while experimental
        // suggestions are disabled, so the editor is the source of truth at a boundary.
        // Keep this synchronous and independent of repository readiness: waiting for an
        // asynchronous dictionary load would lose the boundary that triggered the replacement.
        if (inputConnection != null) {
            val word = extractWordAtCursor(inputConnection, includeAfterCursor = false)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word, notify = false)
                Log.d("PastieraIME", "SYNC: Synced tracker to actual word='$word' before boundary")
            } else {
                // Nothing typed before the cursor: drop any word tracked from after the cursor.
                tracker.setWord("", notify = false)
            }
        }

        val boundaryChar = boundaryCharOverride ?: boundaryCharFor(keyCode, event)
        val wordBeforeBoundary = tracker.currentWord.takeIf { it.isNotBlank() }
        val result = autoReplaceController.handleBoundary(
            keyCode,
            event,
            tracker,
            inputConnection,
            boundaryCharOverride = boundaryChar
        )
        // A missed-space split ("hello how") continues the sentence from its last word.
        var completedWord = (result.replacement ?: wordBeforeBoundary)?.trim()?.substringAfterLast(' ')
        lastContextFix = null
        val settings = settingsProvider()
        val contextAware = settings.contextAwareCorrection
        // Word pairs back sentence-aware review and the English contraction check of text
        // replacements; release them when neither is on.
        if (contextAware || settings.textReplacementsEnabled && currentLocale.language == "en") {
            contextCorrectorForLocale()
        } else {
            releaseContextCorrector()
        }
        if (result.replaced) {
            autoReplaceController.sealLastReplacement(inputConnection)
            pendingAddUserWord = addWordCandidateFor(result.replacement)
            NotificationHelper.triggerHapticFeedback(appContext)
        } else {
            pendingAddUserWord = null
            // Only when the finished word was left alone, so two edits never stack. Not on Enter,
            // which apps often act on before an edit could land.
            if (contextAware && inputConnection != null && boundaryChar != '\n') {
                reviewInContext(inputConnection, completedWord)?.let { fix ->
                    // Learn from the corrected sentence from here on.
                    if (fix.reviewedLastWord) {
                        completedWord = fix.fix
                    } else if (previousCompletedWord.equals(fix.word, ignoreCase = true)) {
                        previousCompletedWord = fix.fix
                    }
                }
            }
        }
        lastBoundaryWord = completedWord
        lastAutoFix = result.replacement?.takeIf { result.replaced && ' ' !in it.trim() }
            ?.let { replacement -> wordBeforeBoundary?.let { it to replacement.trim() } }
        if (isLearningAllowed()) completedWord?.let(recentWords::record)
        if (typingStats != null && isStatsAllowed()) {
            completedWord?.let(typingStats::onWord)
            if (result.replaced) typingStats.onTypoFixed()
            if (lastContextFix != null) typingStats.onTypoFixed()
        }
        handleCompletedWordBoundary(completedWord, boundaryChar)
        return result
    }

    /**
     * A sentence-aware fix, kept until the next key so Backspace can undo it: [replaced] becomes
     * [original] again if the text still ends with [contextAfter].
     */
    private data class ContextFix(
        val word: String,
        val fix: String,
        val original: String,
        val replaced: String,
        val reviewedLastWord: Boolean,
        val contextAfter: String? = null
    )

    private var lastContextFix: ContextFix? = null
    /** The word finished at the previous boundary. */
    private var lastBoundaryWord: String? = null
    /** The last word autocorrect replaced, as (typed, replacement), until the next word ends. */
    private var lastAutoFix: Pair<String, String>? = null
    /** Words whose fix the user undid; kept across language switches. */
    private val contextRejected = mutableSetOf<String>()

    /** The sentence-aware corrector of one language: null while loading or with no pair counts. */
    private class ContextState(
        val language: String,
        val corrector: ContextCorrector? = null,
        val pairs: PairCounts? = null,
        val triples: TripleCounts? = null
    )

    private val contextState = AtomicReference<ContextState?>(null)
    private var contextLoadJob: Job? = null

    /**
     * Loads the language's pair counts once in the background. A load is dropped if the language
     * or setting changed meanwhile.
     */
    private fun contextCorrectorForLocale(): ContextCorrector? {
        val language = currentLocale.language
        contextState.get()?.let { state -> if (state.language == language) return state.corrector }
        val requested = ContextState(language)
        contextState.set(requested)
        contextLoadJob?.cancel() // Avoid two large parses after a quick language switch.
        contextLoadJob = loadScope.launch {
            val pairs = try {
                appContext.assets.open("common/dictionaries/${language}_pairs.txt")
                    .bufferedReader().useLines { lines -> PairCounts.parse(lines.onEach { ensureActive() }) }
            } catch (_: CancellationException) {
                null
            } catch (_: java.io.IOException) {
                null // No word pairs for this language.
            } catch (error: RuntimeException) {
                Log.w("PastieraIME", "Word pairs for $language unreadable", error)
                null
            } catch (error: OutOfMemoryError) {
                Log.w("PastieraIME", "No memory for word pairs; sentence-aware review off", error)
                null
            } ?: return@launch
            // Optional: real words often typed for another one ("he" for "the").
            val confusions = try {
                appContext.assets.open("common/dictionaries/${language}_confusions.txt")
                    .bufferedReader().useLines { lines -> ConfusionTable.parse(lines) }
            } catch (_: java.io.IOException) {
                null
            } catch (error: RuntimeException) {
                if (error is CancellationException) throw error
                Log.w("PastieraIME", "Word confusions for $language unreadable", error)
                null
            }
            // Optional: common short words, for a lone letter or two-letter slip ("o tired").
            val shortWords = try {
                appContext.assets.open("common/dictionaries/${language}_short_words.txt")
                    .bufferedReader().useLines { lines ->
                        lines.filterNot { it.isBlank() || it.startsWith("#") }
                            .mapNotNull { line ->
                                val cells = line.split('\t')
                                cells.getOrNull(1)?.toIntOrNull()?.let { cells[0] to it }
                            }
                            .toMap()
                    }
            } catch (_: java.io.IOException) {
                emptyMap()
            }
            val corrector = ContextCorrector(
                frequencyOf = { word -> dictionaryRepository.getExactWordFrequency(word) },
                isNeighborKey = { a, b -> suggestionEngine.isNeighborKey(a, b) },
                pairCount = pairs::count,
                isProtected = { word ->
                    dictionaryRepository.isUserOrSlangWord(word) ||
                        word.first().isUpperCase() && dictionaryRepository.isFirstName(word) // "Amy", not "May"
                },
                // Recognised real words ("ewe") outside the main dictionary.
                isRecognized = { word -> dictionaryRepository.isRecognizedWord(word) },
                rejected = contextRejected,
                noisyChannel = noisyChannel,
                confusions = confusions,
                shortWords = shortWords,
                isNameOrAcronym = { word ->
                    dictionaryRepository.topByNormalized(word, limit = 4).any { it.word != it.word.lowercase() }
                },
                tripleCount = { first, second, third ->
                    contextState.get()?.takeIf { it.language == language }?.triples?.count(first, second, third) ?: 0
                }
            )
            // Optional: ranks autocorrect fixes by the two words before.
            val triples = try {
                appContext.assets.open("common/dictionaries/${language}_triples.txt")
                    .bufferedReader().useLines { lines -> TripleCounts.parse(lines.onEach { ensureActive() }) }
            } catch (_: java.io.IOException) {
                null
            } catch (error: RuntimeException) {
                if (error is CancellationException) throw error
                Log.w("PastieraIME", "Word triples for $language unreadable", error)
                null
            } catch (error: OutOfMemoryError) {
                Log.w("PastieraIME", "No memory for word triples", error)
                null
            }
            contextState.compareAndSet(requested, ContextState(language, corrector, pairs, triples))
        }
        return null
    }

    /**
     * Whether a text replacement may rewrite a real word ("ill" -> "I'll"). Word-pair counts for
     * the previous word decide; without them only rare words are rewritten.
     */
    private fun allowsRealWordReplacement(previous: String?, word: String, replacement: String): Boolean {
        // Only English contractions are checked; other replacements keep Pastiera's behaviour.
        if (currentLocale.language != "en" || !isApostropheContraction(word, replacement)) return true
        val pairs = contextState.get()?.pairs?.takeIf { contextState.get()?.language == currentLocale.language }
        if (previous != null && pairs != null) {
            val forReplacement = pairs.count(previous, replacement.lowercase())
            val forWord = pairs.count(previous, word.lowercase())
            if (forReplacement >= MIN_REPLACEMENT_EVIDENCE && forReplacement > forWord * REPLACEMENT_EVIDENCE_RATIO) return true
            if (forReplacement > 0 || forWord > 0) return false
        }
        return dictionaryRepository.getExactWordFrequency(word) < COMMON_REAL_WORD_FREQUENCY
    }

    private fun isApostropheContraction(word: String, replacement: String): Boolean =
        replacement.any { it == '\'' || it == '’' } &&
            replacement.filter { it != '\'' && it != '’' }.equals(word, ignoreCase = true)

    /** Frees the pair counts (a few MB) while nothing uses them. */
    private fun releaseContextCorrector() {
        contextLoadJob?.cancel()
        if (contextState.get() != null) contextState.set(null)
    }

    /**
     * Re-checks the two words just typed now that their neighbours are known (see
     * ContextCorrector). Any doubt or failure skips the review.
     */
    private fun reviewInContext(inputConnection: InputConnection, completedWord: String?): ContextFix? {
        val previousWord = lastBoundaryWord ?: return null
        if (completedWord.isNullOrBlank() || !dictionaryRepository.isReady) return null
        val corrector = contextCorrectorForLocale() ?: return null
        return try {
            if (!inputConnection.getSelectedText(0).isNullOrEmpty()) return null
            inputConnection.beginBatchEdit()
            val fix = try {
                val text = inputConnection.getTextBeforeCursor(CONTEXT_WINDOW, 0)?.toString() ?: return null
                val trailing = ContextCorrector.findTrailing(text, truncated = text.length >= CONTEXT_WINDOW)
                    ?: return null
                // Never older text the cursor happened to land on.
                fun same(a: String, b: String) = a.replace('’', '\'').equals(b.replace('’', '\''), ignoreCase = true)
                if (!same(trailing.word, previousWord) || !same(trailing.next, completedWord)) {
                    return null
                }
                // A fix made at the last space, seen again with the word after it.
                val autoFix = lastAutoFix?.takeIf { (_, replacement) -> same(replacement, trailing.word) }
                val fix = (autoFix?.let { (typed, _) -> corrector.reconsiderFix(trailing.before, typed, trailing.word, trailing.next) }
                    ?: corrector.review(trailing.before, trailing.word, trailing.next, trailing.beforePrevious, typedAsIs = autoFix == null))?.let { fixed ->
                    ContextFix(
                        word = trailing.word,
                        fix = fixed,
                        original = "${trailing.word} ${trailing.next}${trailing.tail}",
                        replaced = "$fixed ${trailing.next}${trailing.tail}",
                        reviewedLastWord = false
                    )
                } ?: if (trailing.tail.firstOrNull() in SENTENCE_END_MARKS) {
                    corrector.review(trailing.word, trailing.next, PairCounts.SENTENCE_END)?.let { fixed ->
                        ContextFix(
                            word = trailing.next,
                            fix = fixed,
                            original = "${trailing.next}${trailing.tail}",
                            replaced = "$fixed${trailing.tail}",
                            reviewedLastWord = true
                        )
                    }
                } else {
                    null
                }
                if (fix == null || !text.endsWith(fix.original)) return null
                inputConnection.deleteSurroundingText(fix.original.length, 0)
                inputConnection.commitText(fix.replaced, 1)
                fix
            } finally {
                inputConnection.endBatchEdit()
            }
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = fix.word,
                after = fix.fix,
                trigger = DebugCaptureStore.AutoCorrectionTrigger.SPACE,
                source = "SENTENCE_AWARE"
            )
            fix.copy(contextAfter = inputConnection.getTextBeforeCursor(CONTEXT_WINDOW, 0)?.toString()).also {
                lastContextFix = it
                NotificationHelper.triggerHapticFeedback(appContext)
            }
        } catch (error: RuntimeException) {
            Log.w("PastieraIME", "Sentence-aware review skipped", error)
            null
        }
    }

    /** For tests: waits for the pair counts to load; [load] = false only checks. */
    internal fun isContextCorrectorReadyForTests(load: Boolean = true): Boolean =
        if (load) contextCorrectorForLocale() != null else contextState.get()?.corrector != null

    /** Backspace right after a sentence-aware fix restores exactly what was typed. */
    private fun undoContextFix(fix: ContextFix, inputConnection: InputConnection?): Boolean {
        val connection = inputConnection ?: return false
        val expected = fix.contextAfter ?: return false
        if (connection.getTextBeforeCursor(expected.length, 0)?.toString() != expected) return false
        if (!expected.endsWith(fix.replaced)) return false
        connection.beginBatchEdit()
        try {
            connection.deleteSurroundingText(fix.replaced.length, 0)
            connection.commitText(fix.original, 1)
        } finally {
            connection.endBatchEdit()
        }
        DebugCaptureStore.recordAutoCorrectionAttempt(
            before = fix.fix,
            after = fix.word,
            trigger = DebugCaptureStore.AutoCorrectionTrigger.OTHER,
            source = "UNDO_SENTENCE_AWARE",
            outcome = DebugCaptureStore.AutoCorrectionOutcome.APPLIED,
            reason = "backspace_undo"
        )
        contextRejected += fix.word.lowercase() // Shared with the corrector.
        pendingAddUserWord = null
        previousCompletedWord = null
        lastBoundaryWord = null
        lastAutoFix = null
        return true
    }

    /**
     * Reads the word at cursor immediately without debounce.
     * Use this when entering a text field to show suggestions right away.
     * If dictionary is not ready yet, does nothing - normal typing/cursor flow will handle it.
     */
    fun readInitialContext(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (inputConnection == null) return
        if (!dictionaryRepository.isReady) {
            pendingInitialContextConnection = inputConnection
            ensureDictionaryLoaded(refreshAfterLoad = true)
            return
        }
        
        val word = extractWordAtCursor(inputConnection)
        if (!word.isNullOrBlank()) {
            tracker.setWord(word)
        } else if (previousCompletedWord == null) {
            publishSentenceStartPredictionsOrStarter()
        }
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        ensureDictionaryLoaded()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        if (inputConnection == null) {
            tracker.reset()
            previousCompletedWord = null
            sentenceStartPending = true
            suggestionsListener?.invoke(emptyList())
            return
        }
        cursorRunnable = Runnable {
            if (!dictionaryRepository.isReady) {
                tracker.reset()
                previousCompletedWord = null
                suggestionsListener?.invoke(emptyList())
                return@Runnable
            }
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
            } else {
                tracker.reset()
                val previous = previousCompletedWord
                val lastChar = lastCharBeforeCursor(inputConnection)
                if (previous != null && isSoftPredictionBoundary(lastChar)) {
                    publishNextWordPredictions(previous)
                } else {
                    previousCompletedWord = null
                    sentenceStartPending = true
                    publishSentenceStartPredictionsOrStarter()
                }
            }
        }
        cursorHandler.postDelayed(cursorRunnable!!, cursorDebounceMs)
    }

    /** Any key other than Backspace ends the chance to undo the last correction. */
    fun clearLastReplacement() {
        autoReplaceController.clearLastReplacement()
        lastContextFix = null
    }

    /** Counts a physical key press for typing speed (see TypingStats). */
    fun onTypingKey() {
        if (typingStats != null && isStatsAllowed()) typingStats.onKey()
    }

    fun onContextReset() {
        typingStats?.save()
        if (!isEnabled()) return
        autoReplaceController.clearLastReplacement()
        autoReplaceController.clearPendingLearning()
        lastContextFix = null
        lastBoundaryWord = null
        lastAutoFix = null
        tracker.onContextChanged()
        pendingAddUserWord = null
        previousCompletedWord = null
        sentenceStartPending = true
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        previousCompletedWord = null
        sentenceStartPending = true
    }

    fun addUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepositoryCache.values
            .ifEmpty { listOf(dictionaryRepository) }
            .forEach { repository -> repository.addUserEntryQuick(word) }
    }

    fun removeUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepositoryCache.values
            .ifEmpty { listOf(dictionaryRepository) }
            .forEach { repository -> repository.removeUserEntry(word) }
        refreshUserDictionary()
    }

    fun markUsed(word: String) {
        if (!isEnabled()) return
        dictionaryRepositoryCache.values
            .ifEmpty { listOf(dictionaryRepository) }
            .forEach { repository -> repository.markUsed(word) }
    }

    fun isKnownWordInActiveDictionaries(word: String): Boolean {
        if (!isEnabled()) return false
        val candidate = word.trim()
        if (candidate.isEmpty()) return false

        ensureDictionaryLoaded()
        if (dictionaryRepository.isReady && dictionaryRepository.isKnownWord(candidate)) {
            return true
        }

        return false
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()

    fun dismissSuggestion(candidate: String, hardDeleteUserWord: Boolean = false) {
        if (!isEnabled()) return
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return

        val current = latestSuggestions.get()
        val suggestion = current.firstOrNull { it.candidate.equals(trimmed, ignoreCase = true) }
        if (suggestion?.kind == SuggestionKind.NEXT_WORD) {
            forgetNextWordSuggestion(trimmed, hardDeleteEverywhere = hardDeleteUserWord)
        }
        if (hardDeleteUserWord) {
            removeUserWord(trimmed)
        }

        val next = fillWithStarterSuggestions(
            current.filterNot { it.candidate.equals(trimmed, ignoreCase = true) },
            settingsProvider(),
            excludedCandidates = setOf(trimmed)
        )
        latestSuggestions.set(next)
        suggestionsListener?.invoke(next)
    }

    private fun forgetNextWordSuggestion(candidate: String, hardDeleteEverywhere: Boolean) {
        val locales = listOf(currentLocale)
        locales.forEach { locale ->
            if (hardDeleteEverywhere) {
                nextWordPredictor.forgetNextWordEverywhere(locale, candidate)
            } else {
                val previous = previousCompletedWord
                if (previous != null) {
                    nextWordPredictor.forget(locale, previous, candidate)
                } else {
                    nextWordPredictor.forgetSentenceStart(locale, candidate)
                }
            }
        }
    }

    /**
     * Forces a refresh of user dictionary entries.
     * Should be called when words are added/removed from settings.
     */
    fun refreshUserDictionary() {
        if (!isEnabled()) return
        loadScope.launch {
            try {
                dictionaryRepository.refreshUserEntries()
            } catch (_: CancellationException) {
                // Cancelled due to rapid switches; safe to ignore.
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to refresh user dictionary", e)
            }
        }
    }

    fun handleBackspaceUndo(keyCode: Int, inputConnection: InputConnection?): Boolean {
        if (!isEnabled()) return false
        lastContextFix?.let { fix ->
            lastContextFix = null
            if (keyCode == KeyEvent.KEYCODE_DEL && undoContextFix(fix, inputConnection)) {
                if (isStatsAllowed()) typingStats?.onFixUndone()
                return true
            }
        }
        val undone = autoReplaceController.handleBackspaceUndo(keyCode, inputConnection)
        if (undone) {
            if (isStatsAllowed()) typingStats?.onFixUndone()
            pendingAddUserWord = autoReplaceController.consumeLastUndoOriginalWord()
            // The corrected word was taken back; do not learn the next word as following it.
            previousCompletedWord = null
        }
        return undone
    }

    fun pendingAddWord(): String? = pendingAddUserWord
    fun clearPendingAddWord() {
        pendingAddUserWord = null
    }

    internal fun flushNextWordLearningForTests() {
        nextWordPredictor.flushLearningForTests()
    }

    fun destroy() {
        typingStats?.save()
        contextLoadJob?.cancel()
        currentLoadJob?.cancel()
        suggestionJob?.cancel()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        nextWordPredictor.destroy()
    }

    private fun handleCompletedWordBoundary(completedWord: String?, boundaryChar: Char?) {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled) {
            previousCompletedWord = null
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }

        val cleanWord = completedWord?.trim()?.takeIf { it.any { ch -> ch.isLetterOrDigit() } }
        // Incognito fields (IME_FLAG_NO_PERSONALIZED_LEARNING): predict, but learn nothing.
        if (cleanWord != null && isLearningAllowed()) {
            if (sentenceStartPending) {
                nextWordPredictor.learnSentenceStart(currentLocale, cleanWord)
            }
            previousCompletedWord?.let { previous ->
                nextWordPredictor.learn(currentLocale, previous, cleanWord)
            }
        }

        when {
            cleanWord != null && isSoftPredictionBoundary(boundaryChar) -> {
                previousCompletedWord = cleanWord
                sentenceStartPending = false
                publishNextWordPredictions(cleanWord)
            }
            cleanWord == null && isSoftPredictionBoundary(boundaryChar) -> {
                val previous = previousCompletedWord
                if (previous != null) {
                    publishNextWordPredictions(previous)
                } else {
                    latestSuggestions.set(emptyList())
                    suggestionsListener?.invoke(emptyList())
                }
            }
            else -> {
                previousCompletedWord = null
                sentenceStartPending = true
                latestSuggestions.set(emptyList())
                suggestionsListener?.invoke(emptyList())
            }
        }
    }

    private fun publishNextWordPredictions(previousWord: String) {
        val settings = settingsProvider()
        val primary = nextWordPredictor.predict(
            currentLocale,
            previousWord,
            settings.maxSuggestions
        )
        val predictions = mergeSuggestionResults(primary, emptyList(), settings.maxSuggestions)
        val suggestions = fillWithStarterSuggestions(predictions, settings)
        if (suggestions.isNotEmpty()) {
            latestSuggestions.set(suggestions)
            suggestionsListener?.invoke(suggestions)
        } else {
            publishStarterSuggestions()
        }
    }

    private fun publishSentenceStartPredictionsOrStarter() {
        val settings = settingsProvider()
        val primary = nextWordPredictor.predictSentenceStart(currentLocale, settings.maxSuggestions)
        val predictions = mergeSuggestionResults(primary, emptyList(), settings.maxSuggestions)
        val suggestions = fillWithStarterSuggestions(predictions, settings)
        if (suggestions.isNotEmpty()) {
            latestSuggestions.set(suggestions)
            suggestionsListener?.invoke(suggestions)
        } else {
            publishStarterSuggestions()
        }
    }

    private fun publishStarterSuggestions() {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled || !dictionaryRepository.isReady) {
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }

        val suggestions = starterSuggestions(settings)
        latestSuggestions.set(suggestions)
        suggestionsListener?.invoke(suggestions)
    }

    private fun starterSuggestions(settings: SuggestionSettings): List<SuggestionResult> {
        val primary = starterSuggestionsFor(dictionaryRepository, PRIMARY_SUGGESTION_BOOST, settings.maxSuggestions)
        return mergeSuggestionResults(primary, emptyList(), settings.maxSuggestions)
    }

    private fun fillWithStarterSuggestions(
        predictions: List<SuggestionResult>,
        settings: SuggestionSettings
    ): List<SuggestionResult> {
        if (predictions.size >= settings.maxSuggestions) return predictions.take(settings.maxSuggestions)

        val seen = predictions
            .mapTo(HashSet()) { it.candidate.lowercase(currentLocale) }
        val fillers = starterSuggestions(settings)
            .filter { seen.add(it.candidate.lowercase(currentLocale)) }
        return (predictions + fillers).take(settings.maxSuggestions)
    }

    private fun fillWithStarterSuggestions(
        predictions: List<SuggestionResult>,
        settings: SuggestionSettings,
        excludedCandidates: Set<String>
    ): List<SuggestionResult> {
        if (predictions.size >= settings.maxSuggestions) return predictions.take(settings.maxSuggestions)

        val excluded = excludedCandidates.mapTo(HashSet()) { it.lowercase(currentLocale) }
        val seen = predictions
            .mapTo(HashSet()) { it.candidate.lowercase(currentLocale) }
        val fillers = starterSuggestions(settings)
            .filter { result ->
                val key = result.candidate.lowercase(currentLocale)
                key !in excluded && seen.add(key)
            }
        return (predictions + fillers).take(settings.maxSuggestions)
    }

    private fun starterSuggestionsFor(
        repository: DictionaryRepository,
        scoreBoost: Double,
        limit: Int
    ): List<SuggestionResult> {
        return repository.topCommonEntries(limit * 3)
            .map { entry ->
                SuggestionResult(
                    candidate = entry.word,
                    distance = 0,
                    score = repository.effectiveFrequency(entry) / 1_600.0 + scoreBoost +
                        if (entry.source == SuggestionSource.USER) 5.0 else 0.0,
                    source = entry.source,
                    kind = SuggestionKind.STARTER_WORD
                )
            }
            .take(limit)
    }

    private fun mergeSuggestionResults(
        primary: List<SuggestionResult>,
        extras: List<SuggestionResult>,
        limit: Int,
        locale: Locale = currentLocale
    ): List<SuggestionResult> {
        val seen = HashSet<String>()
        return (primary.map { it to PRIMARY_SUGGESTION_BOOST } + extras.map { it to 0.0 })
            .sortedWith(
                compareByDescending<Pair<SuggestionResult, Double>> { (result, boost) ->
                    result.score + boost
                }.thenBy { (result, _) -> result.candidate.length }
            )
            .map { it.first }
            .filter { result -> seen.add(result.candidate.lowercase(locale)) }
            .take(limit)
    }

    private fun isSoftPredictionBoundary(boundaryChar: Char?): Boolean {
        return boundaryChar == ' ' || boundaryChar == ',' || boundaryChar == ';' || boundaryChar == ':'
    }

    private fun boundaryCharFor(keyCode: Int, event: KeyEvent?): Char? {
        val unicodeChar = event?.unicodeChar ?: 0
        return when {
            unicodeChar != 0 -> unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            keyCode == KeyEvent.KEYCODE_COMMA -> ','
            keyCode == KeyEvent.KEYCODE_SEMICOLON -> ';'
            keyCode == KeyEvent.KEYCODE_PERIOD -> '.'
            keyCode == KeyEvent.KEYCODE_SLASH -> '/'
            keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> '['
            keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'
            keyCode == KeyEvent.KEYCODE_BACKSLASH -> '\\'
            else -> KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .get(keyCode, 0)
                .takeIf { it != 0 }
                ?.toChar()
        }
    }

    private fun lastCharBeforeCursor(inputConnection: InputConnection?): Char? {
        return try {
            inputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clears the pending add-word candidate if the cursor is no longer on that word.
     * Keeps the candidate only while the cursor remains on the originating token.
     */
    fun clearPendingAddWordIfCursorOutside(inputConnection: InputConnection?) {
        val pending = pendingAddUserWord ?: return
        val currentWord = extractWordAtCursor(inputConnection)
        if (currentWord == null || !currentWord.equals(pending, ignoreCase = true)) {
            pendingAddUserWord = null
        }
    }

    private fun extractWordAtCursor(
        inputConnection: InputConnection?,
        includeAfterCursor: Boolean = true
    ): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(CURSOR_WORD_CONTEXT_CHARS, 0)?.toString() ?: ""
            val after = if (includeAfterCursor) {
                inputConnection.getTextAfterCursor(CURSOR_WORD_CONTEXT_CHARS, 0)?.toString() ?: ""
            } else {
                ""
            }
            var start = before.length
            while (start > 0) {
                val ch = before[start - 1]
                val prev = before.getOrNull(start - 2)
                val next = before.getOrNull(start)
                if (!it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)) {
                    start--
                    continue
                }
                break
            }
            var end = 0
            if (includeAfterCursor) {
                while (end < after.length) {
                    val ch = after[end]
                    val prev = if (end == 0) before.lastOrNull() else after[end - 1]
                    val next = after.getOrNull(end + 1)
                    if (!it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)) {
                        end++
                        continue
                    }
                    break
                }
            }
            val word = before.substring(start) + after.substring(0, end)
            if (word.isBlank()) null else word
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Preloads the dictionary in background.
     * Should be called during initialization to have dictionary ready when user focuses a field.
     */
    fun preloadDictionary() {
        schedulePrimaryDictionaryLoad(refreshAfterLoad = false)
        // Word pairs too, so the first words typed already get context-aware fixes.
        val settings = settingsProvider()
        if (settings.contextAwareCorrection ||
            settings.textReplacementsEnabled && currentLocale.language == "en"
        ) {
            contextCorrectorForLocale()
        }
    }

    private fun ensureDictionaryLoaded(refreshAfterLoad: Boolean = false) {
        if (!dictionaryRepository.isReady) {
            schedulePrimaryDictionaryLoad(refreshAfterLoad)
        }
    }

    private fun schedulePrimaryDictionaryLoad(refreshAfterLoad: Boolean) {
        val repository = dictionaryRepository
        if (refreshAfterLoad) {
            pendingPrimaryRefreshAfterLoad = true
        }
        if (repository.isReady) {
            if (refreshAfterLoad) {
                pendingPrimaryRefreshAfterLoad = false
                cursorHandler.post { refreshSuggestionsAfterDictionaryReady(repository) }
            }
            return
        }
        if (repository.isLoadStarted) return

        currentLoadJob = loadScope.launch {
            try {
                repository.loadIfNeeded()
                val shouldRefresh = refreshAfterLoad || pendingPrimaryRefreshAfterLoad
                if (shouldRefresh && repository.isReady) {
                    pendingPrimaryRefreshAfterLoad = false
                    cursorHandler.post { refreshSuggestionsAfterDictionaryReady(repository) }
                }
            } catch (_: CancellationException) {
                // Cancelled due to rapid switches; safe to ignore.
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to load dictionary", e)
            }
        }
    }

    private fun refreshSuggestionsAfterDictionaryReady(repository: DictionaryRepository) {
        if (!isEnabled() || repository !== dictionaryRepository || !repository.isReady) return

        pendingInitialContextConnection?.let { inputConnection ->
            pendingInitialContextConnection = null
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                return
            }
            if (previousCompletedWord == null) {
                publishSentenceStartPredictionsOrStarter()
                return
            }
        }

        val word = tracker.currentWord
        if (word.isNotBlank()) {
            updateSuggestionsForWord(word)
        } else if (previousCompletedWord == null) {
            publishSentenceStartPredictionsOrStarter()
        }
    }

    companion object {
        private const val NOISY_CHANNEL_CANDIDATES = 64
        private const val NOISY_CHANNEL_MIN_FREQUENCY = 20
        private const val CONTEXT_WINDOW = 96
        // A contraction must follow the previous word clearly more often than the typed word.
        private const val MIN_REPLACEMENT_EVIDENCE = 10
        private const val REPLACEMENT_EVIDENCE_RATIO = 3
        private const val COMMON_REAL_WORD_FREQUENCY = 100
        private val SENTENCE_END_MARKS = setOf('.', '!', '?')

        private const val CURSOR_WORD_CONTEXT_CHARS = 128
        private const val PRIMARY_SUGGESTION_BOOST = 0.35
    }
}
