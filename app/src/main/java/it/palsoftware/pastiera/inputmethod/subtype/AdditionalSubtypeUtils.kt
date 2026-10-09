package it.palsoftware.pastiera.inputmethod.subtype

import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService

/**
 * Numen has a single English subtype, declared in method.xml. These helpers read its locale and
 * clear any extra subtypes an earlier version registered.
 */
object AdditionalSubtypeUtils {
    private const val TAG = "AdditionalSubtypeUtils"

    /** The locale of Numen's only subtype, as declared in method.xml. */
    const val SUBTYPE_LOCALE = "en_US"

    fun InputMethodSubtype.localeString(): String =
        languageTag.takeIf { it.isNotBlank() }
            ?: legacyLocaleString()

    @Suppress("DEPRECATION")
    private fun InputMethodSubtype.legacyLocaleString(): String = locale.orEmpty()

    /**
     * Removes any extra subtypes an earlier version registered (other languages and custom input
     * styles). Numen has one English subtype, declared in method.xml.
     */
    @Suppress("DEPRECATION")
    fun clearAdditionalSubtypes(context: Context) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
            val imeId = imm.inputMethodList.firstOrNull { info ->
                info.packageName == context.packageName &&
                    info.serviceName == PhysicalKeyboardInputMethodService::class.java.name
            }?.id ?: return
            imm.setAdditionalInputMethodSubtypes(imeId, emptyArray())
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear additional subtypes", e)
        }
    }
}
