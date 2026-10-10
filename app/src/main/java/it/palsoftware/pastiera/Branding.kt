package it.palsoftware.pastiera

/**
 * Product names that differ between Pastiera and Numen ([false]).
 * Resource strings are rebranded by tools/numen_branding.py; these are hard-coded in Kotlin.
 */
object Branding {
    val appName: String = "Numen"
    val launcherName: String = "App search"
    val launcherSubtitle: String = "Search your apps"
    val compactBarName: String = "Compact bar"

    /** Built-in theme names stay stable identifiers ("Pastiera Dark"); only their label changes. */
    fun themeLabel(name: String): String =
        if (!name.startsWith("Pastiera ")) name
        else "Numen " + name.removePrefix("Pastiera ")
}
