package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoftwareKeyboardDeviceTransitionPolicyTest {
    @Test
    fun aKeyboardConnectingSwitchesAutoModeToHardware() {
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            plan(
                previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
                autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            )
        )
    }

    @Test
    fun persistentConfiguredModeBecomesBaseAfterDeviceChange() {
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            plan(
                configuredMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
                previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
                autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            )
        )
    }

    @Test
    fun irrelevantInputDeviceNotificationDoesNothing() {
        assertNull(
            plan(
                previousAutoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
                autoMode = SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
            )
        )
    }

    private fun plan(
        configuredMode: SettingsManager.SoftwareKeyboardMode = SettingsManager.SoftwareKeyboardMode.AUTO,
        previousAutoMode: SettingsManager.SoftwareKeyboardMode?,
        autoMode: SettingsManager.SoftwareKeyboardMode
    ) = SoftwareKeyboardDeviceTransitionPolicy.plan(configuredMode, previousAutoMode, autoMode)
}
