package it.palsoftware.pastiera.inputmethod.trackpad

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackpadCoordinateMapperTest {
    @Test
    fun `maps thirds using actual 1080 coordinate range`() {
        val range = TrackpadAxisRange(min = 0f, max = 1080f)

        assertEquals(0, TrackpadCoordinateMapper.third(359f, range))
        assertEquals(1, TrackpadCoordinateMapper.third(360f, range))
        assertEquals(1, TrackpadCoordinateMapper.third(719f, range))
        assertEquals(2, TrackpadCoordinateMapper.third(720f, range))
        assertEquals(2, TrackpadCoordinateMapper.third(1003f, range))
    }

    @Test
    fun `normalizes ranges with a non-zero minimum`() {
        val range = TrackpadAxisRange(min = 100f, max = 1000f)

        assertEquals(0f, TrackpadCoordinateMapper.normalized(100f, range), 0.0001f)
        assertEquals(0.5f, TrackpadCoordinateMapper.normalized(550f, range), 0.0001f)
        assertEquals(1f, TrackpadCoordinateMapper.normalized(1000f, range), 0.0001f)
        assertEquals(0, TrackpadCoordinateMapper.third(399f, range))
        assertEquals(1, TrackpadCoordinateMapper.third(400f, range))
        assertEquals(2, TrackpadCoordinateMapper.third(700f, range))
    }

    @Test
    fun `clamps coordinates outside of range`() {
        val range = TrackpadAxisRange(min = 10f, max = 1010f)

        assertEquals(0f, TrackpadCoordinateMapper.normalized(-50f, range), 0.0001f)
        assertEquals(1f, TrackpadCoordinateMapper.normalized(2000f, range), 0.0001f)
    }

    @Test
    fun `Titan 2 Elite debug sample reaches all thirds with 1080 range`() {
        val acceptedStartCoordinates = listOf(
            487f, 144f, 532f, 840f, 470f, 444f, 166f, 527f, 560f,
            214f, 793f, 860f, 830f, 874f, 152f, 102f, 551f, 516f
        )

        val distribution = acceptedStartCoordinates
            .groupingBy { TrackpadCoordinateMapper.third(it, TrackpadAxisRange(0f, 1080f)) }
            .eachCount()

        assertEquals(mapOf(0 to 5, 1 to 8, 2 to 5), distribution)
    }
}
