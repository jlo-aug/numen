package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

/**
 * Pages of the former "Keyboard & Devices" screen. Numen only runs on the Titan 2 Elite,
 * so these pages are no longer in the settings tree; the on-screen and built-in keyboard
 * pages stay reachable from Keys & modifiers only while one of their values differs from
 * the default (see [HiddenKeyboardSettings]). [Main] now shows Keys & modifiers.
 */
enum class KeyboardsDevicesDestination { Main, OnScreen, BuiltIn }

/**
 * Settings for switching between physical and on-screen keyboards, or for other devices.
 * The keyboard still reads them, but the settings UI only shows a row while its saved
 * value differs from the default, so a user who changed it can change it back.
 * Nothing here ever writes a preference.
 */
internal object HiddenKeyboardSettings {
    fun keyboardSwitchingChanged(context: Context): Boolean =
        SettingsManager.getSoftwareKeyboardMode(context) != SettingsManager.SoftwareKeyboardMode.AUTO

    fun switchMessageChanged(context: Context): Boolean =
        !SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context)

    fun onScreenKeyboardChanged(context: Context): Boolean =
        SettingsManager.getSoftwareKeyboardLayoutStyle(context) != SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT ||
            !SettingsManager.getSoftwareKeyboardNumberRowEnabled(context) ||
            !SettingsManager.getSoftwareKeyboardNearestKeyTouchEnabled(context) ||
            !SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context) ||
            !SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context) ||
            SettingsManager.getSoftwareKeyboardLeftModifierKey(context) != SettingsManager.SoftwareKeyboardModifierKey.CTRL ||
            SettingsManager.getSoftwareKeyboardRightModifierKey(context) != SettingsManager.SoftwareKeyboardModifierKey.ALT

    fun keyboardProfileChanged(context: Context): Boolean =
        SettingsManager.getPhysicalKeyboardProfileOverride(context) != "auto"

    /** The default follows the device (on for a Titan 2), so compare with that. */
    fun titan2LayoutChanged(context: Context): Boolean =
        SettingsManager.isTitan2LayoutEnabled(context) != DeviceSpecific.isTitan2Device()

    fun builtInKeyboardChanged(context: Context): Boolean =
        keyboardProfileChanged(context) || titan2LayoutChanged(context)

    fun anyChanged(context: Context): Boolean =
        keyboardSwitchingChanged(context) ||
            switchMessageChanged(context) ||
            onScreenKeyboardChanged(context) ||
            builtInKeyboardChanged(context)
}
