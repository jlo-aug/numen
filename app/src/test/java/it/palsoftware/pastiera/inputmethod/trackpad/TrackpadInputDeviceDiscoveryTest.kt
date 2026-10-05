package it.palsoftware.pastiera.inputmethod.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadInputDeviceDiscoveryTest {
    private val capabilities = """
        add device 1: /dev/input/event7
          name:     "fts_ts"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_POSITION_X : value 0, min 0, max 1079, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y : value 0, min 0, max 1199, fuzz 0, flat 0, resolution 0
        add device 2: /dev/input/event6
          name:     "touchPad"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_POSITION_X : value 0, min 10, max 1010, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y : value 0, min 20, max 1020, fuzz 0, flat 0, resolution 0
        add device 3: /dev/input/event0
          name:     "TitanKey"
          events:
            KEY (0001): 001e 001f 0020
    """.trimIndent()

    @Test
    fun `parses event nodes names capabilities and ranges`() {
        val devices = TrackpadInputDeviceDiscovery.parseGeteventCapabilities(capabilities)

        assertEquals(3, devices.size)
        val trackpad = devices.first { it.path == "/dev/input/event6" }
        assertEquals("touchPad", trackpad.name)
        assertEquals(TrackpadAxisRange(10f, 1010f), trackpad.xRange)
        assertEquals(TrackpadAxisRange(20f, 1020f), trackpad.yRange)
        assertTrue(trackpad.hasBtnTouch)
    }

    @Test
    fun `automatic selection prefers named trackpad over touchscreen`() {
        val devices = TrackpadInputDeviceDiscovery.parseGeteventCapabilities(capabilities)

        assertEquals(
            "/dev/input/event6",
            TrackpadInputDeviceDiscovery.selectAutomatic(devices)?.path
        )
    }

    @Test
    fun `manual choices include every device with position axes`() {
        val devices = TrackpadInputDeviceDiscovery.parseGeteventCapabilities(capabilities)

        assertEquals(
            listOf("/dev/input/event6", "/dev/input/event7"),
            TrackpadInputDeviceDiscovery.selectableDevices(devices).map { it.path }
        )
    }

    @Test
    fun `automatic selection rejects unnamed touchscreen-like device`() {
        val devices = TrackpadInputDeviceDiscovery.parseGeteventCapabilities(
            """
                add device 1: /dev/input/event4
                  name:     "fts_ts"
                  events:
                    KEY (0001): BTN_TOUCH
                    ABS (0003): ABS_MT_POSITION_X : value 0, min 0, max 1079
                                ABS_MT_POSITION_Y : value 0, min 0, max 1199
            """.trimIndent()
        )

        assertEquals(null, TrackpadInputDeviceDiscovery.selectAutomatic(devices))
    }
}
