package it.palsoftware.pastiera.backup

internal object BackupPreferencePolicy {
    private const val PASTIERA_PREFS = "pastiera_prefs"

    val runtimeDerivedKeys = setOf(
        "$PASTIERA_PREFS:pastierina_mode_active",
        "$PASTIERA_PREFS:software_keyboard_mode_runtime_override"
    )

    val targetDeviceDerivedKeys = setOf(
        "$PASTIERA_PREFS:physical_keyboard_profile_override",
        "$PASTIERA_PREFS:titan2_layout_enabled",
        "$PASTIERA_PREFS:titan2_elite_rounded_corner_insets"
    )
}
