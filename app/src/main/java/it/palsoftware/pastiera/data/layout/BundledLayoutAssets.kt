package it.palsoftware.pastiera.data.layout

import android.content.res.AssetManager
import java.io.InputStream

/** Resolves exact bundled layout IDs without treating user-controlled values as asset paths. */
internal object BundledLayoutAssets {
    private const val LAYOUTS_DIRECTORY = "common/layouts"

    fun openLayout(assets: AssetManager, layoutId: String): InputStream? {
        if (!hasStrictIdSyntax(layoutId)) return null

        val fileName = "$layoutId.json"
        val bundledFileNames = assets.list(LAYOUTS_DIRECTORY) ?: return null
        if (fileName !in bundledFileNames) return null

        return assets.open("$LAYOUTS_DIRECTORY/$fileName")
    }

    internal fun hasStrictIdSyntax(layoutId: String): Boolean =
        layoutId.isNotBlank() && layoutId.none { character ->
            character == '/' ||
                character == '\\' ||
                character == '.' ||
                character.isISOControl()
        }
}
