package it.palsoftware.pastiera

/** Entries for system controls whose screens also expose the same stable IDs. */
internal fun systemSettingEntries(): List<SettingEntry> = listOf(
    SettingEntry(
        id = "nav_mode.enabled",
        titleRes = R.string.nav_mode_enable_title,
        summaryRes = R.string.nav_mode_enable_description,
        route = SettingRoute(SettingsDestination.NavMode)
    ),
    SettingEntry(
        id = "nav_mode.ctrl_hold",
        titleRes = R.string.nav_mode_ctrl_hold_title,
        summaryRes = R.string.nav_mode_ctrl_hold_description,
        route = SettingRoute(SettingsDestination.NavMode),
        availabilityCheck = { SettingsManager.getNavModeEnabled(it) },
        unavailableFallbackId = "nav_mode.enabled"
    ),
    SettingEntry(
        id = "accessibility.bounce_keys_delay",
        titleRes = R.string.settings_accessibility_bounce_keys_delay_link_title,
        summaryRes = R.string.settings_accessibility_bounce_keys_delay_description,
        route = SettingRoute(SettingsDestination.Accessibility)
    )
)
