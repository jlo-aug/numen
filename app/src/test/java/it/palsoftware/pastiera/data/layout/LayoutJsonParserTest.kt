package it.palsoftware.pastiera.data.layout

import android.view.KeyEvent
import it.palsoftware.pastiera.data.layout.LayoutJsonParser.LayoutParseError
import it.palsoftware.pastiera.data.layout.LayoutJsonParser.LayoutParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutJsonParserTest {

    @Test
    fun bundledQwerty_loads() {
        val context = RuntimeEnvironment.getApplication()

        val layout = JsonLayoutLoader.loadLayout(context.assets, "qwerty")

        assertEquals("q", layout?.get(KeyEvent.KEYCODE_Q)?.lowercase)
        assertEquals(listOf("qwerty"), JsonLayoutLoader.getAvailableLayouts(context.assets))
    }

    @Test
    fun validSupportedMapping_parses() {
        val layout = LayoutJsonParser.loadLayoutFromStream(
            """{"name":"Fixture","mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"}}}""".byteInputStream()
        )

        assertEquals(LayoutMapping("й", "Й"), layout?.get(KeyEvent.KEYCODE_Q))
    }

    @Test
    fun invalidJson_returnsNull() {
        assertNull(LayoutJsonParser.loadLayoutFromStream("{".byteInputStream()))
    }

    @Test
    fun malformedJson_isRejected() {
        assertRejected("{", LayoutParseError.MALFORMED_JSON)
    }

    @Test
    fun missingMappings_isRejected() {
        assertRejected("""{"name":"Missing"}""", LayoutParseError.MISSING_MAPPINGS)
    }

    @Test
    fun nonObjectMappings_isRejected() {
        assertRejected("""{"mappings":[]}""", LayoutParseError.MAPPINGS_NOT_OBJECT)
    }

    @Test
    fun emptyMappings_isRejected() {
        assertRejected("""{"mappings":{}}""", LayoutParseError.EMPTY_MAPPINGS)
    }

    @Test
    fun onlyUnknownKeyCodes_areRejected() {
        assertRejected(
            """{"mappings":{"KEYCODE_FUTURE":{"lowercase":"x","uppercase":"X"}}}""",
            LayoutParseError.NO_SUPPORTED_MAPPINGS
        )
    }

    @Test
    fun incompleteSupportedMapping_isRejected() {
        assertRejected(
            """{"mappings":{"KEYCODE_Q":{"lowercase":"й"}}}""",
            LayoutParseError.INVALID_MAPPING
        )
    }

    @Test
    fun invalidSupportedMapping_rejectsWholeFileEvenAlongsideValidMapping() {
        assertRejected(
            """{"mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"},"KEYCODE_W":{"lowercase":"ц","uppercase":3}}}""",
            LayoutParseError.INVALID_MAPPING
        )
    }

    private fun assertRejected(json: String, expected: LayoutParseError) {
        val result = LayoutJsonParser.validateLayoutJson(json)

        assertTrue(result is LayoutParseResult.Failure)
        assertEquals(expected, (result as LayoutParseResult.Failure).error)
    }
}
