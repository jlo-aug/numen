package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/**
 * "Ignore overlapping keys": a key pressed while another non-modifier key is still held is
 * dropped, along with its release. Fat-finger rolls on the small keyboard then type one letter.
 */
class OverlappingKeyFilter {
    data class SuppressedEvent(val identity: String) {
        fun debugOutput(): String = "accidental_keys:ignored:reason=overlapping_key:id=$identity"
    }

    private data class KeyIdentity(val deviceId: Int, val scanCode: Int, val keyCode: Int) {
        override fun toString(): String = "$deviceId:$scanCode:$keyCode"
    }

    private val heldByDevice = mutableMapOf<Int, MutableSet<KeyIdentity>>()
    private val suppressedKeyUps = mutableMapOf<KeyIdentity, SuppressedEvent>()

    fun shouldConsumeKeyDown(keyCode: Int, event: KeyEvent?, enabled: Boolean): SuppressedEvent? {
        if (event == null || isModifierKey(keyCode)) return null
        val identity = identityFor(keyCode, event)
        suppressedKeyUps[identity]?.let { return it }
        if (!enabled || event.repeatCount > 0) return null
        val held = heldByDevice.getOrPut(event.deviceId) { linkedSetOf() }
        if (held.any { it != identity }) {
            return SuppressedEvent(identity.toString()).also { suppressedKeyUps[identity] = it }
        }
        held += identity
        return null
    }

    /** The release of a suppressed key, which must be dropped too; null otherwise. */
    fun onKeyUp(keyCode: Int, event: KeyEvent?): SuppressedEvent? {
        if (event == null || isModifierKey(keyCode)) return null
        val identity = identityFor(keyCode, event)
        heldByDevice[identity.deviceId]?.let { held ->
            held.remove(identity)
            if (held.isEmpty()) heldByDevice.remove(identity.deviceId)
        }
        return suppressedKeyUps.remove(identity)
    }

    fun resetDevice(deviceId: Int) {
        heldByDevice.remove(deviceId)
        suppressedKeyUps.keys.removeAll { it.deviceId == deviceId }
    }

    fun reset() {
        heldByDevice.clear()
        suppressedKeyUps.clear()
    }

    private fun identityFor(keyCode: Int, event: KeyEvent) = KeyIdentity(event.deviceId, event.scanCode, keyCode)

    private fun isModifierKey(keyCode: Int): Boolean =
        KeyEvent.isModifierKey(keyCode) || keyCode == KeyEvent.KEYCODE_SYM || keyCode == KeyEvent.KEYCODE_FUNCTION
}
