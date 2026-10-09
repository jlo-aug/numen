package it.palsoftware.pastiera.inputmethod

/**
 * Central registry for narrow app- and device-specific compatibility workarounds.
 *
 * Keep targeted exceptions here so their scope and removal conditions remain discoverable.
 */
internal object CompatibilityWorkarounds {
    const val TELEGRAM_PACKAGE_NAME = "org.telegram.messenger"
}
