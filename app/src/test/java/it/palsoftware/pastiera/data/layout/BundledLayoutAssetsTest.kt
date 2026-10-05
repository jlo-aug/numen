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
            "../qwertz",
            "..\\qwertz",
            ".",
            "..",
            "qwertz.variant",
            "qwertz\u0000"
        ).forEach { layoutId ->
            assertNull(BundledLayoutAssets.openLayout(assets, layoutId))
        }

        verifyNoInteractions(assets)
    }

    @Test
    fun exactRealAssetNameIsOpened() {
        `when`(assets.list("common/layouts")).thenReturn(arrayOf("qwertz.json", "qwerty.json"))
        `when`(assets.open("common/layouts/qwertz.json"))
            .thenReturn(ByteArrayInputStream("{}".toByteArray()))

        assertNotNull(BundledLayoutAssets.openLayout(assets, "qwertz"))

        verify(assets).open("common/layouts/qwertz.json")
    }

    @Test
    fun syntacticallyValidButUnknownIdIsNotOpened() {
        `when`(assets.list("common/layouts")).thenReturn(arrayOf("qwertz.json"))

        assertNull(BundledLayoutAssets.openLayout(assets, "not_bundled"))

        verify(assets, never()).open("common/layouts/not_bundled.json")
    }
}
