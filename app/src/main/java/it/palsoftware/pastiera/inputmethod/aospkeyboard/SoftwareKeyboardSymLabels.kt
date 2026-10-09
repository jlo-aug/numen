package it.palsoftware.pastiera.inputmethod.aospkeyboard

import android.view.KeyEvent

internal object SoftwareKeyboardSymLabels {
    data class Projection(
        val contentByKeyCode: Map<Int, String>,
        val contentByBaseText: Map<String, String>
    )

    fun project(
        rows: List<String>,
        symMappings: Map<Int, String>
    ): Projection = Projection(
        contentByKeyCode = symMappings.filterValues { it.isNotBlank() },
        contentByBaseText = buildContentByChar(rows, symMappings)
            .mapKeys { (char, _) -> char.toString() }
    )

    /** Labels each software key with its hardware key's SYM content, or the key's own symbol. */
    fun buildContentByChar(
        rows: List<String>,
        symMappings: Map<Int, String>
    ): Map<Char, String> {
        val contentByChar = linkedMapOf<Char, String>()
        rows.flatMap { it.toList() }.distinct().forEach { char ->
            val content = keyCodeForChar(char)?.let { symMappings[it] } ?: symbolFallback(char)
            if (content.isNotEmpty()) {
                contentByChar[char] = content
            }
        }
        return contentByChar
    }

    fun keyCodeForChar(char: Char): Int? = when (char.lowercaseChar()) {
        'q' -> KeyEvent.KEYCODE_Q
        'w' -> KeyEvent.KEYCODE_W
        'e' -> KeyEvent.KEYCODE_E
        'r' -> KeyEvent.KEYCODE_R
        't' -> KeyEvent.KEYCODE_T
        'y' -> KeyEvent.KEYCODE_Y
        'u' -> KeyEvent.KEYCODE_U
        'i' -> KeyEvent.KEYCODE_I
        'o' -> KeyEvent.KEYCODE_O
        'p' -> KeyEvent.KEYCODE_P
        'a' -> KeyEvent.KEYCODE_A
        's' -> KeyEvent.KEYCODE_S
        'd' -> KeyEvent.KEYCODE_D
        'f' -> KeyEvent.KEYCODE_F
        'g' -> KeyEvent.KEYCODE_G
        'h' -> KeyEvent.KEYCODE_H
        'j' -> KeyEvent.KEYCODE_J
        'k' -> KeyEvent.KEYCODE_K
        'l' -> KeyEvent.KEYCODE_L
        'z' -> KeyEvent.KEYCODE_Z
        'x' -> KeyEvent.KEYCODE_X
        'c' -> KeyEvent.KEYCODE_C
        'v' -> KeyEvent.KEYCODE_V
        'b' -> KeyEvent.KEYCODE_B
        'n' -> KeyEvent.KEYCODE_N
        'm' -> KeyEvent.KEYCODE_M
        ',' -> KeyEvent.KEYCODE_COMMA
        '.' -> KeyEvent.KEYCODE_PERIOD
        else -> null
    }

    private fun symbolFallback(char: Char): String =
        when {
            char.isLetterOrDigit() -> ""
            else -> char.toString()
        }
}
