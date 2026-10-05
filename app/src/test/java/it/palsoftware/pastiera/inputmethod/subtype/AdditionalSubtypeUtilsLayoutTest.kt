package it.palsoftware.pastiera.inputmethod.subtype

import android.view.KeyEvent
import it.palsoftware.pastiera.data.layout.JsonLayoutLoader
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdditionalSubtypeUtilsLayoutTest {

    @After
    fun tearDown() {
        LayoutFileStore.getLayoutsDirectory(RuntimeEnvironment.getApplication()).deleteRecursively()
        ShadowLog.clear()
    }

    @Test
    fun germanLocales_resolveToQwertz() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de", context))
        assertEquals("qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de_DE", context))
        assertEquals("qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de-AT", context))
    }

    @Test
    fun additionalSubtypes_skipBaseLocaleDuplicateLayout() {
        val context = RuntimeEnvironment.getApplication()

        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:qwerty",
            context.assets,
            context
        )

        assertEquals(0, subtypes.size)
    }

    @Test
    fun additionalSubtypes_keepSameLocaleDifferentLayout() {
        val context = RuntimeEnvironment.getApplication()

        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:vietnamese_telex_qwerty",
            context.assets,
            context
        )

        assertEquals(1, subtypes.size)
        assertEquals("vietnamese_telex_qwerty", AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtypes[0]))
    }

    @Test
    fun subtypeMatching_usesLocaleAndLayout() {
        val context = RuntimeEnvironment.getApplication()
        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:vietnamese_telex_qwerty",
            context.assets,
            context
        )

        assertEquals(
            true,
            AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(
                subtypes[0],
                "en_US",
                "vietnamese_telex_qwerty"
            )
        )
        assertEquals(
            false,
            AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(
                subtypes[0],
                "en_US",
                "qwerty"
            )
        )
    }

    @Test
    fun traversalLikeCustomDisplayNameNeverBecomesAssetPath() {
        val context = RuntimeEnvironment.getApplication()
        val layoutId = "../../Русский путь 273"
        val json = """{"name":"Русский путь 273","mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"}}}"""
        assertTrue(
            LayoutFileStore.saveLayoutFromJson(context, layoutId, json)
                is LayoutFileStore.LayoutImportResult.Success
        )
        ShadowLog.clear()

        val displayName = AdditionalSubtypeUtils.buildSubtypeDisplayName(
            context,
            context.assets,
            Locale.forLanguageTag("ru"),
            "ru",
            layoutId
        )
        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "ru:$layoutId",
            context.assets,
            context
        )
        val mapping = JsonLayoutLoader.loadLayout(context.assets, layoutId, context)

        assertTrue(displayName.endsWith(" · Русский путь 273"))
        assertEquals(1, subtypes.size)
        assertEquals(layoutId, AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtypes[0]))
        assertEquals("й", mapping?.get(KeyEvent.KEYCODE_Q)?.lowercase)
        assertFalse(
            ShadowLog.getLogsForTag("LayoutFileStore").any { log ->
                log.msg.contains("Error getting layout metadata from assets") ||
                    log.throwable is java.io.FileNotFoundException
            }
        )
    }
}
