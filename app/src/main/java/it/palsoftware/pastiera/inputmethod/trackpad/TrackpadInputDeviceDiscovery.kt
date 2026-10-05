package it.palsoftware.pastiera.inputmethod.trackpad

data class TrackpadInputDevice(
    val path: String,
    val name: String,
    val xRange: TrackpadAxisRange?,
    val yRange: TrackpadAxisRange?,
    val hasBtnTouch: Boolean
) {
    val hasTrackpadAxes: Boolean
        get() = xRange?.isValid == true && yRange?.isValid == true

    val displayName: String
        get() = if (name.isBlank()) path else "$name — $path"
}

object TrackpadInputDeviceDiscovery {
    private val deviceHeader = Regex("^add device \\d+:\\s+(\\S+)")
    private val deviceName = Regex("name:\\s+\"([^\"]+)\"")
    private val axisRange = Regex("min\\s+(-?\\d+),\\s+max\\s+(-?\\d+)")

    fun parseGeteventCapabilities(output: String): List<TrackpadInputDevice> {
        val devices = mutableListOf<TrackpadInputDevice>()
        var current: MutableDevice? = null

        fun finishCurrent() {
            current?.toImmutable()?.let(devices::add)
            current = null
        }

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val header = deviceHeader.find(trimmed)
            if (header != null) {
                finishCurrent()
                current = MutableDevice(path = header.groupValues[1])
                return@forEach
            }

            val device = current ?: return@forEach
            deviceName.find(trimmed)?.let { match ->
                device.name = match.groupValues[1]
            }
            if (trimmed.contains("BTN_TOUCH")) {
                device.hasBtnTouch = true
            }
            when {
                trimmed.contains("ABS_MT_POSITION_X") -> {
                    device.xRange = parseAxisRange(trimmed)
                }
                trimmed.contains("ABS_MT_POSITION_Y") -> {
                    device.yRange = parseAxisRange(trimmed)
                }
            }
        }
        finishCurrent()
        return devices
    }

    fun selectableDevices(devices: List<TrackpadInputDevice>): List<TrackpadInputDevice> {
        return devices
            .filter { it.hasTrackpadAxes }
            .sortedWith(
                compareByDescending<TrackpadInputDevice>(::candidateScore)
                    .thenBy { it.path }
            )
    }

    fun selectAutomatic(devices: List<TrackpadInputDevice>): TrackpadInputDevice? {
        return selectableDevices(devices).firstOrNull { candidateScore(it) >= 250 }
    }

    private fun parseAxisRange(line: String): TrackpadAxisRange? {
        val match = axisRange.find(line) ?: return null
        val min = match.groupValues[1].toFloatOrNull() ?: return null
        val max = match.groupValues[2].toFloatOrNull() ?: return null
        return TrackpadAxisRange(min, max).takeIf { it.isValid }
    }

    private fun candidateScore(device: TrackpadInputDevice): Int {
        val normalizedName = device.name.lowercase().replace("_", "")
        var score = 0
        if (device.hasTrackpadAxes) score += 100
        if (device.hasBtnTouch) score += 30
        score += when {
            normalizedName == "touchpad" -> 200
            normalizedName == "subtouch" -> 180
            normalizedName.contains("touchpad") -> 160
            normalizedName.contains("trackpad") -> 150
            else -> 0
        }
        return score
    }

    private data class MutableDevice(
        val path: String,
        var name: String = "",
        var xRange: TrackpadAxisRange? = null,
        var yRange: TrackpadAxisRange? = null,
        var hasBtnTouch: Boolean = false
    ) {
        fun toImmutable(): TrackpadInputDevice = TrackpadInputDevice(
            path = path,
            name = name,
            xRange = xRange,
            yRange = yRange,
            hasBtnTouch = hasBtnTouch
        )
    }
}
