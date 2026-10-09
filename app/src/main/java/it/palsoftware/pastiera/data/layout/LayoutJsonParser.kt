package it.palsoftware.pastiera.data.layout

import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.io.InputStream

/**
 * Parses and validates keyboard layout JSON (the bundled layouts in assets/common/layouts).
 */
object LayoutJsonParser {
    private const val TAG = "LayoutJsonParser"

    private val keyboardLayoutNameToKeyCode = mapOf(
        "KEYCODE_Q" to KeyEvent.KEYCODE_Q,
        "KEYCODE_W" to KeyEvent.KEYCODE_W,
        "KEYCODE_E" to KeyEvent.KEYCODE_E,
        "KEYCODE_R" to KeyEvent.KEYCODE_R,
        "KEYCODE_T" to KeyEvent.KEYCODE_T,
        "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
        "KEYCODE_U" to KeyEvent.KEYCODE_U,
        "KEYCODE_I" to KeyEvent.KEYCODE_I,
        "KEYCODE_O" to KeyEvent.KEYCODE_O,
        "KEYCODE_P" to KeyEvent.KEYCODE_P,
        "KEYCODE_A" to KeyEvent.KEYCODE_A,
        "KEYCODE_S" to KeyEvent.KEYCODE_S,
        "KEYCODE_D" to KeyEvent.KEYCODE_D,
        "KEYCODE_F" to KeyEvent.KEYCODE_F,
        "KEYCODE_G" to KeyEvent.KEYCODE_G,
        "KEYCODE_H" to KeyEvent.KEYCODE_H,
        "KEYCODE_J" to KeyEvent.KEYCODE_J,
        "KEYCODE_K" to KeyEvent.KEYCODE_K,
        "KEYCODE_L" to KeyEvent.KEYCODE_L,
        "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
        "KEYCODE_X" to KeyEvent.KEYCODE_X,
        "KEYCODE_C" to KeyEvent.KEYCODE_C,
        "KEYCODE_V" to KeyEvent.KEYCODE_V,
        "KEYCODE_B" to KeyEvent.KEYCODE_B,
        "KEYCODE_N" to KeyEvent.KEYCODE_N,
        "KEYCODE_M" to KeyEvent.KEYCODE_M,
        "KEYCODE_1" to KeyEvent.KEYCODE_1,
        "KEYCODE_2" to KeyEvent.KEYCODE_2,
        "KEYCODE_3" to KeyEvent.KEYCODE_3,
        "KEYCODE_4" to KeyEvent.KEYCODE_4,
        "KEYCODE_5" to KeyEvent.KEYCODE_5,
        "KEYCODE_6" to KeyEvent.KEYCODE_6,
        "KEYCODE_7" to KeyEvent.KEYCODE_7,
        "KEYCODE_8" to KeyEvent.KEYCODE_8,
        "KEYCODE_9" to KeyEvent.KEYCODE_9,
        "KEYCODE_0" to KeyEvent.KEYCODE_0,
        "KEYCODE_GRAVE" to KeyEvent.KEYCODE_GRAVE,
        "KEYCODE_MINUS" to KeyEvent.KEYCODE_MINUS,
        "KEYCODE_EQUALS" to KeyEvent.KEYCODE_EQUALS,
        "KEYCODE_LEFT_BRACKET" to KeyEvent.KEYCODE_LEFT_BRACKET,
        "KEYCODE_RIGHT_BRACKET" to KeyEvent.KEYCODE_RIGHT_BRACKET,
        "KEYCODE_BACKSLASH" to KeyEvent.KEYCODE_BACKSLASH,
        "KEYCODE_SEMICOLON" to KeyEvent.KEYCODE_SEMICOLON,
        "KEYCODE_APOSTROPHE" to KeyEvent.KEYCODE_APOSTROPHE,
        "KEYCODE_COMMA" to KeyEvent.KEYCODE_COMMA,
        "KEYCODE_PERIOD" to KeyEvent.KEYCODE_PERIOD,
        "KEYCODE_SLASH" to KeyEvent.KEYCODE_SLASH
    )

    internal enum class LayoutParseError {
        MALFORMED_JSON,
        MISSING_MAPPINGS,
        MAPPINGS_NOT_OBJECT,
        EMPTY_MAPPINGS,
        NO_SUPPORTED_MAPPINGS,
        INVALID_MAPPING
    }

    internal sealed interface LayoutParseResult {
        data class Success(val layout: Map<Int, LayoutMapping>) : LayoutParseResult
        data class Failure(
            val error: LayoutParseError,
            val detail: String? = null
        ) : LayoutParseResult
    }

    fun loadLayoutFromStream(inputStream: InputStream): Map<Int, LayoutMapping>? {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            when (val result = validateLayoutJson(jsonString)) {
                is LayoutParseResult.Success -> result.layout
                is LayoutParseResult.Failure -> {
                    Log.e(TAG, "Invalid layout JSON: ${result.error}${result.detail?.let { " ($it)" }.orEmpty()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from stream", e)
            null
        }
    }

    internal fun validateLayoutJson(jsonString: String): LayoutParseResult {
        val jsonObject = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return LayoutParseResult.Failure(LayoutParseError.MALFORMED_JSON, e.message)
        }

        if (!jsonObject.has("mappings")) {
            return LayoutParseResult.Failure(LayoutParseError.MISSING_MAPPINGS)
        }
        val mappingsObject = jsonObject.opt("mappings") as? JSONObject
            ?: return LayoutParseResult.Failure(LayoutParseError.MAPPINGS_NOT_OBJECT)
        if (mappingsObject.length() == 0) {
            return LayoutParseResult.Failure(LayoutParseError.EMPTY_MAPPINGS)
        }

        val layout = mutableMapOf<Int, LayoutMapping>()
        val keys = mappingsObject.keys()
        while (keys.hasNext()) {
            val keyName = keys.next()
            val keyCode = keyboardLayoutNameToKeyCode[keyName] ?: continue
            val mappingObj = mappingsObject.opt(keyName) as? JSONObject
                ?: return invalidMapping(keyName, "mapping must be an object")
            val lowercase = mappingObj.requiredNonEmptyString("lowercase")
                ?: return invalidMapping(keyName, "lowercase must be a non-empty string")
            val uppercase = mappingObj.requiredNonEmptyString("uppercase")
                ?: return invalidMapping(keyName, "uppercase must be a non-empty string")

            layout[keyCode] = LayoutMapping(lowercase = lowercase, uppercase = uppercase)
        }

        if (layout.isEmpty()) {
            return LayoutParseResult.Failure(LayoutParseError.NO_SUPPORTED_MAPPINGS)
        }
        Log.d(TAG, "Parsed layout with ${layout.size} mappings")
        return LayoutParseResult.Success(layout)
    }

    private fun invalidMapping(keyName: String, detail: String) =
        LayoutParseResult.Failure(LayoutParseError.INVALID_MAPPING, "$keyName: $detail")

    private fun JSONObject.requiredNonEmptyString(key: String): String? {
        val value = opt(key)
        return (value as? String)?.takeIf { it.isNotEmpty() }
    }
}
