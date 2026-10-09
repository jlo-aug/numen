package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject

/**
 * Applies text replacements ("dont" → "don't", user entries like "brb" → "be right back")
 * when space or punctuation is pressed.
 * Supports undo with backspace (only as first action after correction).
 * Preserves original capitalization (Dont → Don't, DONT → DON'T).
 */
object AutoCorrector {
    private const val TAG = "AutoCorrector"

    private const val ASSET_PATH = "common/autocorrect/auto_corrections_en.json"

    /** Lowercase trigger → replacement: the bundled English list overlaid with the user's entries. */
    @Volatile
    private var corrections: Map<String, String> = emptyMap()

    /**
     * Information about the last applied correction.
     * The correction remains undoable until a key is pressed (except backspace).
     */
    data class LastCorrection(
        val originalWord: String,
        val correctedWord: String,
        val correctionLength: Int // Length of the inserted corrected word
    )

    // Track last applied correction (null if none or if accepted)
    private var lastCorrection: LastCorrection? = null

    // Track words that have been rejected (undone with backspace)
    // These words won't be corrected until the user modifies the text
    private val rejectedWords = mutableSetOf<String>()

    private fun hasSymbolChar(text: String): Boolean {
        return text.any { !it.isLetterOrDigit() }
    }

    private fun isBoundaryBeforeIndex(text: String, index: Int): Boolean {
        if (index <= 0) return true
        val ch = text[index - 1]
        val prev = if (index - 2 >= 0) text[index - 2] else null
        val next = text.getOrNull(index)
        return it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)
    }

    /**
     * Matches custom/exact triggers that may contain symbols (e.g. ":e1", "@@@")
     * at the end of the text segment before trailing boundaries.
     */
    private fun findExactSymbolTrigger(text: String, endIndex: Int): Pair<String, String>? {
        if (endIndex <= 0) return null
        val textUpToCursor = text.substring(0, endIndex)
        var bestMatch: Pair<String, String>? = null

        for ((trigger, replacement) in corrections) {
            if (trigger.isBlank() || !hasSymbolChar(trigger)) continue
            if (!textUpToCursor.endsWith(trigger, ignoreCase = true)) continue

            val start = textUpToCursor.length - trigger.length
            if (!isBoundaryBeforeIndex(textUpToCursor, start)) continue

            val currentBestLength = bestMatch?.first?.length ?: -1
            if (trigger.length > currentBestLength) {
                bestMatch = Pair(trigger, replacement)
            }
        }

        return bestMatch
    }

    /**
     * Loads the bundled English text replacements and, when [context] is given,
     * overlays the user's own entries on top (custom entries win).
     */
    fun loadCorrections(assets: AssetManager, context: Context? = null) {
        val loaded = mutableMapOf<String, String>()
        try {
            val jsonString = assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            for (key in json.keys()) {
                loaded[key] = json.getString(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $ASSET_PATH", e)
        }
        if (context != null) {
            val custom = it.palsoftware.pastiera.SettingsManager.getCustomAutoCorrections(context)
            loaded.putAll(custom)
            Log.d(TAG, "Loaded ${custom.size} custom text replacements")
        }
        corrections = loaded
        Log.d(TAG, "Loaded ${loaded.size} text replacements")
    }

    /**
     * Applies original capitalization to corrected word.
     * Correctly handles special characters at the beginning (apostrophes, etc.).
     *
     * @param originalWord The original word (e.g. "Cos", "CASA", "cos")
     * @param correctedWord The corrected word in lowercase (e.g. "cos'è", "casa")
     * @return The corrected word with preserved capitalization (e.g. "Cos'è", "CASA", "cos'è")
     */
    private fun applyCapitalization(originalWord: String, correctedWord: String): String {
        if (originalWord.isEmpty() || correctedWord.isEmpty()) {
            return correctedWord
        }

        // If original word was all uppercase, apply uppercase to entire correction
        if (originalWord == originalWord.uppercase() && originalWord.any { it.isLetter() }) {
            // Find first alphabetic letter in correction (might be after apostrophes)
            val firstLetterIndex = correctedWord.indexOfFirst { it.isLetter() }
            if (firstLetterIndex >= 0) {
                val beforeLetter = correctedWord.substring(0, firstLetterIndex)
                val fromLetter = correctedWord.substring(firstLetterIndex)
                return beforeLetter + fromLetter.uppercase()
            }
            return correctedWord.uppercase()
        }

        // If first letter of original word was uppercase, capitalize first letter of correction
        if (originalWord.isNotEmpty() && originalWord[0].isUpperCase()) {
            // Find first alphabetic letter in correction (might be after apostrophes/punctuation)
            val firstLetterIndex = correctedWord.indexOfFirst { it.isLetter() }
            if (firstLetterIndex >= 0) {
                val beforeLetter = correctedWord.substring(0, firstLetterIndex)
                val firstLetter = correctedWord[firstLetterIndex]
                val afterLetter = correctedWord.substring(firstLetterIndex + 1)

                // Capitalize first alphabetic letter
                return beforeLetter + firstLetter.uppercaseChar() + afterLetter
            }
        }

        // Otherwise, keep correction in lowercase
        return correctedWord
    }

    /** Returns the replacement for [word] with its capitalization applied, or null. */
    private fun getCorrection(word: String): String? {
        val correction = corrections[word.lowercase()] ?: return null
        return applyCapitalization(word, correction)
    }

    /**
     * Processes text before cursor and applies corrections if needed.
     * Supports both single words and two-word patterns (e.g. "cos e" → "cos'è").
     * Replacements apply even when the typed word is a dictionary word ("ill" → "I'll").
     * @param textBeforeCursor The text before cursor
     * @return Pair<wordToReplace, correctedWord> if there's a correction, null otherwise
     */
    fun processText(textBeforeCursor: CharSequence?): Pair<String, String>? {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null
        }

        val text = textBeforeCursor.toString()
        var endIndex = text.length

        fun isWordBoundaryAt(index: Int): Boolean {
            val ch = text[index]
            val prev = if (index > 0) text[index - 1] else null
            val next = if (index + 1 < text.length) text[index + 1] else null
            return it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)
        }

        // Highest priority: exact trigger matches that include symbols.
        // Use a cursor endpoint trimmed only for trailing whitespace so pure-symbol
        // triggers like "@@@" are still visible (full boundary trim would remove them).
        var exactSymbolEndIndex = text.length
        while (exactSymbolEndIndex > 0 && text[exactSymbolEndIndex - 1].isWhitespace()) {
            exactSymbolEndIndex--
        }
        val exactSymbolMatch = findExactSymbolTrigger(text, exactSymbolEndIndex)
        if (exactSymbolMatch != null) {
            val (trigger, replacement) = exactSymbolMatch
            val triggerLower = trigger.lowercase()
            if (!rejectedWords.contains(triggerLower)) {
                Log.d(TAG, "Found exact symbol trigger: '$trigger' → '$replacement'")
                return Pair(trigger, replacement)
            }
            Log.d(TAG, "Exact symbol trigger '$trigger' is rejected, skipping")
        }

        // Ignore spaces and punctuation at the end
        while (endIndex > 0 && isWordBoundaryAt(endIndex - 1)) {
            endIndex--
        }

        if (endIndex == 0) {
            return null
        }

        val trailing = text.substring(endIndex)
        val hasHardBoundary = trailing.any { ch ->
            val normalized = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(ch)
            !normalized.isWhitespace() && normalized !in it.palsoftware.pastiera.core.Punctuation.BOUNDARY
        }
        if (hasHardBoundary) {
            return null
        }

        // First, try to search for patterns that include spaces (e.g. "cos e")
        // Search up to 2 "words" (separated by spaces) before cursor
        for (maxWords in 2 downTo 1) {
            val currentEnd = endIndex
            var wordsFound = 0
            var startIndex = currentEnd

            // Find start of maxWords word sequence
            while (startIndex > 0 && wordsFound < maxWords) {
                // Go back until finding space or punctuation
                var tempIndex = startIndex - 1
                while (tempIndex > 0 && !isWordBoundaryAt(tempIndex - 1)) {
                    tempIndex--
                }

                if (tempIndex < startIndex) {
                    wordsFound++
                    if (wordsFound < maxWords) {
                        // Go back past space to find next word
                        while (tempIndex > 0 && isWordBoundaryAt(tempIndex - 1)) {
                            tempIndex--
                        }
                        startIndex = tempIndex
                    } else {
                        startIndex = tempIndex
                    }
                } else {
                    break
                }
            }

            if (startIndex < currentEnd && wordsFound == maxWords) {
                // Extract sequence (may contain spaces if maxWords > 1)
                val sequence = text.substring(startIndex, currentEnd).trim()
                if (sequence.isNotEmpty()) {
                    // Check if this sequence has been rejected
                    if (rejectedWords.contains(sequence.lowercase())) {
                        Log.d(TAG, "Sequence '$sequence' has been rejected, don't correct")
                        continue // Try with fewer words
                    }
                    val correction = getCorrection(sequence)
                    if (correction != null) {
                        Log.d(TAG, "Found correction for sequence: '$sequence' → '$correction'")
                        return Pair(sequence, correction)
                    }
                }
            }
        }

        // If we didn't find patterns with spaces, search for a single word
        var startIndex = endIndex
        while (startIndex > 0 && !isWordBoundaryAt(startIndex - 1)) {
            startIndex--
        }

        if (startIndex >= endIndex) {
            return null
        }

        val word = text.substring(startIndex, endIndex)
        if (word.isEmpty()) {
            return null
        }

        // Check if this word has been rejected (undone with backspace)
        if (rejectedWords.contains(word.lowercase())) {
            Log.d(TAG, "Word '$word' has been rejected, don't correct")
            return null
        }

        val correction = getCorrection(word) ?: return null
        Log.d(TAG, "Found correction for word: '$word' → '$correction'")
        return Pair(word, correction)
    }

    /**
     * Records an applied correction.
     * The correction remains undoable until a key is pressed (except backspace).
     * @param originalWord The original word
     * @param correctedWord The corrected word
     */
    fun recordCorrection(
        originalWord: String,
        correctedWord: String,
        trigger: DebugCaptureStore.AutoCorrectionTrigger = DebugCaptureStore.AutoCorrectionTrigger.OTHER
    ) {
        lastCorrection = LastCorrection(
            originalWord = originalWord,
            correctedWord = correctedWord,
            correctionLength = correctedWord.length
        )
        DebugCaptureStore.recordAutoCorrectionApplied(originalWord, correctedWord, trigger)
        Log.d(TAG, "Correction recorded: '$originalWord' → '$correctedWord'")
    }

    /**
     * Gets last correction if still undoable.
     * @return Correction information if it can be undone, null otherwise
     */
    fun getLastCorrection(): LastCorrection? {
        return lastCorrection
    }

    /**
     * Accepts last correction (called when a key other than backspace is pressed).
     * After acceptance, the correction can no longer be undone.
     */
    fun acceptLastCorrection() {
        if (lastCorrection != null) {
            Log.d(TAG, "Correction accepted: '${lastCorrection!!.correctedWord}'")
            lastCorrection = null
        }
    }

    /**
     * Undoes last correction.
     * After undo, the correction can no longer be undone.
     * The original word is added to the rejected words list
     * to avoid immediately re-proposing the same correction.
     * @return Information about undone correction, null if there was no correction
     */
    fun undoLastCorrection(): LastCorrection? {
        val correction = lastCorrection
        if (correction != null) {
            Log.d(TAG, "Correction undone: '${correction.correctedWord}' → '${correction.originalWord}'")

            // Add original word to rejected words list
            // (use lowercase for comparison, so it works regardless of capitalization)
            rejectedWords.add(correction.originalWord.lowercase())
            Log.d(TAG, "Word '${correction.originalWord}' added to rejected list")

            lastCorrection = null
            return correction
        }
        return null
    }

    /**
     * Resets the rejected words list.
     * Called when user types a new character (not backspace),
     * indicating they may have modified the text and thus rejected corrections
     * might no longer be valid.
     */
    fun clearRejectedWords() {
        if (rejectedWords.isNotEmpty()) {
            Log.d(TAG, "Reset rejected words list (${rejectedWords.size} words)")
            rejectedWords.clear()
        }
    }

}
