package it.palsoftware.pastiera.inputmethod

/**
 * Tells a deliberate swipe on the Titan 2 keyboard surface apart from fingers sliding while
 * typing. The keys are the touchpad, so every keystroke also moves it a little: movement while a
 * key is down, or just after one is released, never counts. A swipe is measured from where the
 * finger was once typing stopped.
 */
class TypingAwareSwipeDetector(
    private val slopPx: () -> Float,
    private val quietAfterKeyMs: Long = QUIET_AFTER_KEY_MS
) {
    // Key down times. A key whose release never arrived stops counting after HELD_KEY_LIMIT_MS.
    private val keysDown = mutableMapOf<Int, Long>()
    private var lastKeyUpAt = Long.MIN_VALUE / 2
    private var start: Pair<Float, Float>? = null

    fun onKeyDown(keyCode: Int, eventTime: Long) {
        keysDown.putIfAbsent(keyCode, eventTime)
    }

    fun onKeyUp(keyCode: Int, eventTime: Long) {
        keysDown.remove(keyCode)
        lastKeyUpAt = eventTime
    }

    fun onTouchDown(x: Float, y: Float) {
        start = x to y
    }

    /** Returns true once, on the move that turns this touch into a swipe. */
    fun onTouchMove(x: Float, y: Float, eventTime: Long): Boolean {
        if (isTyping(eventTime)) {
            // Follow the finger so typing jitter never adds up to a swipe.
            if (start != null) start = x to y
            return false
        }
        val (startX, startY) = start ?: return false
        if (kotlin.math.hypot(x - startX, y - startY) <= slopPx()) return false
        start = null
        return true
    }

    fun onTouchEnd() {
        start = null
    }

    private fun isTyping(now: Long): Boolean =
        now - lastKeyUpAt < quietAfterKeyMs || keysDown.values.any { now - it < HELD_KEY_LIMIT_MS }

    companion object {
        const val QUIET_AFTER_KEY_MS = 400L
        const val HELD_KEY_LIMIT_MS = 2_000L
    }
}
