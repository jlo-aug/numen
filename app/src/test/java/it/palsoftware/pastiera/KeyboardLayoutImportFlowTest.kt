package it.palsoftware.pastiera

import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportError
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardLayoutImportFlowTest {

    @Test
    fun nullEmptyAndBlankDocumentContent_reportMalformedJson() {
        val context = RuntimeEnvironment.getApplication()

        listOf<String?>(null, "", " \n\t").forEach { content ->
            assertEquals(
                LayoutImportResult.Failure(LayoutImportError.MALFORMED_JSON),
                importKeyboardLayoutDocument(context, content)
            )
        }
    }
}
