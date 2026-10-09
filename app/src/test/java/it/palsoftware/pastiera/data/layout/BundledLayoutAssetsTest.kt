package it.palsoftware.pastiera.data.layout

import android.content.res.AssetManager
import java.io.ByteArrayInputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class BundledLayoutAssetsTest {
    private val assets = mock(AssetManager::class.java)

    @Test
    fun unsafeIdsNeverConsultOrOpenAssets() {
        listOf(
            "../qwerty",
            "..\\qwerty",
            ".",
            "..",
            "qwerty.variant",
            "qwerty\u0000"
        ).forEach { layoutId ->
            assertNull(BundledLayoutAssets.openLayout(assets, layoutId))
        }

        verifyNoInteractions(assets)
    }

    @Test
    fun exactRealAssetNameIsOpened() {
        `when`(assets.list("common/layouts")).thenReturn(arrayOf("qwerty.json"))
        `when`(assets.open("common/layouts/qwerty.json"))
            .thenReturn(ByteArrayInputStream("{}".toByteArray()))

        assertNotNull(BundledLayoutAssets.openLayout(assets, "qwerty"))

        verify(assets).open("common/layouts/qwerty.json")
    }

    @Test
    fun syntacticallyValidButUnknownIdIsNotOpened() {
        `when`(assets.list("common/layouts")).thenReturn(arrayOf("qwerty.json"))

        assertNull(BundledLayoutAssets.openLayout(assets, "not_bundled"))

        verify(assets, never()).open("common/layouts/not_bundled.json")
    }
}
