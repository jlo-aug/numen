package it.palsoftware.pastiera.data.variation

import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VariationRepositoryTest {

    @After
    fun tearDown() {
        File(RuntimeEnvironment.getApplication().filesDir, "variations.json").delete()
    }

    @Test
    fun saveStaticVariationBasePreset_updatesOnlyBaseStaticRow() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "variations.json").writeText(
            """
            {
              "variations": {
                "a": ["á"]
              },
              "staticVariationsShift": ["{"],
              "staticVariationsAlt": ["<"]
            }
            """.trimIndent()
        )

        SettingsManager.saveStaticVariationBasePreset(
            context,
            SettingsManager.getDevChoiceStaticVariationBasePreset()
        )

        assertEquals(
            listOf("»", "«", ";", "!", "?", ",", ".", "–", "%"),
            VariationRepository.loadStaticVariations(context.assets, context)
        )
        assertEquals(listOf("{"), VariationRepository.loadStaticVariationsShift(context.assets, context))
        assertEquals(listOf("<"), VariationRepository.loadStaticVariationsAlt(context.assets, context))
        assertEquals(listOf("á"), VariationRepository.loadVariations(context.assets, context)['a'])
    }
}
