package it.palsoftware.pastiera

object SoftwareKeyboardModeActions {
    val ACTION_TOGGLE: String = BuildConfig.TOGGLE_KEYBOARD_MODE_ACTION

    fun toggleTemporaryMode(context: android.content.Context): SettingsManager.SoftwareKeyboardMode {
        val current = SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
        val next = when (current) {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL -> SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.SoftwareKeyboardMode.AUTO -> SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        }
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(context, next)
        return next
    }

    fun clearTemporaryMode(context: android.content.Context) {
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(context, null)
    }
}
