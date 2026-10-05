package it.palsoftware.pastiera

import android.content.Context

internal val KEYBOARD_THEME_DRAFT_REQUIRED_FIELDS = setOf(
    DRAFT_BACKGROUND,
    DRAFT_DIVIDER,
    DRAFT_NORMAL_KEY,
    DRAFT_SPECIAL_KEY,
    DRAFT_TEXT_ICONS,
    DRAFT_ACCENT,
    DRAFT_SUGGESTION,
    DRAFT_STATUS_BUTTON,
    DRAFT_CURSOR_SWIPE,
    DRAFT_KEY_POPUP,
    DRAFT_KEY_POPUP_SELECTED,
    DRAFT_LED_INACTIVE,
    DRAFT_LED_ACTIVE,
    DRAFT_LED_LOCKED,
    DRAFT_KEY_ROUNDING,
    DRAFT_CHROME_ROUNDING,
    DRAFT_KEY_HEIGHT,
    DRAFT_NUMBER_ROW_HEIGHT,
    DRAFT_KEY_WIDTH,
    DRAFT_ROW_SPACING,
    DRAFT_SUGGESTIONS_HEIGHT,
    DRAFT_VARIATIONS_HEIGHT,
    DRAFT_SHOW_LEDS,
    DRAFT_DISTRIBUTE_SPACING,
    DRAFT_ORTHOLINEAR,
    DRAFT_ATTACH_POPUP,
    DRAFT_POPUP_TAIL,
    DRAFT_PREVIEW_ON_HOLD,
    DRAFT_CHARACTER_PICKER
)

internal fun keyboardThemeNameExists(context: Context, name: String): Boolean {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return false
    return SettingsManager.getSavedKeyboardThemes(context)
        .any { it.name.equals(normalizedName, ignoreCase = true) } ||
        SettingsManager.getKeyboardThemeDrafts(context)
            .any { it.name.equals(normalizedName, ignoreCase = true) }
}

internal fun importedKeyboardThemeDraft(
    draft: SettingsManager.KeyboardThemeDraft,
    importedTheme: SettingsManager.KeyboardThemeSettings
): SettingsManager.KeyboardThemeDraft =
    draft.copy(
        theme = importedTheme,
        populatedFields = KEYBOARD_THEME_DRAFT_REQUIRED_FIELDS
    )

internal fun saveImportedKeyboardTheme(
    context: Context,
    target: SettingsManager.KeyboardThemeTarget,
    name: String,
    importedTheme: SettingsManager.KeyboardThemeSettings
): Boolean {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty() || keyboardThemeNameExists(context, normalizedName)) return false

    SettingsManager.saveKeyboardTheme(context, normalizedName, importedTheme)
    SettingsManager.setKeyboardTheme(context, target, importedTheme)
    return true
}
