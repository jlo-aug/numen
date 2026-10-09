package it.palsoftware.pastiera.inputmethod.aospkeyboard

internal object SoftwareKeyboardLayoutTemplates {
    fun rowTemplateFor(style: AospKeyboardView.SoftwareLayoutStyle): List<String> =
        when (style) {
            AospKeyboardView.SoftwareLayoutStyle.COMPACT ->
                listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO ->
                listOf("qwertyuiop[", "asdfghjkl;'", "zxcvbnm")
            AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI ->
                listOf("qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./")
            AospKeyboardView.SoftwareLayoutStyle.FULL_ISO ->
                listOf("qwertyuiop[]", "asdfghjkl;'#", "\\zxcvbnm,./")
        }
}
