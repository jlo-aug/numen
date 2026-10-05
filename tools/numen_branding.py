#!/usr/bin/env python3
"""Generate Numen's string overrides from Pastiera's resources.

Pastiera's forking policy asks forks not to present themselves as Pastiera. Instead of editing
upstream's strings (which would conflict on every merge), this copies each string that names
"Pastiera" or its successor "Plektra" into the nightly (Numen) flavor's resources, with the
name replaced. Android's flavor overlay then uses these in the Numen build only.

Run from the repository root after merging upstream changes:
  python3 tools/numen_branding.py
Credits and attributions live in assets (about_credits.md), not here, so they keep the names.
"""
import glob
import os
import re

MAIN = "app/src/main/res"
OVERLAY = "app/src/nightly/res"
NAME = "Numen"
LAUNCHER = "KeyLaunches"
# Files whose strings are user-visible text (themes.xml only has style names).
FILES = ("strings.xml", "customization_setting_links.xml", "nightly_update.xml",
         "system_setting_links.xml", "corner_calibration.xml")
STRING = re.compile(r'(<string\b[^>]*>)(.*?)(</string>)', re.S)
# Numen's own English wording: the regrouped Settings and the "Compact bar" (Pastiera's
# "Pastierina mode"). Applied to values/ (English) by string name, on top of the renaming.
ENGLISH = {
    "settings_category_auto_correction": "Autocorrect &amp; Suggestions",
    "settings_category_text_input": "Text Input",
    "custom_input_styles_title": "Languages &amp; Layouts",
    "modifiers_title": "Keys &amp; Modifiers",
    "modifiers_description": "SYM layers, long press and modifier keys",
    "keyboards_devices_title": "Keyboard &amp; Devices",
    "status_bar_buttons_title": "Keyboard Bar",
    "status_bar_buttons_description": "Compact or full bar, and its buttons",
    "settings_category_customization": "Sound &amp; Variations",
    "settings_group_customization": "Keyboard bar",
    "settings_group_utility": "Keys",
    "settings_group_system": "System",
    "extended_status_bar_title": "Full bar",
    "nav_mode_action_toggle_pastierina": "Compact bar",
    "tutorial_nav_mode_bullet_media": "Mappings can also handle app shortcuts, media controls, Escape, Tab, Page Up/Down, and the compact bar.",
    "pastierina_mode_description": "Toggle the compact keyboard bar",
    "software_keyboard_mode_description": "Separate from the compact bar. Controls hardware vs virtual keyboard behaviour.",
    "pastierina_status_bar_buttons_title": "Compact bar",
    "pastierina_status_bar_buttons_description": "Choose which buttons appear on the left and right edges of the compact bar.",
    "pastierina_status_bar_slots_left": "Compact bar left buttons",
    "pastierina_status_bar_slots_right": "Compact bar right buttons",
    "status_bar_button_minimal_ui": "Compact bar",
    "status_bar_style_section": "Keyboard bar style",
    "status_bar_slot_add": "Add keyboard bar button",
    "status_bar_slot_remove": "Remove keyboard bar button",
    "status_bar_swipe_cursor_info": "Swipe across the keyboard bar to move the cursor.",
    "status_bar_variations_visible_description": "Show accents and other forms of the letter you just typed",
    "titan2_elite_rounded_corners_description": "Round the bottom corners of the keyboard bar to match the Elite’s rounded display corners.",
    "extended_status_bar_features_section": "Full bar features",
    "modifier_indicators_status_bar": "Keyboard bar",
    "modifier_indicators_status_bar_description": "Compact symbols at the left edge of the keyboard bar.",
    "keyboard_theme_status_bar_buttons": "Keyboard bar buttons",
    "settings_accessibility_live_read_title": "Live reading for keyboard bar changes",
    "settings_accessibility_live_read_description": "When enabled, TalkBack can announce language/layout and keyboard bar changes automatically.",
    "tutorial_status_bar_button": "Keyboard bar",
    "tutorial_page_led_description": "To get the most out of Numen, set the Fn key to act as Ctrl from Android system settings. Numen can show modifier state in the bottom strip, menu bar, or keyboard bar: active one-shot states use the active color, locked states use the locked color.",
    "tutorial_t2e_description": "Numen fits the keyboard bar to the Elite’s rounded display.",
    "setting_link_status_extended_left": "Full bar: left buttons",
    "setting_link_status_extended_right": "Full bar: right buttons",
    "setting_link_status_pastierina_left": "Compact bar: left buttons",
    "setting_link_status_pastierina_right": "Compact bar: right buttons",
    "clicks_sticky_alt_help": "Controls the Clicks firmware’s SYM Lock behaviour. Numen’s separate Sticky Alt behaviour remains configured under Keys &amp; Modifiers.",
    "clicks_sticky_shift_help": "Controls the Clicks firmware’s Caps Lock behaviour. Numen’s separate Sticky Shift behaviour remains configured under Keys &amp; Modifiers.",
    "quick_launcher_limit_results_description": "Do not render the full alphabetical app list. KeyLaunches stays empty until you type and then shows only the top three matches.",
    "app_enter_behaviour_sym_enter_conflict_description": "Move the KeyLaunches trigger to another key before using SYM + Enter as an additional send shortcut.",
    "quick_launcher_provider_description": "Choose whether the shortcut opens KeyLaunches or Niagara Launcher search. Niagara falls back to KeyLaunches if unavailable.",
    "auto_correct_ricette_pastiera_name": "All languages",
    "auto_correct_ricette_pastiera_description": "Replacements that apply in every language",
    "tutorial_t2e_trackpad_note": "Trackpad control still needs refinement.",
    "feature_status_construction_hint": "This feature is still being developed and will improve in a future update.",
    "tutorial_feature_status_construction_description": "This feature is still being developed and will improve in a future update.",
    "launcher_shortcuts_screen_title": "Key assignments",
    "pastierina_mode_title": "Compact bar",
    "experimental_suggestions_title": "Suggestion engine",
    "experimental_suggestions_subtitle": "Required for autocorrect and suggestions",
    "auto_correct_aggressive_description": "Fix missing and extra letters and missed spaces",
    "auto_correct_auto_replace_description": "Fix likely typos when you press Space or Enter",
    "auto_correct_title_description": "Apply your saved rules, like “omw” → “on my way”",
    "auto_correct_languages_description": "Add and edit your replacement rules",
    "auto_correct_manage_user_dict_description": "Words Numen should know and suggest",
    "trackpad_provider_native_ime_status": "Trackpad swipes are on",
    "settings_accessibility_second_row_title": "Read quick settings and variations",
    "auto_show_keyboard_description": "Show the bar whenever a text field opens",
    "settings_row_devices_description": "On-screen and built-in keyboards, and when the bar shows",
    "long_press_modifier_alt": "The key’s Alt character",
    "typing_sound_output_title": "Volume used for typing sounds",
    "settings_row_advanced_description": "Backup &amp; restore, clipboard and diagnostics",
    # Plain-English settings text (Keyboard & Devices).
    "keyboard_switching_auto_description": "Use the on-screen keyboard when no physical keyboard is found",
    "keyboard_switching_override_description": "Show or hide the on-screen keyboard until a keyboard connects or disconnects",
    "keyboard_switching_status_override_virtual": "On-screen · Switched for now",
    "keyboard_switching_status_override_hardware": "Physical · Switched for now",
    "software_keyboard_mode_toggle_toasts_title": "Keyboard switch message",
    "software_keyboard_mode_toggle_toasts_description": "Show a short message when the on-screen keyboard turns on or off",
    "keyboard_source_active": "In use",
    "keyboard_source_standby": "Not in use",
    "keyboard_source_device_bound_detected": "Found on this phone",
    "keyboard_source_device_bound": "No supported built-in keyboard found",
    # Keys & Modifiers.
    "long_press_modifier_title": "Long press types",
    "long_press_title": "Long press delay",
    "modifier_indicators_description": "Choose where to show when Shift, Ctrl, Alt or SYM is on",
    "sym_customization_description": "Choose, reorder and edit the pages SYM opens",
    "alt_binding_title": "What Alt types",
    "alt_binding_description": "Choose which layer Alt + a key types from, even if SYM hides it",
    "alt_key_shortcuts_modifier_link_description": "Set Alt + key shortcuts and whether they work while typing",
    "modifier_control_nav_mode_description": "Ctrl is used by Nav Mode; change its keys and behaviour there",
    "modifier_tap_behaviour_title": "Tap to lock",
    "shift_tap_latches_description": "One tap turns on Caps Lock instead of capitalizing one letter",
    "alt_tap_latches_description": "One tap keeps Alt on until you tap it again",
    "alt_latch_stays_on_space_description": "Pressing Space doesn’t turn a locked Alt off",
    "ctrl_tap_latches_description": "One tap keeps Ctrl on until you tap it again",
    "ctrl_latch_stays_on_space_description": "Ctrl stays locked after you switch layouts with Ctrl+Space",
    "sym_shortcuts_compatibility_description": "SYM key shortcuts need a keyboard that sends SYM to Numen as its own key. Some keyboards, like the Clicks Power Keyboard, turn SYM combinations into characters or arrows first, so these shortcuts can’t work with them.",
    # Keyboard Bar.
    "modifier_indicators_bottom_strip": "Bottom light strip",
    "modifier_indicators_bottom_strip_description": "Colored lights in a thin strip below the keyboard bar",
    "modifier_indicators_menu_bar": "Phone status bar",
    "modifier_indicators_menu_bar_description": "A small icon in the phone’s status bar at the top",
    "titan2_elite_fill_bottom_corners_description": "Extend the bar color into the corners; buttons stay rounded",
    "titan2_elite_top_corner_description": "How round the top outer corner of the edge buttons is",
    "titan2_elite_max_icon_shrink_title": "Shrink icons up to %1$d%%",
    "titan2_elite_max_icon_shrink_description": "Shrink edge button icons only as much as needed; 0% never shrinks",
    "dynamic_variation_slot_count_title": "Variation spaces",
    "dynamic_variation_slot_count_description": "Room for about %1$d variations between the bar buttons",
    "dynamic_variation_resize_to_content_title": "Stretch variations to fit",
    "dynamic_variation_resize_to_content_description": "Widen variations to fill the row; off keeps them left-aligned",
    "status_bar_button_minimal_ui_description": "Switch to or from the compact bar",
    # Text Input.
    "auto_space_punctuation_dialog_instruction": "For each mark, choose whether Numen removes the space it added before it, and whether it adds a space only once you type more.",
    "text_expansion_description": "Type a short code to insert text, emoji or symbols",
    "auto_capitalize_respect_manual_shift_off_title": "Remember Shift off",
    "auto_capitalize_respect_manual_shift_off_description": "If you turn auto Shift off, keep it off in this text field",
    "comma_space_description": "Add a space after commas and remove the space before them",
    "french_punctuation_spacing_description": "Add a thin, unbreakable space before ; : ! ? and « »",
    "french_punctuation_only_french_description": "Only while you’re typing in French",
    "clear_alt_on_space_description": "Pressing Space turns Alt off, including a locked Alt",
    "delete_alternatives_description": "Other ways to delete the character after the cursor",
    "delete_alternatives_hint": "Tip: you can also set a Ctrl + key for this in Nav Mode",
    "settings_nav_mode_configure": "Choose what each key does in Nav Mode",
    # Languages & Layouts.
    "keyboard_layout_mode_title": "Layout per language",
    "keyboard_layout_mode_auto_description": "Each language uses its own keyboard layout",
    "keyboard_layout_mode_manual_description": "One layout you choose is used for every language",
    "toast_on_layout_switch_title": "Language switch message",
    "toast_on_layout_switch_description": "Show a short message when you switch language or layout",
    "custom_input_styles_add": "Add language",
    "custom_input_styles_empty": "No languages added yet. Tap + to add one.",
    "custom_input_styles_cannot_hide_last": "Keep at least one language visible",
    "custom_input_styles_duplicate_error": "This language and layout are already added",
    "custom_input_styles_layout_mapping_updated": "Layout changed: %1$s - %2$s",
    "custom_input_styles_input_style_updated": "Saved: %1$s - %2$s",
    "custom_input_styles_input_style_added": "Added: %1$s - %2$s",
    "custom_input_styles_delete_confirm_title": "Remove language",
    "custom_input_styles_delete_confirm_message": "Remove this language and layout from the list?",
    "custom_input_styles_input_style_deleted": "Language removed",
    "custom_input_styles_edit_system_locale_title": "Layout for phone language",
    "custom_input_styles_edit_dialog_title": "Edit language",
    "custom_input_styles_add_dialog_title": "Add language",
    "custom_input_styles_add_custom_locale": "Other language code…",
    "custom_input_styles_system_locale_hint": "Phone language, can’t be changed",
    "custom_input_styles_no_dictionary_warning": "No dictionary for this language, so no suggestions or autocorrect",
    "custom_input_styles_custom_locale_title": "Enter language code",
    "custom_input_styles_custom_locale_label": "Language code",
    "custom_input_styles_custom_locale_hint": "Enter a language code (e.g., en_US, it_IT, fr_FR)",
    "custom_input_styles_custom_locale_empty_error": "Enter a language code",
    "custom_input_styles_custom_locale_invalid_error": "That code doesn’t look right. Use xx or xx_XX",
    "languages_description": "Add languages with a dictionary to switch between; your phone’s languages aren’t listed",
    "languages_no_available": "All available languages are already added or set on your phone",
    # Nav Mode.
    "nav_mode_guide_description": "How Nav Mode, Ctrl and app shortcuts work together",
    "nav_mode_ctrl_hold_description": "In text fields, holding Ctrl uses Nav Mode keys instead of app shortcuts",
    "layout_aware_ctrl_shortcuts_title": "Ctrl shortcuts follow layout",
    "layout_aware_ctrl_shortcuts_description": "App Ctrl+letter shortcuts use the letter your layout types",
    "layout_aware_ctrl_shortcuts_disabled_description": "Off while Ctrl-hold navigation is on, since Nav Mode uses held Ctrl",
    # Enter key behaviour.
    "app_enter_behaviour_enable_title": "Enter key per app",
    "app_enter_behaviour_enable_description": "Choose whether Enter sends or starts a new line in chat apps",
    "app_enter_behaviour_overrides_title": "Per-app settings",
    "app_enter_behaviour_preset_title": "Default for chat apps",
    "app_enter_behaviour_preset_description": "Set the same Enter behaviour for known chat apps on your phone",
    "app_enter_behaviour_additional_send_shortcut_description": "Add another key combo that sends, for every app listed below",
    "app_enter_behaviour_hide_manual_override": "Hide manual settings",
    "app_enter_behaviour_show_manual_override": "Change manually",
    "app_enter_behaviour_manual_override_note": "The built-in setting usually works best; change it only if an app update breaks it",
    "app_enter_behaviour_strategy_label": "How Numen sends",
    "app_enter_behaviour_status_native_result": "App default: Numen leaves Enter alone.",
    "app_enter_behaviour_strategy_not_tested_detail": "Not tested with this app yet.",
    # Advanced.
    "swipe_incremental_threshold_title": "Cursor swipe distance",
    "dip_unit": "dp per character",
    "clipboard_retention_time_title": "Keep clipboard items",
    "clipboard_retention_time_description": "Minutes; 0 keeps them forever",
    "experimental_candidates_view_title": "Experimental bar display",
    "experimental_candidates_view_description": "Shows the bar in a different Android mode. May cause display glitches",
    "corner_calibration_description": "Fine-tune how Numen fits the Elite’s rounded screen edges",
    "home_diagnostics_description": "Key presses and autocorrect log",
    # Accessibility.
    "settings_accessibility_overlapping_keys_description": "Ignore a second key until you let go of the first; Shift, Alt and Ctrl still work",
    # Sound & Variations, KeyLaunches.
    "tap_haptic_system_description": "Use the phone’s standard vibration when tapping suggestions and variations",
    "tap_haptic_duration_description": "Used instead of the phone’s standard vibration",
    "starter_launcher_shortcuts_intro": "All the shortcut types below use the same key list. Choose where Numen listens for those keys.",
    "launcher_shortcuts_title": "Home screen shortcuts",
    "launcher_shortcuts_description": "Press an assigned key on the home screen to open its app or action",
    "launcher_shortcuts_configure": "Key assignments",
    "launcher_shortcuts_configure_description": "Tap a key to choose what it opens; shared by all shortcut types",
    "quick_launcher_behaviour_description": "Which search opens, how results are sorted, and SYM+Space while typing",
    "quick_launcher_cosmetic_description": "Width, compact pill, what’s listed, favorites, nicknames and colors",
    "quick_launcher_respect_keyboard_layout_description": "Search uses the letters of your keyboard layout, not plain QWERTY",
    "quick_launcher_typo_tolerant_ranking_description": "Find apps even with small typos; closer matches still come first",
    "quick_launcher_text_field_shortcuts_hint": "In text fields, hold SYM and press Space. Tapping SYM won’t work here, since it switches SYM pages.",
    "quick_launcher_ranking_info_body": "Results are sorted in this order: exact app name, start of the app name, start of any word, abbreviations, names with small typos, then the app’s technical name. Typos are ignored for very short searches.",
    # Customize SYM layers.
    "sym_swap_pages_title": "SYM page order",
    "sym_swap_pages_description": "Drag or use the arrows to reorder; the switch shows or hides each page",
    "sym_modifiers_deeplink_description": "Choose which layer Alt types from, in Keys &amp; Modifiers",
    "sym_auto_close_title": "Close SYM automatically",
    "sym_auto_close_description": "Close SYM after you type a symbol or press Alt or Enter, not Ctrl or Shift",
    "sym_auto_close_touch_description": "Also close SYM after tapping a symbol on screen",
    "sym_reset_confirm_message": "Reset all SYM layers to their defaults? This can’t be undone.",
}
# Attribution text that must keep naming Pastiera.
KEEP = ()  # Attribution strings that must keep naming Pastiera (none at the moment).



def rebrand(text):
    # The QuickLauncher is "KeyLaunches" in Numen.
    text = text.replace("Pastiera-QuickLauncher", LAUNCHER)
    text = re.sub(r"\bQuickLauncher\b", LAUNCHER, text)
    text = re.sub(r"\b[Qq]uick [Ll]auncher\b", LAUNCHER, text)
    text = re.sub(rf"\b(?:the|Pastiera|Pastieras|Pastiera’s|Numen) {LAUNCHER}", LAUNCHER, text)
    text = re.sub(r"\bPastieras\b", f"{NAME}s", text)  # German possessive
    text = re.sub(r"\bPastiery\b", NAME, text)  # Polish genitive
    text = re.sub(r"\bPastiera\b", NAME, text)
    text = re.sub(r"\bpastiera\b(?![.\-/])", NAME.lower(), text)  # search keywords, not web addresses
    text = re.sub(r"\bPastierina\b", "Compact", text)  # other languages; English uses ENGLISH
    return re.sub(r"\bPlektra\b", NAME, text)


def main():
    written = 0
    for path in sorted(glob.glob(f"{MAIN}/values*/*.xml")):
        if os.path.basename(path) not in FILES:
            continue
        overrides = []
        english = os.path.basename(os.path.dirname(path)) == "values"
        for match in STRING.finditer(open(path, encoding="utf-8").read()):
            start, body, end = match.groups()
            if any(keep in start for keep in KEEP):
                continue
            name = re.search(r'name="([^"]+)"', start).group(1)
            if english and name in ENGLISH:
                overrides.append(f"    {start}{ENGLISH[name]}{end}")
            elif rebrand(body) != body:
                overrides.append(f"    {start}{rebrand(body)}{end}")
        if not overrides:
            continue
        folder = os.path.basename(os.path.dirname(path))
        out_dir = f"{OVERLAY}/{folder}"
        os.makedirs(out_dir, exist_ok=True)
        out = f"{out_dir}/numen_{os.path.basename(path)}"
        with open(out, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write("<!-- Generated by tools/numen_branding.py from Pastiera's strings. Do not edit. -->\n")
            f.write("<resources>\n" + "\n".join(overrides) + "\n</resources>\n")
        written += len(overrides)
        print(f"{out}: {len(overrides)} strings")
    print(f"total {written} strings rebranded")


if __name__ == "__main__":
    main()
