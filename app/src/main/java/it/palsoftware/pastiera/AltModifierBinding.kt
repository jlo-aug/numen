package it.palsoftware.pastiera

/**
 * Selects the character mapping triggered by the Alt modifier.
 *
 * Alt is only the trigger. Device SYM, Emoji, and Symbols remain independent SYM layers.
 */
sealed interface AltModifierBinding {
    val persistedValue: String

    data object DeviceSym : AltModifierBinding {
        override val persistedValue: String = "device_sym:auto"
    }

    data object FirstEnabledSymKeyLayer : AltModifierBinding {
        override val persistedValue: String = "sym:first_enabled"
    }

    data object Emoji : AltModifierBinding {
        override val persistedValue: String = "sym:emoji"
    }

    data object Symbols : AltModifierBinding {
        override val persistedValue: String = "sym:symbols"
    }

    data class DeviceSymProfile(val profileId: String) : AltModifierBinding {
        override val persistedValue: String = "device_sym:$profileId"
    }

    companion object {
        fun fromPersistedValue(value: String?): AltModifierBinding = when {
            value == null -> DeviceSym
            value == DeviceSym.persistedValue || value == "device:auto" || value == SymPagesConfig.PAGE_DEVICE -> DeviceSym
            value == FirstEnabledSymKeyLayer.persistedValue || value == "first" -> FirstEnabledSymKeyLayer
            value == Emoji.persistedValue || value == "emoji" -> Emoji
            value == Symbols.persistedValue || value == "symbols" -> Symbols
            value.startsWith("device_sym:") -> value.removePrefix("device_sym:")
                .takeIf { it.isNotBlank() && it != "auto" }
                ?.let(::DeviceSymProfile)
                ?: DeviceSym
            value.startsWith("device:") -> value.removePrefix("device:")
                .takeIf { it.isNotBlank() && it != "auto" }
                ?.let(::DeviceSymProfile)
                ?: DeviceSym
            else -> DeviceSym
        }
    }
}
