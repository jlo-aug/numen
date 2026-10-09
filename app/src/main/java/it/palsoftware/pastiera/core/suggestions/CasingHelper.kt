package it.palsoftware.pastiera.core.suggestions

import java.util.Locale

/**
 * Applies the right capitalization to suggestions
 * based on the casing pattern of the word the user typed.
 */
object CasingHelper {

    private fun capitalizeFirstLetter(candidate: String): String {
        val idx = candidate.indexOfFirst { it.isLetter() }
        if (idx < 0) return candidate
        val first = candidate[idx]
        val cap = if (first.isLowerCase()) first.titlecase(Locale.ROOT) else first.toString()
        return candidate.substring(0, idx) + cap + candidate.substring(idx + 1)
    }

    /**
     * Applies capitalization to the suggestion based on the original word's casing pattern.
     *
     * @param candidate The suggested word (e.g. "Parenzo")
     * @param original The word the user typed (e.g. "parenz", "Parenz", "PARENZ")
     * @param forceLeadingCapital If true, forces an uppercase first letter (for auto-capitalize)
     * @return The word with the correct capitalization
     */
    fun applyCasing(
        candidate: String,
        original: String,
        forceLeadingCapital: Boolean = false
    ): String {
        if (candidate.isEmpty()) return candidate
        
        // If the field requires forced capitalization, apply titlecase
        if (forceLeadingCapital) {
            return capitalizeFirstLetter(candidate)
        }
        
        if (original.isEmpty()) return candidate
        
        // Determine the casing pattern from letters only (ignore apostrophes/punctuation)
        val letters = original.filter { it.isLetter() }
        if (letters.isEmpty()) return candidate

        val allUpper = letters.length > 1 && letters.all { it.isUpperCase() }
        val allLower = letters.all { it.isLowerCase() }
        val firstLetter = letters.first()
        val restLetters = letters.drop(1)
        val firstUpper = firstLetter.isUpperCase()
        val restLower = restLetters.all { it.isLowerCase() }

        // If the candidate has uppercase letters and we're not in the "allUpper" case (>=2 uppercase letters),
        // keep the dictionary casing as is.
        val candidateHasUpper = candidate.any { it.isUpperCase() }
        val candidateLettersUpperCount = candidate.count { it.isUpperCase() }
        if (!forceLeadingCapital && !allUpper && candidateHasUpper && candidateLettersUpperCount < 2) {
            return candidate
        }
        // If the original is all lowercase but the candidate has uppercase letters (e.g. "mccartney" -> "McCartney"),
        // keep the candidate's casing.
        if (allLower && candidateHasUpper) {
            return candidate
        }
        
        return when {
            // Case: PARENZ -> PARENZO (all uppercase)
            allUpper -> candidate.uppercase(Locale.ROOT)
            // Case: Parenz -> Parenzo (first uppercase, rest lowercase)
            firstUpper && restLower -> capitalizeFirstLetter(candidate)
            // Case: parenz -> parenzo (all lowercase)
            allLower -> candidate.lowercase(Locale.ROOT)
            // Other cases: use the suggestion as is
            else -> candidate
        }
    }
}

