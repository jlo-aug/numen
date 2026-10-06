package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingAwareSwipeDetectorTest {
    private val detector = TypingAwareSwipeDetector(slopPx = { 20f })

    @Test
    fun aSwipeWellAfterTypingCounts() {
        detector.onKeyDown(KEY_A, 1_000)
        detector.onKeyUp(KEY_A, 1_080)
        detector.onTouchDown(100f, 100f)
        assertFalse(detector.onTouchMove(110f, 100f, 2_000))
        assertTrue(detector.onTouchMove(100f, 140f, 2_016))
        // Only once per touch.
        assertFalse(detector.onTouchMove(100f, 180f, 2_032))
    }

    @Test
    fun fingersSlidingWhileAKeyIsDownNeverCount() {
        detector.onTouchDown(100f, 100f)
        detector.onKeyDown(KEY_A, 5_000)
        assertFalse(detector.onTouchMove(160f, 100f, 5_050))
        assertFalse(detector.onTouchMove(220f, 100f, 5_100))
    }

    @Test
    fun slidingJustAfterAKeyIsReleasedNeverCounts() {
        detector.onTouchDown(100f, 100f)
        detector.onKeyDown(KEY_A, 5_000)
        detector.onKeyUp(KEY_A, 5_080)
        assertFalse(detector.onTouchMove(160f, 100f, 5_200))
    }

    @Test
    fun typingJitterDoesNotAddUpToASwipeOnceTypingStops() {
        detector.onTouchDown(100f, 100f)
        // Many small slides across a burst of keystrokes...
        var x = 100f
        var t = 5_000L
        repeat(10) {
            detector.onKeyDown(KEY_A, t)
            x += 15f
            assertFalse(detector.onTouchMove(x, 100f, t + 40))
            detector.onKeyUp(KEY_A, t + 80)
            t += 200
        }
        // ...then the finger rests: measured from where typing stopped, it is not a swipe.
        assertFalse(detector.onTouchMove(x + 5f, 100f, t + 600))
        // A real swipe from there still counts.
        assertTrue(detector.onTouchMove(x + 5f, 160f, t + 620))
    }

    @Test
    fun aKeyWhoseReleaseWasLostStopsBlockingSwipes() {
        detector.onKeyDown(KEY_A, 1_000)
        detector.onTouchDown(100f, 100f)
        assertFalse(detector.onTouchMove(100f, 160f, 2_000))
        assertTrue(detector.onTouchMove(100f, 220f, 1_000 + TypingAwareSwipeDetector.HELD_KEY_LIMIT_MS + 1))
    }

    @Test
    fun movementWithoutATouchDownIsIgnored() {
        assertFalse(detector.onTouchMove(300f, 300f, 10_000))
        detector.onTouchDown(100f, 100f)
        detector.onTouchEnd()
        assertFalse(detector.onTouchMove(300f, 300f, 10_100))
    }

    private companion object {
        const val KEY_A = 29
    }
}
