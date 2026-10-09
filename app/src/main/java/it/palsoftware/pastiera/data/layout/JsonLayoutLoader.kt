package it.palsoftware.pastiera.data.layout

import android.content.res.AssetManager
import android.util.Log

/**
 * Loads layout mappings from the bundled JSON assets.
 */
object JsonLayoutLoader {
    private const val TAG = "JsonLayoutLoader"

    fun loadLayout(
        assets: AssetManager,
        layoutName: String
    ): Map<Int, LayoutMapping>? {
        return try {
            val inputStream = BundledLayoutAssets.openLayout(assets, layoutName) ?: return null
            inputStream.use {
                LayoutJsonParser.loadLayoutFromStream(it)
            }?.also {
                Log.d(TAG, "Loaded layout from assets: $layoutName with ${it.size} mappings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from assets: $layoutName", e)
            null
        }
    }

    fun getAvailableLayouts(assets: AssetManager): List<String> {
        return try {
            assets.list("common/layouts")
                .orEmpty()
                .filter { it.endsWith(".json") }
                .map { it.removeSuffix(".json") }
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available layouts from assets", e)
            emptyList()
        }
    }
}
