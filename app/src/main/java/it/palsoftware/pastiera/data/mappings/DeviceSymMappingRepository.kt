package it.palsoftware.pastiera.data.mappings

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

object DeviceSymProfileResolver {
    fun resolve(context: Context): String {
        val manualOverride = SettingsManager.getPhysicalKeyboardProfileOverride(context)
        if (manualOverride != "auto") {
            return manualOverride
        }
        DeviceSpecific.detectedInputProfiles()
            .firstOrNull { it.kind == DeviceSpecific.InputDeviceKind.ACCESSORY }
            ?.let { return it.profileId }
        return DeviceSpecific.physicalKeyboardName().takeUnless { it == "unknown" } ?: VIRTUAL_PROFILE_ID
    }

    const val VIRTUAL_PROFILE_ID = "virtual"
}

object DeviceSymMappingRepository {
    private const val TAG = "DeviceSymMappings"

    fun load(
        assets: AssetManager,
        context: Context,
        profileOverride: String? = null
    ): Map<Int, String> {
        val requestedProfile = profileOverride ?: DeviceSymProfileResolver.resolve(context)
        return try {
            loadProfile(assets, requestedProfile, context)
        } catch (e: Exception) {
            if (requestedProfile != DeviceSymProfileResolver.VIRTUAL_PROFILE_ID) {
                Log.w(TAG, "Device SYM profile '$requestedProfile' unavailable; using virtual profile", e)
                runCatching {
                    loadProfile(assets, DeviceSymProfileResolver.VIRTUAL_PROFILE_ID, context)
                }.getOrElse { fallbackError ->
                    Log.e(TAG, "Virtual Device SYM profile unavailable", fallbackError)
                    emptyMap()
                }
            } else {
                Log.e(TAG, "Virtual Device SYM profile unavailable", e)
                emptyMap()
            }
        }
    }

    private fun loadProfile(
        assets: AssetManager,
        profileId: String,
        context: Context
    ): Map<Int, String> {
        val mappings = KeyMappingLoader.loadStringMappings(
            assets,
            "devices/$profileId/device_sym_key_mappings.json"
        ).toMutableMap()
        if (mappings.containsKey(KeyEvent.KEYCODE_GRAVE)) {
            mappings[KeyEvent.KEYCODE_GRAVE] = SettingsManager.getPhysicalKeyboardCurrencySymbol(context)
        }
        Log.d(TAG, "Loaded Device SYM mappings for profile: $profileId")
        return mappings
    }
}
