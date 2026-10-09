package it.palsoftware.pastiera.inputmethod

import android.os.Build
import android.view.InputDevice
import it.palsoftware.pastiera.DeviceIdentitySnapshot

/** Which Unihertz Titan this is, read from the build fingerprint. Numen is made for the Titan 2 Elite. */
object DeviceSpecific {
    enum class InputDeviceKind {
        BUILT_IN,
        ACCESSORY,
        UNKNOWN
    }

    data class KeyboardInputIdentity(
        val name: String,
        val descriptor: String,
        val vendorId: Int,
        val productId: Int,
        val sources: Int,
        val keyboardType: Int,
        val isExternal: Boolean,
        val isVirtual: Boolean
    )

    data class ResolvedInputProfile(
        val profileId: String,
        val kind: InputDeviceKind,
        val autoDetected: Boolean
    )

    private enum class KeyboardModel(val profileId: String, val stableId: String?, val displayName: String?) {
        TITAN_2_ELITE_QWERTY("titan2elite_qwerty", "titan2-elite", "Titan 2 Elite"),
        TITAN_2("titan2", "titan2", "Titan 2"),
        TITAN_POCKET("titan2", "titan-pocket", "Titan Pocket"),
        TITAN_SLIM("titan2", "titan-slim", "Titan Slim"),
        TITAN_ORIGINAL("titan", "titan", "Titan"),
        UNKNOWN("unknown", null, null)
    }

    /** Profiles a person can pick by hand when detection gets the device wrong. */
    private val MANUAL_PROFILES = setOf("titan", "titan2", "titan2elite_qwerty")

    private var testBuildFingerprintOverride: BuildFingerprint? = null

    private data class BuildFingerprint(
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val board: String,
        val display: String,
        val fingerprint: String
    ) {
        fun containsAny(vararg tokens: String): Boolean {
            return tokens.any { token ->
                brand.contains(token) ||
                    manufacturer.contains(token) ||
                    model.contains(token) ||
                    device.contains(token) ||
                    product.contains(token) ||
                    board.contains(token) ||
                    display.contains(token)
            }
        }
    }

    private fun currentModel(): KeyboardModel {
        val fp = buildFingerprint()
        return when {
            isTitan2EliteQwerty(fp) -> KeyboardModel.TITAN_2_ELITE_QWERTY
            !fp.containsAny("unihertz", "titan") -> KeyboardModel.UNKNOWN
            fp.containsAny("titan pocket", "titan_pocket") -> KeyboardModel.TITAN_POCKET
            fp.containsAny("titan slim", "titan_slim") -> KeyboardModel.TITAN_SLIM
            fp.containsAny("titan 2", "titan2") -> KeyboardModel.TITAN_2
            fp.containsAny("titan") -> KeyboardModel.TITAN_ORIGINAL
            else -> KeyboardModel.UNKNOWN
        }
    }

    private fun isTitan2EliteQwerty(fp: BuildFingerprint): Boolean {
        if (fp.containsAny("titan2elite_qwerty", "titan2elite-qwerty", "titan2eliteqwerty")) {
            return true
        }
        // Reviewer devices may expose a Titan 2-like model/product but still leak Elite traits
        // via BOARD or DISPLAY. Use these only inside the Unihertz Titan family.
        val looksLikeTitanFamily = fp.containsAny("unihertz", "titan")
        return looksLikeTitanFamily && (fp.display.contains("elite") || fp.board.contains("g72"))
    }

    private fun buildFingerprint(): BuildFingerprint {
        testBuildFingerprintOverride?.let { return it }
        return BuildFingerprint(
            brand = Build.BRAND.orEmpty().lowercase(),
            manufacturer = Build.MANUFACTURER.orEmpty().lowercase(),
            model = Build.MODEL.orEmpty().lowercase(),
            device = Build.DEVICE.orEmpty().lowercase(),
            product = Build.PRODUCT.orEmpty().lowercase(),
            board = Build.BOARD.orEmpty().lowercase(),
            display = Build.DISPLAY.orEmpty().lowercase(),
            fingerprint = Build.FINGERPRINT.orEmpty()
        )
    }

    fun resolveInputProfile(
        device: InputDevice,
        physicalProfileOverride: String? = null
    ): ResolvedInputProfile {
        return resolveInputProfile(keyboardInputIdentity(device), physicalProfileOverride)
    }

    internal fun resolveInputProfile(
        identity: KeyboardInputIdentity?,
        physicalProfileOverride: String? = null
    ): ResolvedInputProfile {
        val kind = when {
            identity == null -> InputDeviceKind.UNKNOWN
            identity.isExternal -> InputDeviceKind.ACCESSORY
            else -> InputDeviceKind.BUILT_IN
        }
        val manualProfile = normalizePhysicalProfileOverride(physicalProfileOverride)
        if (manualProfile != null) {
            return ResolvedInputProfile(profileId = manualProfile, kind = kind, autoDetected = false)
        }
        val model = currentModel()
        return ResolvedInputProfile(
            profileId = model.profileId,
            kind = kind,
            autoDetected = model != KeyboardModel.UNKNOWN
        )
    }

    fun detectedInputProfiles(): List<ResolvedInputProfile> {
        val model = currentModel()
        if (model == KeyboardModel.UNKNOWN) return emptyList()
        return listOf(ResolvedInputProfile(model.profileId, InputDeviceKind.BUILT_IN, autoDetected = true))
    }

    fun hasConnectedHardwareKeyboard(): Boolean {
        if (hasBuiltInHardwareKeyboard()) {
            return true
        }
        return InputDevice.getDeviceIds().any { deviceId ->
            val device = InputDevice.getDevice(deviceId) ?: return@any false
            val identity = keyboardInputIdentity(device)
            !identity.isVirtual &&
                isKeyboardLike(identity) &&
                identity.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }
    }

    fun hasBuiltInHardwareKeyboard(): Boolean = currentModel() != KeyboardModel.UNKNOWN

    private fun keyboardInputIdentity(device: InputDevice): KeyboardInputIdentity {
        return KeyboardInputIdentity(
            name = device.name.orEmpty(),
            descriptor = device.descriptor.orEmpty(),
            vendorId = device.vendorId,
            productId = device.productId,
            sources = device.sources,
            keyboardType = device.keyboardType,
            isExternal = device.isExternal,
            isVirtual = device.isVirtual
        )
    }

    private fun isKeyboardLike(identity: KeyboardInputIdentity): Boolean {
        return (identity.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
            identity.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
    }

    private fun normalizePhysicalProfileOverride(physicalProfileOverride: String?): String? {
        val normalized = physicalProfileOverride?.trim()?.lowercase().orEmpty()
        return normalized.takeIf { it in MANUAL_PROFILES }
    }

    internal fun setBuildFingerprintForTests(
        brand: String,
        manufacturer: String,
        model: String,
        device: String,
        product: String,
        board: String = "",
        display: String = "",
        fingerprint: String = ""
    ) {
        testBuildFingerprintOverride = BuildFingerprint(
            brand = brand.lowercase(),
            manufacturer = manufacturer.lowercase(),
            model = model.lowercase(),
            device = device.lowercase(),
            product = product.lowercase(),
            board = board.lowercase(),
            display = display.lowercase(),
            fingerprint = fingerprint
        )
    }

    internal fun clearTestOverrides() {
        testBuildFingerprintOverride = null
    }

    fun deviceName(): String {
        return Build.BRAND + " " + Build.MODEL
    }

    fun detectedDeviceIdentity(): DeviceIdentitySnapshot {
        val fingerprint = buildFingerprint()
        val model = currentModel()
        val fallbackName = listOf(fingerprint.brand, fingerprint.model)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { "Unknown device" }
        return DeviceIdentitySnapshot(
            stableId = model.stableId,
            displayName = model.displayName ?: fallbackName,
            brand = fingerprint.brand,
            manufacturer = fingerprint.manufacturer,
            model = fingerprint.model,
            device = fingerprint.device,
            product = fingerprint.product,
            board = fingerprint.board,
            buildDisplay = fingerprint.display,
            buildFingerprint = fingerprint.fingerprint
        )
    }

    fun keyboardName(): String = if (currentModel() == KeyboardModel.UNKNOWN) "unknown" else "Unihertz"

    fun physicalKeyboardName(): String = currentModel().profileId

    fun isTitan2Device(): Boolean = when (currentModel()) {
        KeyboardModel.TITAN_2,
        KeyboardModel.TITAN_2_ELITE_QWERTY -> true
        else -> false
    }

    fun isTitan2EliteDevice(): Boolean = currentModel() == KeyboardModel.TITAN_2_ELITE_QWERTY

    fun isPhysicalKeyboardDevice(physicalProfileOverride: String? = null): Boolean =
        normalizePhysicalProfileOverride(physicalProfileOverride) != null || hasBuiltInHardwareKeyboard()
}
