package it.palsoftware.pastiera

/**
 * Product names that differ between Pastiera and Numen ([BuildConfig.UPSTREAM_BRANDING]).
 * Resource strings are rebranded by tools/numen_branding.py; these are hard-coded in Kotlin.
 */
object Branding {
    val appName: String = if (BuildConfig.UPSTREAM_BRANDING) "Pastiera" else "Numen"
    val launcherName: String = if (BuildConfig.UPSTREAM_BRANDING) "Pastiera QuickLauncher" else "KeyLaunches"
    val launcherSubtitle: String = if (BuildConfig.UPSTREAM_BRANDING) "Open Pastiera search" else "Open KeyLaunches search"
    val compactBarName: String = if (BuildConfig.UPSTREAM_BRANDING) "Pastierina" else "Compact bar"

    /** Built-in theme names stay stable identifiers ("Pastiera Dark"); only their label changes. */
    fun themeLabel(name: String): String =
        if (BuildConfig.UPSTREAM_BRANDING || !name.startsWith("Pastiera ")) name
        else "Numen " + name.removePrefix("Pastiera ")
}
