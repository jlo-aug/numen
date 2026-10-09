package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import java.util.LinkedHashMap
import java.util.Locale

object AutoCorrectionSubstitutionStore {
    fun addCustomSubstitution(
        context: Context,
        trigger: String,
        replacement: String
    ): Boolean {
        val normalizedTrigger = trigger.trim().lowercase(Locale.ROOT)
        val normalizedReplacement = replacement.trim()
        if (normalizedTrigger.isBlank() || normalizedReplacement.isBlank()) return false
        if (normalizedTrigger == "__name") return false

        val existing = SettingsManager.getCustomAutoCorrections(context)
        val updated = LinkedHashMap<String, String>()
        updated[normalizedTrigger] = normalizedReplacement
        existing.forEach { (key, value) ->
            if (key != normalizedTrigger) {
                updated[key] = value
            }
        }
        SettingsManager.saveCustomAutoCorrections(context, updated)
        reloadAutoCorrector(context)
        return true
    }

    private fun reloadAutoCorrector(context: Context) {
        try {
            AutoCorrector.loadCorrections(context.assets, context)
        } catch (_: Exception) {
            // The persisted substitution will still be picked up on the next full reload.
        }
    }
}
