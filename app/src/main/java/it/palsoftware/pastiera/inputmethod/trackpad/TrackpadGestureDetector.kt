package it.palsoftware.pastiera.inputmethod.trackpad

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Listens to trackpad events via Shizuku and triggers callbacks on swipe.
 * Keeps gesture logic isolated so the IME can stay lean and only react to events.
 */
class TrackpadGestureDetector(
    private val isEnabled: () -> Boolean,
    private val onSwipeUp: (third: Int) -> Unit,
    private val scope: CoroutineScope,
    private val eventDeviceSelection: String = AUTO_EVENT_DEVICE,
    private val fallbackEventDevice: String = DEFAULT_EVENT_DEVICE,
    private val trackpadMaxX: Int = DEFAULT_TRACKPAD_MAX_X,
    private val swipeUpThreshold: Int = DEFAULT_SWIPE_UP_THRESHOLD,
    private val minVelocityThreshold: Double = DEFAULT_MIN_VELOCITY_THRESHOLD,
    private val logTag: String = DEFAULT_LOG_TAG,
    private val shizukuPing: () -> Boolean = { 
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
    }
) {

    private var geteventJob: Job? = null
    @Volatile
    private var geteventProcess: Process? = null
    private var touchDown = false
    private var startX = 0
    private var startY = 0
    private var currentX = 0
    private var currentY = 0
    private var startXSet = false
    private var startYSet = false
    private var trackpadXRange = TrackpadAxisRange(0f, trackpadMaxX.toFloat())
    private var startTime: Long = 0
    private var endTime: Long = 0

    fun start() {
        // Guard: if already running, do nothing
        if (isRunning()) {
            Log.d(DEBUG_TAG, "start() SKIPPED: detector already running")
            return
        }
        
        val enabled = isEnabled()
        Log.d(DEBUG_TAG, "start() called - isEnabled=$enabled, swipeUpThreshold=$swipeUpThreshold, eventDeviceSelection=$eventDeviceSelection")
        
        if (!enabled) {
            Log.d(DEBUG_TAG, "start() ABORTED: gestures disabled in settings")
            Log.d(logTag, "Trackpad gestures disabled in settings")
            return
        }

        val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
        val shizukuAuthorized = try { 
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
        } catch (e: Exception) { false }
        val shizukuAvailable = shizukuRunning && shizukuAuthorized
        Log.d(DEBUG_TAG, "start() Shizuku status: running=$shizukuRunning, authorized=$shizukuAuthorized, available=$shizukuAvailable")
        
        if (!shizukuAvailable) {
            val reason = when {
                !shizukuRunning -> "Shizuku not running"
                !shizukuAuthorized -> "App not authorized in Shizuku"
                else -> "Unknown"
            }
            Log.d(DEBUG_TAG, "start() ABORTED: $reason")
            Log.w(logTag, "Shizuku not available ($reason), trackpad gesture detection disabled")
            return
        }

        geteventJob?.cancel()
        Log.d(DEBUG_TAG, "start() launching getevent coroutine...")
        geteventJob = scope.launch(Dispatchers.IO) {
            try {
                val discoveredDevices = runCatching {
                    ShizukuTrackpadDeviceDiscovery.discoverBlocking()
                }.onFailure { error ->
                    Log.w(DEBUG_TAG, "Unable to discover trackpad event devices", error)
                }.getOrDefault(emptyList())
                val selectedDevice = if (eventDeviceSelection == AUTO_EVENT_DEVICE) {
                    TrackpadInputDeviceDiscovery.selectAutomatic(discoveredDevices)
                } else {
                    discoveredDevices.firstOrNull { it.path == eventDeviceSelection }
                }
                val resolvedEventDevice = when {
                    eventDeviceSelection != AUTO_EVENT_DEVICE -> eventDeviceSelection
                    selectedDevice != null -> selectedDevice.path
                    else -> fallbackEventDevice
                }
                selectedDevice?.xRange?.takeIf { it.isValid }?.let { trackpadXRange = it }

                Log.d(
                    DEBUG_TAG,
                    "Invoking Shizuku getevent for $resolvedEventDevice " +
                        "(selection=$eventDeviceSelection, detected=${selectedDevice?.displayName}, " +
                        "xRange=${trackpadXRange.min}..${trackpadXRange.max}, fallback=$fallbackEventDevice)"
                )
                val process = ShizukuTrackpadDeviceDiscovery.startProcess(
                    arrayOf("getevent", "-l", resolvedEventDevice)
                )
                geteventProcess = process

                try {
                    Log.d(DEBUG_TAG, "getevent process started successfully, reading events...")
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            parseTrackpadEvent(line)
                        }
                    }
                } finally {
                    process.destroy()
                    if (geteventProcess === process) {
                        geteventProcess = null
                    }
                }
                Log.d(DEBUG_TAG, "getevent reader loop ended")
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "getevent coroutine FAILED: ${e.message}", e)
                Log.e(logTag, "Trackpad getevent failed", e)
            }
        }
        Log.d(DEBUG_TAG, "start() completed - getevent job launched")
        Log.d(logTag, "Trackpad gesture detection started")
    }

    fun stop() {
        Log.d(DEBUG_TAG, "stop() called - had active job: ${geteventJob != null}")
        geteventProcess?.destroy()
        geteventProcess = null
        geteventJob?.cancel()
        geteventJob = null
        Log.d(logTag, "Trackpad gesture detection stopped")
    }

    /**
     * Returns true if the detector is currently running (has an active getevent job).
     */
    fun isRunning(): Boolean {
        return geteventJob != null && geteventJob?.isActive == true
    }

    private fun parseTrackpadEvent(line: String) {
        when {
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                touchDown = true
                startXSet = false
                startYSet = false
                startTime = System.nanoTime()
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                if (touchDown && startXSet && startYSet) {
                    endTime = System.nanoTime()
                    detectGesture()
                }
                touchDown = false
                startXSet = false
                startYSet = false
            }

            line.contains("ABS_MT_POSITION_X") -> {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val hexValue = parts.last()
                    val newX = hexValue.toIntOrNull(16)
                    if (newX != null) {
                        currentX = newX
                        if (touchDown && !startXSet) {
                            startX = newX
                            startXSet = true
                        }
                    }
                }
            }

            line.contains("ABS_MT_POSITION_Y") -> {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val hexValue = parts.last()
                    val newY = hexValue.toIntOrNull(16)
                    if (newY != null) {
                        currentY = newY
                        if (touchDown && !startYSet) {
                            startY = newY
                            startYSet = true
                        }
                    }
                }
            }
        }
    }

    private fun detectGesture() {
        val deltaY = startY - currentY  // Positive = swipe up
        val deltaX = currentX - startX
        val absDeltaX = kotlin.math.abs(deltaX)

        // Calculate duration in milliseconds
        val durationMs = (endTime - startTime) / 1_000_000.0
        
        // Calculate velocity (pixels per millisecond)
        val velocity = if (durationMs > 0) deltaY / durationMs else 0.0

        // Require primarily vertical swipe: deltaY must be at least 5x larger than horizontal drift
        // AND velocity must exceed minimum threshold
        if (deltaY > swipeUpThreshold && absDeltaX < deltaY / 4 && velocity >= minVelocityThreshold) {
            val third = TrackpadCoordinateMapper.third(startX.toFloat(), trackpadXRange)

            Log.d(
                logTag,
                ">>> SWIPE UP DETECTED in third $third (deltaY=$deltaY, absDeltaX=$absDeltaX, velocity=${String.format("%.2f", velocity)} px/ms, duration=${String.format("%.1f", durationMs)}ms, startX=$startX, xRange=${trackpadXRange.min}..${trackpadXRange.max}) <<<"
            )
            onSwipeUp(third)
        }
    }

    companion object {
        const val DEFAULT_TRACKPAD_MAX_X = 1440
        const val DEFAULT_SWIPE_UP_THRESHOLD = 300
        const val DEFAULT_MIN_VELOCITY_THRESHOLD = 2.0  // pixels per millisecond (e.g., 1.0 px/ms = 1000 px/s)
        const val DEFAULT_EVENT_DEVICE = TrackpadEventDeviceResolver.LEGACY_EVENT_DEVICE
        const val AUTO_EVENT_DEVICE = "auto"
        const val DEFAULT_LOG_TAG = "PastieraIME"
        private const val DEBUG_TAG = "TrackpadDebug"
    }
}
