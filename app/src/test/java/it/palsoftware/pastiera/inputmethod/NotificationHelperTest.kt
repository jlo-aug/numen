package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.os.VibratorManager
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationHelperTest {

    private val context
        get() = RuntimeEnvironment.getApplication() as android.app.Application

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().clear().commit()
        ShadowVibrator.reset()
    }

    @Test
    fun defaultHapticUsesConfiguredTapDuration() {
        SettingsManager.setTapHapticDurationMs(context, 45L)
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
        val shadowVibrator = Shadows.shadowOf(vibrator).apply { setHasVibrator(true) }

        NotificationHelper.triggerHapticFeedback(context)

        assertEquals(45L, shadowVibrator.milliseconds)
    }
}
