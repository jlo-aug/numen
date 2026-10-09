package it.palsoftware.pastiera

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import java.io.File

class PastieraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.enforceTitan2EliteRoundedCornersOnce(this)
        SettingsManager.migrateKeyboardThemeLayoutOverrides(this)
        AppPackageChangeMonitor.register(this)
        publishSoftwareKeyboardModeShortcut()
        Handler(Looper.getMainLooper()).post {
            AdditionalSubtypeUtils.clearAdditionalSubtypes(this)
        }
        Thread(::deleteLeftoverFiles).start()
    }

    /**
     * Removes what earlier versions stored for other languages and layouts: downloaded or imported
     * dictionaries (one could replace the English one) and custom layouts. Nothing reads them now.
     */
    private fun deleteLeftoverFiles() {
        LEFTOVER_FILES.map { File(filesDir, it) }.filter { it.exists() }.forEach { it.deleteRecursively() }
    }

    private fun publishSoftwareKeyboardModeShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(this, SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID)
            .setShortLabel(getString(R.string.software_keyboard_mode_toggle_shortcut_short))
            .setLongLabel(getString(R.string.software_keyboard_mode_toggle_shortcut_long))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(SoftwareKeyboardModeActions.ACTION_TOGGLE)
                    .setClass(this, SoftwareKeyboardModeActionActivity::class.java)
            )
            .build()
        runCatching {
            shortcutManager.removeDynamicShortcuts(listOf(SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID))
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        }
    }

    companion object {
        private const val SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID = "software_keyboard_mode_toggle"
        private val LEFTOVER_FILES = listOf("dictionaries_serialized", "keyboard_layouts", "locale_layout_mapping.json")
    }
}
