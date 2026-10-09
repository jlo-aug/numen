package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.Log
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.commands.CommandExecutor
import it.palsoftware.pastiera.commands.CommandKind
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandTarget
import it.palsoftware.pastiera.commands.PastieraCommandSource

/**
 * Controller for handling launcher shortcuts functionality.
 * Manages app launching, launcher detection, and shortcut assignment dialogs.
 */
class LauncherShortcutController(
    private val context: Context
) {
    companion object {
        private const val TAG = "PastieraInputMethod"
        private const val POWER_SHORTCUT_TIMEOUT_MS = 5000L // 5-second timeout
    }

    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // Power Shortcuts state: SYM pressed to arm a shortcut
    private var powerShortcutSymPressed: Boolean = false
    private var powerShortcutTimeoutHandler: android.os.Handler? = null
    private var powerShortcutTimeoutRunnable: Runnable? = null
    private var powerShortcutToastRunnable: Runnable? = null
    
    // State for handling nav mode during power shortcuts
    private var navModeWasActive: Boolean = false
    private var exitNavModeCallback: (() -> Unit)? = null
    private var enterNavModeCallback: (() -> Unit)? = null

    /**
     * Checks whether the given package is a launcher.
     */
    fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache the launcher list to avoid repeated queries
        if (cachedLauncherPackages == null) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                cachedLauncherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()
                Log.d(TAG, "Launcher packages found: $cachedLauncherPackages")
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting launchers", e)
                cachedLauncherPackages = emptySet()
            }
        }
        
        val isLauncher = cachedLauncherPackages?.contains(packageName) ?: false
        Log.d(TAG, "isLauncher($packageName) = $isLauncher")
        return isLauncher
    }
    
    /**
     * Opens an app by package name.
     */
    private fun launchApp(packageName: String): Boolean {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "App opened: $packageName")
                return true
            } else {
                Log.w(TAG, "No launch intent found for: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app $packageName", e)
            return false
        }
    }

    private fun executeShortcutCommand(shortcut: SettingsManager.LauncherShortcut): Boolean {
        if (shortcut.commandId == PastieraCommandSource.COMMAND_QUICK_LAUNCHER) {
            return QuickLauncherOpener.open(context)
        }
        val command = shortcut.commandId?.let { CommandRegistry(context).resolve(it) }
            ?: shortcutToCommand(shortcut)
            ?: return false
        return CommandExecutor(context).execute(command).isSuccess
    }

    private fun shortcutToCommand(shortcut: SettingsManager.LauncherShortcut): CommandTarget? {
        val launch = shortcut.commandLaunch ?: return null
        return CommandTarget(
            id = shortcut.commandId ?: "legacy:${shortcut.type}:${shortcut.packageName ?: shortcut.action.orEmpty()}",
            source = shortcut.commandSource
                ?.let { CommandSourceId.fromStorageValue(it) }
                ?: CommandSourceId.Pastiera,
            kind = runCatching {
                CommandKind.valueOf(shortcut.commandKind ?: "")
            }.getOrDefault(CommandKind.PastieraAction),
            label = shortcut.commandTitle ?: shortcut.appName ?: shortcut.packageName ?: "Shortcut",
            subtitle = shortcut.commandSubtitle,
            launch = launch
        )
    }
    
    /**
     * Handles launcher shortcuts when not in a text field.
     */
    fun handleLauncherShortcut(keyCode: Int): Boolean {
        val shortcut = SettingsManager.getLauncherShortcut(context, keyCode)
        if (shortcut != null) {
            // Handle the different action types
            when (shortcut.type) {
                SettingsManager.LauncherShortcut.TYPE_APP -> {
                    if (shortcut.packageName != null) {
                        val success = executeShortcutCommand(shortcut) || launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Launcher shortcut executed: key $keyCode -> ${shortcut.packageName}")
                            return true // Consume the event
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Handle shortcuts in the future
                    Log.d(TAG, "Shortcut type not implemented yet: ${shortcut.type}")
                }
                SettingsManager.LauncherShortcut.TYPE_QUICK_LAUNCHER -> {
                    if (QuickLauncherOpener.open(context)) {
                        Log.d(TAG, "Quick launcher shortcut executed: key $keyCode")
                        return true
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_COMMAND -> {
                    if (executeShortcutCommand(shortcut)) {
                        Log.d(TAG, "Command shortcut executed: key $keyCode -> ${shortcut.commandId}")
                        return true
                    }
                }
                else -> {
                    Log.d(TAG, "Unknown action type: ${shortcut.type}")
                }
            }
        } else {
            // Unassigned key: show the dialog to assign an app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consume the event so nothing else handles it
        }
        return false // Don't consume the event
    }

    /**
     * Shows the dialog for assigning an app to a key.
     */
    private fun showLauncherShortcutAssignmentDialog(keyCode: Int) {
        try {
            val intent = Intent(context, LauncherShortcutAssignmentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            }
            context.startActivity(intent)
            Log.d(TAG, "Assignment dialog shown for key $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing the assignment dialog", e)
        }
    }
    
    /**
     * Sets the callbacks for handling nav mode during power shortcuts.
     */
    fun setNavModeCallbacks(
        exitNavMode: () -> Unit,
        enterNavMode: () -> Unit
    ) {
        exitNavModeCallback = exitNavMode
        enterNavModeCallback = enterNavMode
    }
    
    /**
     * Toggles Power Shortcut mode (SYM pressed).
     * If already active, deactivates it (edge case).
     * Returns true if the mode was activated, false if deactivated.
     * @param isNavModeActive whether nav mode is active when SYM is pressed
     */
    fun togglePowerShortcutMode(
        showToast: (String) -> Unit,
        isNavModeActive: Boolean = false
    ): Boolean {
        if (powerShortcutSymPressed) {
            // Edge case: already active, so deactivate it
            resetPowerShortcutMode()
            Log.d(TAG, "Power Shortcut mode deactivated by SYM")
            return false
        }
        
        // Remember whether nav mode was active and disable it if needed
        navModeWasActive = isNavModeActive
        if (isNavModeActive) {
            exitNavModeCallback?.invoke()
            Log.d(TAG, "Nav mode disabled to activate Power Shortcut")
        }
        
        // Activate the mode
        powerShortcutSymPressed = true
        Log.d(TAG, "Power Shortcut mode activated")

        // Cancel any previous timeout
        cancelPowerShortcutTimeout()

        // Set a timeout to auto-reset, and show the toast only if the chord doesn't continue right away.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val message = context.getString(R.string.power_shortcuts_press_key)
        powerShortcutToastRunnable = Runnable {
            if (powerShortcutSymPressed) {
                showToast(message)
            }
        }
        handler.postDelayed(powerShortcutToastRunnable!!, 500L)
        powerShortcutTimeoutRunnable = Runnable {
            resetPowerShortcutMode()
        }
        powerShortcutTimeoutHandler = handler
        handler.postDelayed(powerShortcutTimeoutRunnable!!, POWER_SHORTCUT_TIMEOUT_MS)
        
        return true
    }
    
    /**
     * Resets Power Shortcut mode.
     * Re-enables nav mode if it was active before.
     */
    fun resetPowerShortcutMode() {
        if (powerShortcutSymPressed) {
            powerShortcutSymPressed = false
            cancelPowerShortcutTimeout()
            Log.d(TAG, "Power Shortcut mode reset")

            // If nav mode was active before, re-enable it
            if (navModeWasActive) {
                enterNavModeCallback?.invoke()
                navModeWasActive = false
                Log.d(TAG, "Nav mode re-enabled after Power Shortcut")
            }
        }
    }
    
    /**
     * Returns whether Power Shortcut mode is active.
     */
    fun isPowerShortcutModeActive(): Boolean {
        return powerShortcutSymPressed
    }
    
    /**
     * Cancels the Power Shortcut mode timeout.
     */
    private fun cancelPowerShortcutTimeout() {
        powerShortcutToastRunnable?.let { runnable ->
            powerShortcutTimeoutHandler?.removeCallbacks(runnable)
        }
        powerShortcutTimeoutRunnable?.let { runnable ->
            powerShortcutTimeoutHandler?.removeCallbacks(runnable)
        }
        powerShortcutToastRunnable = null
        powerShortcutTimeoutRunnable = null
        powerShortcutTimeoutHandler = null
    }

    /**
     * Handles power shortcuts when SYM was pressed first.
     * Reuses the existing handleLauncherShortcut logic.
     * Returns true if the shortcut was handled, false otherwise.
     */
    fun handlePowerShortcut(keyCode: Int): Boolean {
        if (!isPowerShortcutModeActive()) {
            return false
        }
        
        // Reset the mode after use
        resetPowerShortcutMode()

        // Reuse the existing logic: same function, same assignments
        return handleLauncherShortcut(keyCode)
    }
}
