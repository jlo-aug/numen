package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager

internal object SoftwareKeyboardDeviceTransitionPolicy {
    /**
     * The keyboard surface to switch to when a keyboard connects or disconnects, or null when the
     * automatic choice did not change. A temporary override is cleared whenever this returns a mode.
     */
    fun plan(
        configuredMode: SettingsManager.SoftwareKeyboardMode,
        previousAutoMode: SettingsManager.SoftwareKeyboardMode?,
        autoMode: SettingsManager.SoftwareKeyboardMode
    ): SettingsManager.SoftwareKeyboardMode? {
        if (autoMode == previousAutoMode) return null
        return if (configuredMode == SettingsManager.SoftwareKeyboardMode.AUTO) autoMode else configuredMode
    }
}
