package it.palsoftware.pastiera.inputmethod

import android.view.InputDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceSpecificTest {

    @After
    fun tearDown() {
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun titanPocketProfile_mapsToTitan2Layout() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan Pocket",
            device = "titan_pocket",
            product = "titan_pocket"
        )

        assertEquals("titan2", DeviceSpecific.physicalKeyboardName())
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.isTitan2EliteDevice())
    }

    @Test
    fun originalTitanProfile_detectsOriginalTitanLayout() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "Unihertz",
            manufacturer = "A-gold",
            model = "Titan",
            device = "Titan",
            product = "Titan",
            board = "g61v71c2k_dfl_tee",
            display = "Titan_20221121"
        )

        assertEquals("titan", DeviceSpecific.physicalKeyboardName())
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.isTitan2Device())
    }

    @Test
    fun otherPhones_haveNoBuiltInKeyboard() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "google",
            manufacturer = "google",
            model = "Pixel 9",
            device = "tokay",
            product = "tokay"
        )

        assertEquals("unknown", DeviceSpecific.physicalKeyboardName())
        assertEquals("unknown", DeviceSpecific.keyboardName())
        assertFalse(DeviceSpecific.hasBuiltInHardwareKeyboard())
        assertTrue(DeviceSpecific.detectedInputProfiles().isEmpty())
    }

    @Test
    fun manualOverride_choosesATitanProfile() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "google",
            manufacturer = "google",
            model = "Pixel 9",
            device = "tokay",
            product = "tokay"
        )

        val profile = DeviceSpecific.resolveInputProfile(identity = null, physicalProfileOverride = "titan2")
        assertEquals("titan2", profile.profileId)
        assertFalse(profile.autoDetected)
        // Profiles of other devices are no longer accepted.
        assertEquals("unknown", DeviceSpecific.resolveInputProfile(identity = null, physicalProfileOverride = "key2").profileId)
    }

    @Test
    fun titan2EliteQwertyProfile_detectsOnlyWithStrictToken() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan2Elite_QWERTY",
            device = "titan2elite_qwerty",
            product = "titan2elite_qwerty"
        )

        assertEquals("titan2elite_qwerty", DeviceSpecific.physicalKeyboardName())
        assertEquals("Unihertz", DeviceSpecific.keyboardName())
        assertTrue(DeviceSpecific.isTitan2Device())
        assertTrue(DeviceSpecific.isTitan2EliteDevice())
    }

    @Test
    fun titan2EliteDetectedFromDisplayEvenWithoutStrictToken() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2 Elite",
            device = "titan2",
            product = "titan2",
            display = "Titan 2 Elite_V02.00.00"
        )

        assertEquals("titan2elite_qwerty", DeviceSpecific.physicalKeyboardName())
        assertTrue(DeviceSpecific.isTitan2EliteDevice())
    }

    @Test
    fun titan2EliteDetectedFromBoardToken() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan_2",
            product = "titan_2",
            board = "G72BoardV1"
        )

        assertEquals("titan2elite_qwerty", DeviceSpecific.physicalKeyboardName())
        assertTrue(DeviceSpecific.isTitan2EliteDevice())
    }

    @Test
    fun shippedTitan2EliteFingerprint_exposesStableBackupIdentity() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "Unihertz",
            manufacturer = "Unihertz",
            model = "Titan 2",
            device = "Titan_2",
            product = "Titan_2_EEA",
            board = "G72BoardV1",
            display = "Titan 2 Elite_EEA_V02.00.02",
            fingerprint = "Unihertz/Titan_2_EEA/Titan_2:16/build/V02.00.02:user/release-keys"
        )

        val identity = DeviceSpecific.detectedDeviceIdentity()

        assertEquals("titan2-elite", identity.stableId)
        assertEquals("Titan 2 Elite", identity.displayName)
        assertEquals("g72boardv1", identity.board)
        assertTrue(identity.buildDisplay.contains("elite"))
        assertTrue(identity.buildFingerprint.contains("V02.00.02"))
    }

    @Test
    fun externalKeyboard_isAnAccessoryUsingTheBuiltInProfile() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        val profile = DeviceSpecific.resolveInputProfile(
            identity = DeviceSpecific.KeyboardInputIdentity(
                name = "Bluetooth Keyboard",
                descriptor = "test-descriptor",
                vendorId = 1,
                productId = 0,
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isExternal = true,
                isVirtual = false
            )
        )

        assertEquals("titan2", profile.profileId)
        assertEquals(DeviceSpecific.InputDeviceKind.ACCESSORY, profile.kind)
    }
}
