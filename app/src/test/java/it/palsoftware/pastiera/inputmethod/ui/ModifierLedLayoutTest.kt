package it.palsoftware.pastiera.inputmethod.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierLedLayoutTest {
    @Test
    fun titan2EliteMirrorsCoupledShiftAtBothOuterBottomPositions() {
        val shifts = ModifierLedLayouts.TITAN_2_ELITE.segments
            .filter { it.state == ModifierLedState.SHIFT }

        assertEquals(2, shifts.size)
        assertTrue(shifts.all { it.y > 0.5f })
        assertTrue(shifts.minOf { it.x } < 0.02f)
        assertTrue(shifts.maxOf { it.x + it.width } > 0.98f)
    }

    @Test
    fun titan2ElitePlacesAltAndSymAboveShiftAndCtrlAtFnPosition() {
        val segments = ModifierLedLayouts.TITAN_2_ELITE.segments
        val alt = segments.single { it.state == ModifierLedState.ALT }
        val sym = segments.single { it.state == ModifierLedState.SYM }
        val ctrl = segments.single { it.state == ModifierLedState.CTRL }
        val rightShift = segments.filter { it.state == ModifierLedState.SHIFT }.maxBy { it.x }

        assertTrue(alt.x < 0.02f)
        assertTrue(alt.y < 0.5f)
        assertTrue(sym.x > 0.7f)
        assertTrue(sym.y < 0.5f)
        assertTrue(ctrl.y > 0.5f)
        assertTrue(ctrl.x < rightShift.x)
        assertTrue(ctrl.x + ctrl.width <= rightShift.x)
    }

    @Test
    fun explicitPhysicalProfileControlsPresetAndAutoUsesDetection() {
        assertSame(
            ModifierLedLayouts.TITAN_2_ELITE,
            ModifierLedLayouts.resolve("titan2elite_qwerty", titan2EliteAutoDetected = false)
        )
        assertSame(
            ModifierLedLayouts.TITAN_2_ELITE,
            ModifierLedLayouts.resolve("auto", titan2EliteAutoDetected = true)
        )
        assertSame(
            ModifierLedLayouts.DEFAULT,
            ModifierLedLayouts.resolve("titan2", titan2EliteAutoDetected = true)
        )
    }
}
