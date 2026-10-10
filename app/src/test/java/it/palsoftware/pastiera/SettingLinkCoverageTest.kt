package it.palsoftware.pastiera

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingLinkCoverageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetSettings() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun auditedSystemControlsAreDiscoverableUsingTheirDisplayedTitle() {
        SettingsManager.setNavModeEnabled(context, true)
        val controls = mapOf(
            "nav_mode.enabled" to R.string.nav_mode_enable_title,
            "nav_mode.ctrl_hold" to R.string.nav_mode_ctrl_hold_title,
            "accessibility.bounce_keys_delay" to R.string.settings_accessibility_bounce_keys_delay_link_title
        )
        controls.forEach { (id, titleRes) ->
            val results = SettingLinkRegistry.search(context, context.getString(titleRes))
            assertTrue("Search does not expose $id", results.any { it.id == id })
        }
    }

    @Test
    fun hiddenNavControlsResolveToVisibleEnableSwitchAndReturnWhenEnabled() {
        val ids = listOf("nav_mode.ctrl_hold")
        SettingsManager.setNavModeEnabled(context, false)
        ids.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertFalse(entry.isAvailable(context))
            assertEquals("nav_mode.enabled", SettingLinkRegistry.visibleTarget(context, entry).id)
            assertFalse(SettingLinkRegistry.search(context, context.getString(entry.titleRes)).any { it.id == id })
        }
        SettingsManager.setNavModeEnabled(context, true)
        ids.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertTrue(entry.isAvailable(context))
            assertEquals(id, SettingLinkRegistry.visibleTarget(context, entry).id)
        }
    }

    @Test
    fun registeredDeviceControlsRetainTheirActualSubscreenInNavigationStack() {
        val onScreen = requireNotNull(SettingLinkRegistry.byId("on_screen.number_row")).route.toSettingsPage()
        assertEquals(SettingsDestination.KeyboardsDevices, onScreen.destination)
        assertEquals(KeyboardsDevicesDestination.OnScreen, onScreen.keyboardsDevicesDestination)
        // The currency symbol moved to Keys & modifiers.
        val currency = requireNotNull(SettingLinkRegistry.byId("hardware.currency")).route.toSettingsPage()
        assertEquals(SettingsDestination.Modifiers, currency.destination)
        assertEquals(KeyboardsDevicesDestination.Main, currency.keyboardsDevicesDestination)
        assertEquals(
            KeyboardsDevicesDestination.Main,
            requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MAIN_KEYBOARDS_DEVICES))
                .route.toSettingsPage().keyboardsDevicesDestination
        )
    }

    @Test
    fun formerKeyboardsDevicesRowsAreHiddenUntilChangedAndLinksLandOnKeysAndModifiers() {
        val hiddenIds = listOf(
            SettingLinkIds.MAIN_KEYBOARDS_DEVICES,
            SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_AUTO,
            SettingLinkIds.KEYBOARDS_DEVICES_TOGGLE_TOASTS,
            SettingLinkIds.KEYBOARDS_DEVICES_ON_SCREEN_KEYBOARD,
            SettingLinkIds.KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS,
            "on_screen.number_row",
            "on_screen.layer_popup_below",
            "hardware.profile",
            "hardware.titan2_layout"
        )
        hiddenIds.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertFalse("$id should be hidden with default settings", entry.isAvailable(context))
            assertEquals(SettingLinkIds.MAIN_MODIFIERS, SettingLinkRegistry.visibleTarget(context, entry).id)
            assertFalse(SettingLinkRegistry.search(context, context.getString(entry.titleRes)).any { it.id == id })
        }
        val shortcut = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_SHORTCUT))
        assertEquals(SettingLinkIds.MAIN_NAV_MODE, SettingLinkRegistry.visibleTarget(context, shortcut).id)

        // A changed value brings back only the matching row, so it can be set back.
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "titan2")
        assertTrue(requireNotNull(SettingLinkRegistry.byId("hardware.profile")).isAvailable(context))
        assertTrue(requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS)).isAvailable(context))
        assertFalse(requireNotNull(SettingLinkRegistry.byId("on_screen.number_row")).isAvailable(context))
        assertEquals(
            SettingLinkIds.KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS,
            SettingLinkRegistry.visibleTarget(context, requireNotNull(SettingLinkRegistry.byId("hardware.titan2_layout"))).id
        )

        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)
        assertTrue(requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_AUTO)).isAvailable(context))
    }

    @Test
    fun hiddenKeyboardSettingsNeverWritePreferences() {
        val prefs = SettingsManager.getPreferences(context)
        HiddenKeyboardSettings.anyChanged(context)
        SettingLinkRegistry.entries.forEach { SettingLinkRegistry.visibleTarget(context, it) }
        SettingLinkRegistry.search(context, "keyboard")
        assertTrue("Reading visibility must not save anything", prefs.all.isEmpty())
    }

    @Test
    fun movedAndConditionalModifierRowsResolveToVisibleRows() {
        listOf(
            SettingLinkIds.TEXT_INPUT_CLEAR_ALT_ON_SPACE,
            SettingLinkIds.TEXT_INPUT_ALT_CTRL_SPEECH_SHORTCUT,
            "hardware.currency"
        ).forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertEquals(SettingsDestination.Modifiers, entry.route.destination)
            assertTrue(entry.isAvailable(context))
        }
        val autoShow = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.TEXT_INPUT_AUTO_SHOW_KEYBOARD)).route
        assertEquals(SettingsDestination.Customization, autoShow.destination)
        assertEquals(SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS, autoShow.customizationDestination)

        // Keep locked Alt only matters while Space and Enter release Alt.
        val keepLocked = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MODIFIERS_ALT_LATCH_STAYS_ON_SPACE))
        assertTrue(keepLocked.isAvailable(context))
        SettingsManager.setClearAltOnSpace(context, false)
        assertEquals(SettingLinkIds.TEXT_INPUT_CLEAR_ALT_ON_SPACE, SettingLinkRegistry.visibleTarget(context, keepLocked).id)

        // What Alt types: hidden at the default, shown once changed.
        val altBinding = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MODIFIERS_ALT_BINDING))
        assertEquals(SettingLinkIds.MAIN_MODIFIERS, SettingLinkRegistry.visibleTarget(context, altBinding).id)
        SettingsManager.setAltModifierBinding(context, AltModifierBinding.Emoji)
        assertEquals(altBinding.id, SettingLinkRegistry.visibleTarget(context, altBinding).id)

        // The duplicate Alt shortcuts link lands on the switch in App shortcuts.
        val altShortcuts = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MODIFIERS_ALT_KEY_SHORTCUTS))
        assertEquals("quick_launcher.alt_shortcuts", SettingLinkRegistry.visibleTarget(context, altShortcuts).id)

        // Sound & vibration has one page; the main-list link opens it.
        val sounds = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MAIN_CUSTOMIZATION))
        assertEquals("customization.sounds", SettingLinkRegistry.visibleTarget(context, sounds).id)
    }

    @Test
    fun activeKeyboardLayoutSwitchIsHiddenUnlessTurnedOff() {
        val entry = requireNotNull(SettingLinkRegistry.byId("quick_launcher.respect_keyboard_layout"))
        assertEquals("quick_launcher.behavior", SettingLinkRegistry.visibleTarget(context, entry).id)
        SettingsManager.setQuickLauncherRespectKeyboardLayout(context, false)
        assertEquals(entry.id, SettingLinkRegistry.visibleTarget(context, entry).id)
    }

    @Test
    fun supplementalLiteralEntriesHaveUiReferences() {
        val sources = mainSources()
        val registrySources = sources.filter { it.name.endsWith("SettingEntries.kt") }
        assertTrue("Supplemental registries were not found", registrySources.isNotEmpty())
        val entryPattern = Regex("""SettingEntry\(\s*(?:id\s*=\s*)?"([a-z0-9_]+(?:\.[a-z0-9_]+)+)"""")
        val ids = registrySources.flatMap { source ->
            entryPattern.findAll(source.readText()).map { it.groupValues[1] }.toList()
        }
        assertTrue("No literal supplemental entries were found", ids.isNotEmpty())
        val uiText = sources.filterNot { it in registrySources || it.name == "SettingLinks.kt" }
            .joinToString("\n") { it.readText() }
        ids.forEach { id ->
            assertTrue("Supplemental entry $id has no literal UI reference", uiText.contains("\"$id\""))
        }
    }

    @Test
    fun literalUiHooksResolveInRegistry() {
        // Only complete static literals match; interpolated per-item IDs are deliberately excluded.
        val hookPattern = Regex("""(?:\.settingRow\(\s*|\blinkId\s*=\s*)"([a-z0-9_]+(?:\.[a-z0-9_]+)+)"""")
        val hooks = mainSources().flatMap { source ->
            hookPattern.findAll(source.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("No literal UI hooks were found", hooks.isNotEmpty())
        hooks.forEach { id ->
            assertTrue("UI hook $id has no registered entry", SettingLinkRegistry.byId(id) != null)
        }
    }

    private fun mainSources(): List<File> {
        val workingDirectory = File(System.getProperty("user.dir"))
        val sourceDirectory = listOf(
            File(workingDirectory, "src/main/java/it/palsoftware/pastiera"),
            File(workingDirectory, "app/src/main/java/it/palsoftware/pastiera")
        ).firstOrNull { it.isDirectory }
        requireNotNull(sourceDirectory) { "Main sources not found relative to $workingDirectory" }
        return sourceDirectory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

}
