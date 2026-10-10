package it.palsoftware.pastiera

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.commands.CommandJson
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.PastieraCommandSource
import it.palsoftware.pastiera.core.Punctuation
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionActivationPolicy
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionPresentation
import it.palsoftware.pastiera.inputmethod.expansion.TextExpansionEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Manages the app settings.
 * Centralizes access to SharedPreferences for Pastiera settings.
 */
object SettingsManager {
    private const val TAG = "SettingsManager"
    private const val PREFS_NAME = "pastiera_prefs"
    
    // Settings keys
    private const val KEY_LONG_PRESS_THRESHOLD = "long_press_threshold"
    const val KEY_TYPING_SOUND_MODE = "typing_sound_mode"
    const val KEY_TYPING_SOUND_OUTPUT_MODE = "typing_sound_output_mode"
    const val KEY_TYPING_SOUND_CUSTOM_FILE_NAME = "typing_sound_custom_file_name"
    const val KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME = "typing_sound_custom_display_name"
    const val KEY_TYPING_SOUND_UPDATED_AT = "typing_sound_updated_at"
    private const val KEY_TAP_HAPTIC_USE_SYSTEM = "tap_haptic_use_system"
    private const val KEY_TAP_HAPTIC_DURATION_MS = "tap_haptic_duration_ms"
    private const val KEY_AUTO_CAPITALIZE_FIRST_LETTER = "auto_capitalize_first_letter"
    private const val KEY_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF =
        "auto_capitalize_respect_manual_shift_off"
    private const val KEY_AUTO_CAPITALIZE_RESTRICTED_FIELDS =
        "auto_capitalize_restricted_fields"
    private const val KEY_DOUBLE_SPACE_TO_PERIOD = "double_space_to_period"
    private const val KEY_SPACED_HYPHEN_TO_EN_DASH = "spaced_hyphen_to_en_dash"
    private const val KEY_SPACED_HYPHEN_DASH_STYLE = "spaced_hyphen_dash_style"
    private const val KEY_MID_WORD_QUOTE_TO_APOSTROPHE = "mid_word_quote_to_apostrophe"
    private const val KEY_COMMA_SPACE = "comma_space"
    private const val KEY_AUTO_SPACE_PUNCTUATION = "auto_space_punctuation"
    private const val KEY_SPACE_AFTER_PUNCTUATION = "space_after_punctuation"
    private const val KEY_SMART_QUOTES = "smart_quotes"
    private const val KEY_AUTO_SHOW_KEYBOARD = "auto_show_keyboard"
    private const val KEY_CLEAR_ALT_ON_SPACE = "clear_alt_on_space"
    private const val KEY_ALT_CTRL_SPEECH_SHORTCUT = "alt_ctrl_speech_shortcut"
    private const val KEY_SYM_MAPPINGS_CUSTOM = "sym_mappings_custom"
    private const val KEY_SYM_MAPPINGS_PAGE2_CUSTOM = "sym_mappings_page2_custom"
    private const val KEY_AUTO_CORRECT_ENABLED = "auto_correct_enabled"
    private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
    private const val KEY_SNIPPETS_ENABLED = "snippets_enabled"
    private const val KEY_SNIPPETS_PREFIX = "snippets_prefix"
    private const val KEY_SNIPPETS = "snippets_v1"
    private const val KEY_SNIPPETS_PRESENTATION = "snippets_presentation"
    private const val KEY_SNIPPETS_EXACT_ON_SPACE = "snippets_exact_on_space"
    private const val KEY_SNIPPETS_ACCEPT_PREFIX_WITH_SPACE = "snippets_accept_prefix_with_space"
    private const val KEY_SNIPPETS_ACCEPT_WITH_TAB = "snippets_accept_with_tab"
    private const val KEY_SNIPPETS_ACCEPT_WITH_ENTER = "snippets_accept_with_enter"
    private const val KEY_EMOJI_SHORTCODES_ENABLED = "emoji_shortcodes_enabled"
    private const val KEY_SYMBOL_SHORTCODES_ENABLED = "symbol_shortcodes_enabled"
    private const val KEY_EMOJI_SYMBOLS_PRESENTATION = "emoji_symbols_presentation"
    private const val KEY_EMOJI_SYMBOLS_EXACT_ON_SPACE = "emoji_symbols_exact_on_space"
    private const val KEY_EMOJI_SYMBOLS_ACCEPT_PREFIX_WITH_SPACE = "emoji_symbols_accept_prefix_with_space"
    private const val KEY_EMOJI_SYMBOLS_ACCEPT_WITH_TAB = "emoji_symbols_accept_with_tab"
    private const val KEY_EMOJI_SYMBOLS_ACCEPT_WITH_ENTER = "emoji_symbols_accept_with_enter"
    private const val KEY_EMOJI_SYMBOLS_EXACT_ON_CLOSE = "emoji_symbols_exact_on_close"
    private const val KEY_ACCENT_MATCHING_ENABLED = "accent_matching_enabled"
    private const val KEY_AUTO_REPLACE_ON_SPACE_ENTER = "auto_replace_on_space_enter"
    private const val KEY_MAX_AUTO_REPLACE_DISTANCE = "max_auto_replace_distance"
    private const val KEY_AUTO_CAPITALIZE_AFTER_PERIOD = "auto_capitalize_after_period"
    private const val KEY_LONG_PRESS_MODIFIER = "long_press_modifier" // "alt", "shift", "variations", or "sym"
    private const val KEY_TITAN2_ELITE_ROUNDED_CORNERS_ENFORCED_V1 =
        "titan2_elite_rounded_corners_enforced_v1"
    private const val KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE = "physical_keyboard_profile_override" // auto | titan | titan2 | titan2elite_qwerty
    private const val KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL = "physical_keyboard_currency_symbol" // Currency symbol for dedicated hardware keys
    private const val KEY_RESTORE_SYM_PAGE = "restore_sym_page" // SYM page to restore when returning from settings
    private const val KEY_PENDING_RESTORE_SYM_PAGE = "pending_restore_sym_page" // Temporary SYM page state saved when opening settings
    private const val KEY_SYM_PAGES_CONFIG = "sym_pages_config" // Order/enabled pages for SYM
    const val KEY_ALT_MODIFIER_BINDING = "alt_modifier_binding"
    internal const val LEGACY_KEY_ALT_CHARACTER_LAYER_BINDING = "alt_character_layer_binding"
    private const val KEY_SYM_AUTO_CLOSE = "sym_auto_close" // Auto-close SYM layout after key press
    private const val KEY_SYM_AUTO_CLOSE_ON_TOUCH = "sym_auto_close_on_touch" // Auto-close SYM layout after tapping on-screen SYM keys
    private const val KEY_SHIFT_TAP_LATCHES = "shift_tap_latches"
    private const val KEY_ALT_TAP_LATCHES = "alt_tap_latches"
    private const val KEY_CTRL_TAP_LATCHES = "ctrl_tap_latches"
    private const val KEY_ALT_LATCH_STAYS_ON_SPACE = "alt_latch_stays_on_space"
    private const val KEY_CTRL_LATCH_STAYS_ON_SPACE = "ctrl_latch_stays_on_space"
    private const val KEY_EMOJI_PICKER_EXPANDED_HEIGHT = "emoji_picker_expanded_height"
    private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed" // Whether the first-run tutorial has been completed
    private const val KEY_SWIPE_INCREMENTAL_THRESHOLD = "swipe_incremental_threshold" // Distance in DIP for cursor movement
    private const val KEY_STATIC_VARIATION_BAR_MODE = "static_variation_bar_mode" // Use static variation bar instead of dynamic cursor-based variations
    private const val KEY_STATIC_VARIATION_BAR_PRESET = "static_variation_bar_preset"
    private const val KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED = "static_variation_bar_base_layer_enabled" // Toggle top-row preset
    private const val KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION = "static_variation_bar_modifier_hold_restoration"
    private const val KEY_VARIATIONS_UPDATED = "variations_updated" // Trigger for reloading variations in input method service
    private const val KEY_CLIPBOARD_HISTORY_ENABLED = "clipboard_history_enabled" // Whether clipboard history is enabled
    private const val KEY_CLIPBOARD_RETENTION_TIME = "clipboard_retention_time" // How long to keep clipboard entries (in minutes)
    private const val KEY_SHIFT_BACKSPACE_DELETE = "shift_backspace_delete" // Shift + Backspace performs forward delete
    private const val KEY_ALT_BACKSPACE_DELETE = "alt_backspace_delete" // Alt + Backspace performs forward delete
    private const val KEY_BACKSPACE_AT_START_DELETE = "backspace_at_start_delete" // Backspace at line start performs forward delete
    private const val KEY_PASTIERINA_MODE_OVERRIDE = "pastierina_mode_override" // pastierina | full_status_bar
    private const val KEY_PASTIERINA_MODE_ACTIVE = "pastierina_mode_active" // Current effective state
    private const val KEY_SOFTWARE_KEYBOARD_MODE = "software_keyboard_mode" // auto | force_hardware | force_virtual
    const val KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE = "software_keyboard_mode_runtime_override"
    private const val KEY_SOFTWARE_KEYBOARD_LAYOUT_STYLE = "software_keyboard_layout_style" // compact | extended_iso | full_ansi | full_iso
    private const val KEY_SOFTWARE_KEYBOARD_NUMBER_ROW_ENABLED = "software_keyboard_number_row_enabled"
    private const val KEY_SOFTWARE_KEYBOARD_NEAREST_KEY_TOUCH_ENABLED = "software_keyboard_nearest_key_touch_enabled"
    private const val KEY_SOFTWARE_KEYBOARD_LEFT_MODIFIER_KEY = "software_keyboard_left_modifier_key"
    private const val KEY_SOFTWARE_KEYBOARD_RIGHT_MODIFIER_KEY = "software_keyboard_right_modifier_key"
    private const val KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED = "software_keyboard_long_press_layer_popup_enabled"
    private const val KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY = "software_keyboard_long_press_layer_popup_below_key"
    private const val KEY_TITAN2_LAYOUT_ENABLED = "titan2_layout_enabled" // Align OSK with Titan 2 physical layout
    const val KEY_TITAN2_ELITE_MAX_ICON_SHRINK = "titan2_elite_max_icon_shrink"
    const val KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER = "titan2_elite_top_corner_multiplier"
    const val KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS = "titan2_elite_rounded_corner_insets"
    const val KEY_TITAN2_ELITE_FILL_BOTTOM_CORNERS = "titan2_elite_fill_bottom_corners"
    private const val KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED = "accessibility_live_announcements_enabled" // Whether status bar accessibility live announcements are enabled
    private const val KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED = "accessibility_read_second_row_enabled" // Whether TalkBack should read quick settings/variations row
    private const val KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = "accessibility_suggestions_announcement_delay_ms" // Delay before suggestions become accessible again while typing
    private const val KEY_BOUNCE_KEYS_ENABLED = "bounce_keys_enabled" // Whether repeated same-key taps inside the delay are ignored
    private const val KEY_BOUNCE_KEYS_DELAY_MS = "bounce_keys_delay_ms" // Minimum delay before the same key can be accepted again
    private const val KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = "bounce_keys_character_keys_enabled"
    private const val KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = "bounce_keys_modifier_keys_enabled"
    private const val KEY_BOUNCE_KEYS_SPACE_ENABLED = "bounce_keys_space_enabled"
    private const val KEY_BOUNCE_KEYS_ENTER_ENABLED = "bounce_keys_enter_enabled"
    private const val KEY_BOUNCE_KEYS_BACKSPACE_ENABLED = "bounce_keys_backspace_enabled"
    private const val KEY_OVERLAPPING_KEYS_ENABLED = "overlapping_keys_enabled"
    private const val KEY_APP_ENTER_BEHAVIOR_ENABLED = "app_enter_behavior_enabled"
    private const val KEY_APP_ENTER_BEHAVIOR_PRESET = "app_enter_behavior_preset"
    private const val KEY_APP_ENTER_BEHAVIOR_OVERRIDES = "app_enter_behavior_overrides"
    const val KEY_KEYBOARD_THEME_HARDWARE = "keyboard_theme_hardware"
    const val KEY_KEYBOARD_THEME_SOFTWARE = "keyboard_theme_software"
    private const val KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_HARDWARE = "keyboard_theme_assignment_mode_hardware"
    private const val KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_SOFTWARE = "keyboard_theme_assignment_mode_software"
    private const val KEY_KEYBOARD_THEME_LIGHT_HARDWARE = "keyboard_theme_light_hardware"
    private const val KEY_KEYBOARD_THEME_LIGHT_SOFTWARE = "keyboard_theme_light_software"
    private const val KEY_KEYBOARD_THEME_DARK_HARDWARE = "keyboard_theme_dark_hardware"
    private const val KEY_KEYBOARD_THEME_DARK_SOFTWARE = "keyboard_theme_dark_software"
    // Written by 1.0.1 and earlier; read once by migrateKeyboardThemeLayoutOverrides, then removed.
    private const val LEGACY_KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_HARDWARE = "keyboard_theme_layout_overrides_hardware"
    private const val LEGACY_KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_SOFTWARE = "keyboard_theme_layout_overrides_software"
    const val KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED = "fixed"
    const val KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM = "follow_system"
    const val KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN = 1f
    const val KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX = 1.8f
    const val KEYBOARD_THEME_POPUP_STYLE_FLOATING = "floating"
    const val KEYBOARD_THEME_POPUP_STYLE_CLASSIC = "classic"
    private const val KEY_KEYBOARD_THEME_SAVED_THEMES = "keyboard_theme_saved_themes"
    private const val KEY_KEYBOARD_THEME_DRAFTS = "keyboard_theme_drafts"
    private const val KEY_KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE = "keyboard_theme_preview_viewport_scale"
    
    // Status bar button slot configuration keys
    private const val KEY_STATUS_BAR_SLOT_LEFT = "status_bar_slot_left"
    private const val KEY_STATUS_BAR_SLOT_RIGHT_1 = "status_bar_slot_right_1"
    private const val KEY_STATUS_BAR_SLOT_RIGHT_2 = "status_bar_slot_right_2"
    private const val KEY_STATUS_BAR_SLOTS_LEFT = "status_bar_slots_left"
    private const val KEY_STATUS_BAR_SLOTS_RIGHT = "status_bar_slots_right"
    private const val KEY_PASTIERINA_STATUS_BAR_SLOTS_LEFT = "pastierina_status_bar_slots_left"
    private const val KEY_PASTIERINA_STATUS_BAR_SLOTS_RIGHT = "pastierina_status_bar_slots_right"
    private const val KEY_STATUS_BAR_VARIATIONS_VISIBLE = "status_bar_variations_visible"
    private const val KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT = "dynamic_variation_bar_slot_count"
    private const val KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT = "dynamic_variation_bar_resize_to_content"
    const val KEY_MODIFIER_INDICATOR_MODE = "modifier_indicator_mode"
    
    // Public constants for button IDs
    const val STATUS_BAR_BUTTON_NONE = "none"
    const val STATUS_BAR_BUTTON_CLIPBOARD = "clipboard"
    const val STATUS_BAR_BUTTON_MICROPHONE = "microphone"
    const val STATUS_BAR_BUTTON_EMOJI = "emoji"
    // Removed with other languages; a saved "language" button becomes the emoji button.
    private const val LEGACY_STATUS_BAR_BUTTON_LANGUAGE = "language"
    const val STATUS_BAR_BUTTON_HAMBURGER = "hamburger"
    const val STATUS_BAR_BUTTON_MINIMAL_UI = "minimal_ui"
    const val STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE = "software_keyboard_mode"
    const val STATUS_BAR_BUTTON_SETTINGS = "settings"
    const val STATUS_BAR_BUTTON_SYMBOLS = "symbols"
    const val STATUS_BAR_BUTTON_UNDO = "undo"
    const val STATUS_BAR_BUTTON_REDO = "redo"
    const val MODIFIER_INDICATOR_BOTTOM_STRIP = "bottom_strip"
    const val MODIFIER_INDICATOR_MENU_BAR = "menu_bar"
    const val MODIFIER_INDICATOR_STATUS_BAR = "status_bar"
    const val MODIFIER_INDICATOR_MODE_BOTTOM = "bottom"
    const val MODIFIER_INDICATOR_MODE_BOTTOM_AND_MENU = "bottom_and_menu"
    const val MODIFIER_INDICATOR_MODE_MENU = "menu"
    const val MODIFIER_INDICATOR_MODE_OFF = "off"

    const val STATIC_VARIATION_PRESET_OFF = "off"
    const val STATIC_VARIATION_PRESET_SYMBOLS = "symbols"
    const val STATIC_VARIATION_PRESET_NUMBERS = "numbers"
    const val STATIC_VARIATION_PRESET_ALTERNATIVE = "alternative"
    const val STATIC_VARIATION_PRESET_DEV_CHOICE = "dev_choice"

    const val ENTER_BEHAVIOR_PRESET_APP_DEFAULT = "app_default"
    const val ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE = "enter_send_shift_newline"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND = "enter_newline_ctrl_send"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY = "enter_newline_only"
    const val ENTER_BEHAVIOR_PRESET_CUSTOM = "custom"

    const val ENTER_BEHAVIOR_APP_DEFAULT = "app_default"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE = "enter_newline"
    const val ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE = "enter_send_shift_newline"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND = "enter_newline_ctrl_send"

    const val ENTER_SEND_STRATEGY_AUTO = "auto"
    const val ENTER_SEND_STRATEGY_EDITOR_ACTION = "editor_action"
    const val ENTER_SEND_STRATEGY_CTRL_ENTER = "ctrl_enter"
    const val ENTER_SEND_STRATEGY_PLAIN_ENTER = "plain_enter"
    const val ENTER_ADDITIONAL_SEND_SHORTCUT_NONE = "none"
    const val ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER = "sym_enter"
    
    // Default slot assignments
    // The default bar is just emoji on the left and dictation on the right.
    private const val DEFAULT_SLOT_LEFT = STATUS_BAR_BUTTON_EMOJI
    private const val DEFAULT_SLOT_RIGHT_1 = STATUS_BAR_BUTTON_MICROPHONE
    private const val DEFAULT_SLOT_RIGHT_2 = STATUS_BAR_BUTTON_NONE
    private const val DEFAULT_PASTIERINA_SLOT_LEFT = STATUS_BAR_BUTTON_EMOJI
    private const val DEFAULT_PASTIERINA_SLOT_RIGHT = STATUS_BAR_BUTTON_MICROPHONE
    // Accent variations are for other languages; Numen types English.
    private const val DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE = false
    private const val DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 7
    private const val DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT = false
    private val DEFAULT_MODIFIER_INDICATORS = setOf(MODIFIER_INDICATOR_BOTTOM_STRIP)
    const val MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 1
    const val MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 9

    private const val VARIATIONS_FILE_NAME = "variations.json"
    
    // Default values
    private const val DEFAULT_LONG_PRESS_THRESHOLD = 300L
    const val TYPING_SOUND_MODE_OFF = "off"
    const val TYPING_SOUND_MODE_CLICK = "click"
    const val TYPING_SOUND_MODE_TYPEWRITER = "typewriter"
    const val TYPING_SOUND_MODE_CUSTOM = "custom"
    const val TYPING_SOUND_OUTPUT_MEDIA = "media"
    const val TYPING_SOUND_OUTPUT_SYSTEM = "system"
    const val TYPING_SOUND_OUTPUT_NOTIFICATION = "notification"
    private const val DEFAULT_TYPING_SOUND_MODE = TYPING_SOUND_MODE_OFF
    private const val DEFAULT_TYPING_SOUND_OUTPUT_MODE = TYPING_SOUND_OUTPUT_MEDIA
    private const val DEFAULT_TAP_HAPTIC_USE_SYSTEM = true
    private const val DEFAULT_TAP_HAPTIC_DURATION_MS = 25L
    private const val MIN_TAP_HAPTIC_DURATION_MS = 5L
    private const val MAX_TAP_HAPTIC_DURATION_MS = 80L
    internal const val TYPING_SOUND_CUSTOM_DIR = "typing_sounds"
    internal const val TYPING_SOUND_CUSTOM_PACK_DIR = "custom_pack"
    internal const val TYPING_SOUND_MAX_FILE_BYTES = 2L * 1024L * 1024L
    internal const val TYPING_SOUND_MAX_PACK_BYTES = 16L * 1024L * 1024L
    internal const val TYPING_SOUND_MAX_PACK_FILES = 96
    internal val TYPING_SOUND_GROUPS = setOf("normal", "space", "backspace", "enter", "modifier")
    internal val TYPING_SOUND_AUDIO_EXTENSIONS = setOf("ogg", "wav", "mp3", "m4a")
    private const val MIN_LONG_PRESS_THRESHOLD = 50L
    private const val MAX_LONG_PRESS_THRESHOLD = 1000L
    private const val DEFAULT_SWIPE_INCREMENTAL_THRESHOLD = 9.6f
    private const val MIN_SWIPE_INCREMENTAL_THRESHOLD = 3f
    private const val MAX_SWIPE_INCREMENTAL_THRESHOLD = 25f
    private const val DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER = true
    private const val DEFAULT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF = true
    private const val DEFAULT_AUTO_CAPITALIZE_RESTRICTED_FIELDS = false
    private const val DEFAULT_DOUBLE_SPACE_TO_PERIOD = true
    private const val DEFAULT_SPACED_HYPHEN_TO_EN_DASH = false
    const val DASH_STYLE_EN = "en_dash"
    const val DASH_STYLE_EM = "em_dash"
    private const val DEFAULT_SPACED_HYPHEN_DASH_STYLE = DASH_STYLE_EN
    private const val DEFAULT_MID_WORD_QUOTE_TO_APOSTROPHE = false
    private const val DEFAULT_COMMA_SPACE = false
    private const val DEFAULT_AUTO_SPACE_PUNCTUATION = Punctuation.DEFAULT_AUTO_SPACE
    private const val DEFAULT_SPACE_AFTER_PUNCTUATION = ""
    private const val DEFAULT_SMART_QUOTES = false
    // Numen stays silent by default: the hardware keyboard needs no on-screen bar until SYM.
    private val DEFAULT_AUTO_SHOW_KEYBOARD = false
    private const val DEFAULT_CLEAR_ALT_ON_SPACE = true
    private const val DEFAULT_ALT_CTRL_SPEECH_SHORTCUT = true
    private const val DEFAULT_AUTO_CORRECT_ENABLED = true
    // The suggestion row stays hidden unless turned on; autocorrect works without it.
    private const val DEFAULT_SUGGESTIONS_ENABLED = false
    private const val DEFAULT_SNIPPETS_ENABLED = false
    private const val DEFAULT_SNIPPETS_PREFIX = "!"
    private const val DEFAULT_ACCENT_MATCHING_ENABLED = true
    // Autocorrect starts on in Numen and off in Pastiera.
    private val DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER = true
    private const val DEFAULT_MAX_AUTO_REPLACE_DISTANCE = 1
    private const val DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD = true
    private const val DEFAULT_LONG_PRESS_MODIFIER = "alt"
    private const val DEFAULT_KEYBOARD_LAYOUT = "qwerty"
    private const val KEY_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS = "software_keyboard_mode_toggle_toasts"
    private const val DEFAULT_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS = true
    private const val DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED = true
    private const val DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY = true
    private const val DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE = "auto"
    private const val DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL = "€"
    private const val DEFAULT_SYM_AUTO_CLOSE = true
    private const val DEFAULT_SYM_AUTO_CLOSE_ON_TOUCH = true
    private const val DEFAULT_MODIFIER_TAP_LATCHES = false
    private const val DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE = false
    private const val DEFAULT_BOUNCE_KEYS_ENABLED = false
    private const val DEFAULT_BOUNCE_KEYS_DELAY_MS = 80L
    private const val MIN_BOUNCE_KEYS_DELAY_MS = 20L
    private const val MAX_BOUNCE_KEYS_DELAY_MS = 500L
    private const val DEFAULT_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = true
    private const val DEFAULT_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = false
    private const val DEFAULT_BOUNCE_KEYS_SPACE_ENABLED = true
    private const val DEFAULT_BOUNCE_KEYS_ENTER_ENABLED = true
    private const val DEFAULT_BOUNCE_KEYS_BACKSPACE_ENABLED = true
    private const val DEFAULT_OVERLAPPING_KEYS_ENABLED = false
    private const val DEFAULT_EMOJI_PICKER_EXPANDED_HEIGHT = true
    private val DEFAULT_SYM_PAGES_CONFIG = SymPagesConfig()
    private const val SYM_PAGES_SCHEMA_VERSION = 2
    private const val DEFAULT_STATIC_VARIATION_BAR_MODE = false
    private const val DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED = false
    private const val DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED = true
    private const val DEFAULT_SUGGESTION_DEBUG_LOGGING = true
    private const val KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED = "experimental_suggestions_enabled"
    private const val KEY_SUGGESTION_DEBUG_LOGGING = "suggestion_debug_logging"
    private const val KEY_IME_OVERLAY_DEBUG_LOGGING = "ime_overlay_debug_logging"
    private const val KEY_EXPERIMENTAL_CANDIDATES_VIEW_ENABLED = "experimental_candidates_view_enabled"
    private const val KEY_USE_KEYBOARD_PROXIMITY = "use_keyboard_proximity"
    private const val KEY_USE_EDIT_TYPE_RANKING = "use_edit_type_ranking"
    private const val KEY_AGGRESSIVE_AUTO_REPLACE = "aggressive_auto_replace"
    private const val KEY_CONTEXT_AWARE_CORRECTION = "context_aware_correction"

    private const val DEFAULT_USE_KEYBOARD_PROXIMITY = false
    private const val DEFAULT_USE_EDIT_TYPE_RANKING = false
    private val DEFAULT_AGGRESSIVE_AUTO_REPLACE = true
    // Sentence-aware fixes start on in Numen and off in Pastiera.
    private val DEFAULT_CONTEXT_AWARE_CORRECTION = true
    private const val DEFAULT_IME_OVERLAY_DEBUG_LOGGING = false
    private const val DEFAULT_CLIPBOARD_HISTORY_ENABLED = true
    private const val DEFAULT_CLIPBOARD_RETENTION_TIME = 120L // 2 hours in minutes
    private const val DEFAULT_SHIFT_BACKSPACE_DELETE = false
    private const val DEFAULT_ALT_BACKSPACE_DELETE = false
    private const val DEFAULT_BACKSPACE_AT_START_DELETE = false
    private const val DEFAULT_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED = false
    private const val DEFAULT_ACCESSIBILITY_READ_SECOND_ROW_ENABLED = false
    private const val DEFAULT_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 500L
    private const val MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 100L
    private const val MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 2000L
    private val STATIC_VARIATION_BASE_PRESET_DEFAULT = listOf("@", "\"", ":", "!", "?", ",", ".")
    private val STATIC_VARIATION_BASE_PRESET_NUMBERS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    private val STATIC_VARIATION_BASE_PRESET_ALTERNATIVE = listOf("[", "]", "$", "%", "^", "&", "\\")
    private val STATIC_VARIATION_BASE_PRESET_DEV_CHOICE = listOf("»", "«", ";", "!", "?", ",", ".", "–", "%")
    private val STATIC_VARIATION_SHIFT_PRESET_DEFAULT = listOf("{", "}", "€", "=", "~", ";", "¿")
    private val STATIC_VARIATION_ALT_PRESET_DEFAULT = listOf("<", ">", "¥", "|", "`", "´", "°")

    enum class StatusBarPresentationMode(val storageValue: String) {
        PASTIERINA("pastierina"),
        FULL_STATUS_BAR("full_status_bar")
    }

    enum class SoftwareKeyboardMode(val storageValue: String) {
        AUTO("auto"),
        FORCE_HARDWARE("force_hardware"),
        FORCE_VIRTUAL("force_virtual")
    }

    enum class SoftwareKeyboardLayoutStyle(val storageValue: String) {
        COMPACT("compact"),
        EXTENDED_ISO("extended_iso"),
        FULL_ANSI("full_ansi"),
        FULL_ISO("full_iso")
    }

    enum class SoftwareKeyboardModifierKey(val storageValue: String) {
        CTRL("ctrl"),
        ALT("alt")
    }

    enum class KeyboardThemeTarget {
        HARDWARE,
        SOFTWARE
    }

    data class KeyboardThemeSettings(
        val background: Int,
        val divider: Int,
        val normalKey: Int,
        val specialKey: Int,
        val textAndIcons: Int,
        val ledInactive: Int,
        val ledActive: Int,
        val ledLocked: Int,
        val accent: Int,
        val cursorSwipe: Int = accent,
        val keyPopup: Int = specialKey,
        val keyPopupSelected: Int = accent,
        val suggestion: Int = normalKey,
        val statusBarButton: Int = specialKey,
        val keyCornerRadiusRatio: Float = 0.08f,
        val chromeCornerRadiusRatio: Float = 0.08f,
        val keyHeightScale: Float = 1f,
        val numberRowHeightScale: Float = 0.8f,
        val keyWidthScale: Float = 1f,
        val rowGapScale: Float = 0f,
        val distributeHorizontalSpacing: Boolean = true,
        val ortholinear: Boolean = false,
        val showLeds: Boolean = true,
        val suggestionsHeightScale: Float = 1f,
        val variationsHeightScale: Float = 1f,
        val keyPopupStyle: String = KEYBOARD_THEME_POPUP_STYLE_FLOATING,
        val keyPopupAttached: Boolean = true,
        val keyPopupTailEnabled: Boolean = true,
        val keyPreviewAfterLongPress: Boolean = false,
        val keyAlternatesPopupEnabled: Boolean = true
    ) {
        fun toKeyboardThemeColors(): KeyboardThemeColors =
            KeyboardThemeColors(
                background = background,
                divider = divider,
                normalKey = normalKey,
                specialKey = specialKey,
                textAndIcons = textAndIcons,
                ledInactive = ledInactive,
                ledActive = ledActive,
                ledLocked = ledLocked,
                accent = accent,
                cursorSwipe = cursorSwipe,
                keyPopup = keyPopup,
                keyPopupSelected = keyPopupSelected,
                suggestion = suggestion,
                statusBarButton = statusBarButton,
                keyCornerRadiusRatio = keyCornerRadiusRatio,
                chromeCornerRadiusRatio = chromeCornerRadiusRatio,
                suggestionsHeightScale = suggestionsHeightScale,
                variationsHeightScale = variationsHeightScale
            )
    }

    data class NamedKeyboardTheme(
        val name: String,
        val theme: KeyboardThemeSettings
    )

    data class KeyboardThemeDraft(
        val name: String,
        val theme: KeyboardThemeSettings,
        val populatedFields: Set<String> = emptySet()
    )

    /**
     * Returns the SharedPreferences instance for Pastiera.
     */
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getStatusBarPresentationMode(context: Context): StatusBarPresentationMode {
        val value = getPreferences(context).getString(
            KEY_PASTIERINA_MODE_OVERRIDE,
            StatusBarPresentationMode.FULL_STATUS_BAR.storageValue
        )
        return when (value) {
            StatusBarPresentationMode.PASTIERINA.storageValue,
            "force_minimal" -> StatusBarPresentationMode.PASTIERINA
            else -> StatusBarPresentationMode.FULL_STATUS_BAR
        }
    }

    fun setStatusBarPresentationMode(context: Context, mode: StatusBarPresentationMode) {
        getPreferences(context).edit()
            .putString(KEY_PASTIERINA_MODE_OVERRIDE, mode.storageValue)
            .apply()
    }

    fun getPastierinaModeActive(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_PASTIERINA_MODE_ACTIVE, false)
    }

    fun setPastierinaModeActive(context: Context, isActive: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_PASTIERINA_MODE_ACTIVE, isActive)
            .apply()
    }

    fun getSoftwareKeyboardMode(context: Context): SoftwareKeyboardMode {
        val value = getPreferences(context).getString(
            KEY_SOFTWARE_KEYBOARD_MODE,
            SoftwareKeyboardMode.AUTO.storageValue
        )
        return SoftwareKeyboardMode.values().firstOrNull { it.storageValue == value }
            ?: SoftwareKeyboardMode.AUTO
    }

    fun setSoftwareKeyboardMode(context: Context, mode: SoftwareKeyboardMode) {
        getPreferences(context).edit()
            .putString(KEY_SOFTWARE_KEYBOARD_MODE, mode.storageValue)
            .remove(KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE)
            .apply()
    }

    fun getSoftwareKeyboardModeRuntimeOverride(context: Context): SoftwareKeyboardMode? {
        val value = getPreferences(context).getString(
            KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE,
            null
        ) ?: return null
        return SoftwareKeyboardMode.values()
            .firstOrNull { it.storageValue == value && it != SoftwareKeyboardMode.AUTO }
    }

    fun setSoftwareKeyboardModeRuntimeOverride(
        context: Context,
        mode: SoftwareKeyboardMode?
    ) {
        val editor = getPreferences(context).edit()
        if (mode == null || mode == SoftwareKeyboardMode.AUTO) {
            editor.remove(KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE)
        } else {
            editor.putString(KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE, mode.storageValue)
        }
        editor.apply()
    }

    fun getSoftwareKeyboardModeToggleToastsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS,
            DEFAULT_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS
        )
    }

    fun setSoftwareKeyboardModeToggleToastsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS, enabled)
            .apply()
    }

    fun getSoftwareKeyboardLayoutStyle(context: Context): SoftwareKeyboardLayoutStyle {
        val value = getPreferences(context).getString(
            KEY_SOFTWARE_KEYBOARD_LAYOUT_STYLE,
            SoftwareKeyboardLayoutStyle.COMPACT.storageValue
        )
        return SoftwareKeyboardLayoutStyle.values().firstOrNull { it.storageValue == value }
            ?: SoftwareKeyboardLayoutStyle.COMPACT
    }

    fun setSoftwareKeyboardLayoutStyle(context: Context, style: SoftwareKeyboardLayoutStyle) {
        getPreferences(context).edit()
            .putString(KEY_SOFTWARE_KEYBOARD_LAYOUT_STYLE, style.storageValue)
            .apply()
    }

    fun getSoftwareKeyboardNumberRowEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_SOFTWARE_KEYBOARD_NUMBER_ROW_ENABLED, true)

    fun setSoftwareKeyboardNumberRowEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SOFTWARE_KEYBOARD_NUMBER_ROW_ENABLED, enabled)
            .apply()
    }

    fun getSoftwareKeyboardNearestKeyTouchEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_SOFTWARE_KEYBOARD_NEAREST_KEY_TOUCH_ENABLED, true)

    fun setSoftwareKeyboardNearestKeyTouchEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SOFTWARE_KEYBOARD_NEAREST_KEY_TOUCH_ENABLED, enabled)
            .apply()
    }

    fun getSoftwareKeyboardLongPressLayerPopupEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(
            KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED,
            DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED
        )

    fun setSoftwareKeyboardLongPressLayerPopupEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED, enabled)
            .apply()
    }

    fun getSoftwareKeyboardLongPressLayerPopupBelowKey(context: Context): Boolean =
        getPreferences(context).getBoolean(
            KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY,
            DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY
        )

    fun setSoftwareKeyboardLongPressLayerPopupBelowKey(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY, enabled)
            .apply()
    }

    fun getSoftwareKeyboardLeftModifierKey(context: Context): SoftwareKeyboardModifierKey =
        getSoftwareKeyboardModifierKey(
            context = context,
            key = KEY_SOFTWARE_KEYBOARD_LEFT_MODIFIER_KEY,
            defaultValue = SoftwareKeyboardModifierKey.CTRL
        )

    fun setSoftwareKeyboardLeftModifierKey(context: Context, modifierKey: SoftwareKeyboardModifierKey) {
        getPreferences(context).edit()
            .putString(KEY_SOFTWARE_KEYBOARD_LEFT_MODIFIER_KEY, modifierKey.storageValue)
            .apply()
    }

    fun getSoftwareKeyboardRightModifierKey(context: Context): SoftwareKeyboardModifierKey =
        getSoftwareKeyboardModifierKey(
            context = context,
            key = KEY_SOFTWARE_KEYBOARD_RIGHT_MODIFIER_KEY,
            defaultValue = SoftwareKeyboardModifierKey.ALT
        )

    fun setSoftwareKeyboardRightModifierKey(context: Context, modifierKey: SoftwareKeyboardModifierKey) {
        getPreferences(context).edit()
            .putString(KEY_SOFTWARE_KEYBOARD_RIGHT_MODIFIER_KEY, modifierKey.storageValue)
            .apply()
    }

    private fun getSoftwareKeyboardModifierKey(
        context: Context,
        key: String,
        defaultValue: SoftwareKeyboardModifierKey
    ): SoftwareKeyboardModifierKey {
        val value = getPreferences(context).getString(key, defaultValue.storageValue)
        return SoftwareKeyboardModifierKey.values().firstOrNull { it.storageValue == value }
            ?: defaultValue
    }

    fun defaultKeyboardTheme(): KeyboardThemeSettings =
        KeyboardThemeSettings(
            background = 0xFFF2F2F2.toInt(),
            divider = 0xFFB8B8B8.toInt(),
            normalKey = 0xFFFAFAFA.toInt(),
            specialKey = 0xFFDDDDDD.toInt(),
            textAndIcons = 0xFF111111.toInt(),
            ledInactive = 0xFFB0B0B0.toInt(),
            ledActive = 0xFF555555.toInt(),
            ledLocked = 0xFF111111.toInt(),
            accent = 0xFF3F8C96.toInt(),
            cursorSwipe = 0xFF3F8C96.toInt(),
            keyPopup = 0xFFDDDDDD.toInt(),
            keyPopupSelected = 0xFF3F8C96.toInt(),
            suggestion = 0xFFFAFAFA.toInt(),
            statusBarButton = 0xFFDDDDDD.toInt()
        )

    private fun defaultKeyboardTheme(target: KeyboardThemeTarget): KeyboardThemeSettings =
        when (target) {
            KeyboardThemeTarget.HARDWARE -> defaultKeyboardTheme()
            KeyboardThemeTarget.SOFTWARE -> defaultKeyboardTheme().copy(
                keyCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS,
                chromeCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS,
                keyHeightScale = SOFTWARE_THEME_DEFAULT_KEY_HEIGHT,
                numberRowHeightScale = SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT,
                rowGapScale = SOFTWARE_THEME_DEFAULT_ROW_GAP,
                ortholinear = true,
                showLeds = false,
                suggestionsHeightScale = SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
                variationsHeightScale = SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT
            )
        }

    private fun defaultSystemKeyboardTheme(target: KeyboardThemeTarget, dark: Boolean): KeyboardThemeSettings {
        val base = if (dark) {
            KeyboardThemeSettings(
                background = 0xFF000000.toInt(),
                divider = 0xFF2C3136.toInt(),
                normalKey = 0xFF15191D.toInt(),
                specialKey = 0xFF2B3138.toInt(),
                textAndIcons = 0xFFEFEFEF.toInt(),
                ledInactive = 0xFF303030.toInt(),
                ledActive = 0xFF6496FF.toInt(),
                ledLocked = 0xFFF76300.toInt(),
                accent = 0xFF6496FF.toInt(),
                cursorSwipe = 0xFF6496FF.toInt(),
                keyPopup = 0xFF2B3138.toInt(),
                keyPopupSelected = 0xFF6496FF.toInt(),
                suggestion = 0xFF15191D.toInt(),
                statusBarButton = 0xFF2B3138.toInt(),
                keyCornerRadiusRatio = 0.10f,
                chromeCornerRadiusRatio = 0.10f
            )
        } else {
            KeyboardThemeSettings(
                background = 0xFFF8FAFC.toInt(),
                divider = 0xFFC7CDD4.toInt(),
                normalKey = 0xFFFFFFFF.toInt(),
                specialKey = 0xFFE0E6EE.toInt(),
                textAndIcons = 0xFF171A1F.toInt(),
                ledInactive = 0xFFD1D5DB.toInt(),
                ledActive = 0xFF276EF1.toInt(),
                ledLocked = 0xFFD65A00.toInt(),
                accent = 0xFF276EF1.toInt(),
                cursorSwipe = 0xFF276EF1.toInt(),
                keyPopup = 0xFFE0E6EE.toInt(),
                keyPopupSelected = 0xFF276EF1.toInt(),
                suggestion = 0xFFFFFFFF.toInt(),
                statusBarButton = 0xFFE0E6EE.toInt(),
                keyCornerRadiusRatio = 0.10f,
                chromeCornerRadiusRatio = 0.10f
            )
        }
        return when (target) {
            KeyboardThemeTarget.HARDWARE -> base
            KeyboardThemeTarget.SOFTWARE -> base.copy(
                keyCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS,
                chromeCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS,
                keyHeightScale = SOFTWARE_THEME_DEFAULT_KEY_HEIGHT,
                numberRowHeightScale = SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT,
                rowGapScale = SOFTWARE_THEME_DEFAULT_ROW_GAP,
                ortholinear = true,
                showLeds = false,
                suggestionsHeightScale = SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
                variationsHeightScale = SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT
            )
        }
    }

    fun keyboardThemeKeyForTarget(target: KeyboardThemeTarget): String =
        when (target) {
            KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_HARDWARE
            KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_SOFTWARE
        }

    private fun keyboardThemeAssignmentModeKeyForTarget(target: KeyboardThemeTarget): String =
        when (target) {
            KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_HARDWARE
            KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_SOFTWARE
        }

    private fun keyboardThemeLightKeyForTarget(target: KeyboardThemeTarget): String =
        when (target) {
            KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_LIGHT_HARDWARE
            KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_LIGHT_SOFTWARE
        }

    private fun keyboardThemeDarkKeyForTarget(target: KeyboardThemeTarget): String =
        when (target) {
            KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_DARK_HARDWARE
            KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_DARK_SOFTWARE
        }

    private fun legacyKeyboardThemeLayoutOverridesKeyForTarget(target: KeyboardThemeTarget): String =
        when (target) {
            KeyboardThemeTarget.HARDWARE -> LEGACY_KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_HARDWARE
            KeyboardThemeTarget.SOFTWARE -> LEGACY_KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_SOFTWARE
        }

    fun isKeyboardThemePreferenceKey(key: String?): Boolean {
        return key == KEY_KEYBOARD_THEME_HARDWARE || key == KEY_KEYBOARD_THEME_SOFTWARE
    }

    fun isModifierIndicatorPreferenceKey(key: String?): Boolean {
        return key == KEY_MODIFIER_INDICATOR_MODE
    }

    fun getKeyboardThemePreviewViewportScale(context: Context): Float =
        getPreferences(context)
            .getFloat(KEY_KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE, KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN)
            .coerceIn(KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN, KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX)

    fun setKeyboardThemePreviewViewportScale(context: Context, scale: Float) {
        getPreferences(context).edit()
            .putFloat(
                KEY_KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE,
                scale.coerceIn(
                    KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN,
                    KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX
                )
            )
            .apply()
    }

    fun getKeyboardTheme(context: Context, target: KeyboardThemeTarget): KeyboardThemeSettings {
        val defaults = defaultKeyboardTheme(target)
        val stored = getPreferences(context).getString(keyboardThemeKeyForTarget(target), null)
            ?: return defaults
        return try {
            val json = JSONObject(stored)
            KeyboardThemeSettings(
                background = json.optInt("background", defaults.background),
                divider = json.optInt("divider", defaults.divider),
                normalKey = json.optInt("normal_key", defaults.normalKey),
                specialKey = json.optInt("special_key", defaults.specialKey),
                textAndIcons = json.optInt("text_and_icons", defaults.textAndIcons),
                ledInactive = json.optInt("led_inactive", defaults.ledInactive),
                ledActive = json.optInt("led_active", defaults.ledActive),
                ledLocked = json.optInt("led_locked", defaults.ledLocked),
                accent = json.optInt("accent", defaults.accent),
                cursorSwipe = json.optInt("cursor_swipe", defaults.cursorSwipe),
                keyPopup = json.optInt("key_popup", defaults.keyPopup),
                keyPopupSelected = json.optInt("key_popup_selected", defaults.keyPopupSelected),
                suggestion = json.optInt("suggestion", defaults.suggestion),
                statusBarButton = json.optInt("status_bar_button", defaults.statusBarButton),
                keyCornerRadiusRatio = json.optDouble("key_corner_radius_ratio", defaults.keyCornerRadiusRatio.toDouble()).toFloat(),
                chromeCornerRadiusRatio = json.optDouble("chrome_corner_radius_ratio", defaults.chromeCornerRadiusRatio.toDouble()).toFloat(),
                keyHeightScale = json.optDouble("key_height_scale", defaults.keyHeightScale.toDouble()).toFloat(),
                numberRowHeightScale = json.optDouble("number_row_height_scale", defaults.numberRowHeightScale.toDouble()).toFloat(),
                keyWidthScale = json.optDouble("key_width_scale", defaults.keyWidthScale.toDouble()).toFloat(),
                rowGapScale = json.optDouble("row_gap_scale", defaults.rowGapScale.toDouble()).toFloat(),
                distributeHorizontalSpacing = json.optBoolean("distribute_horizontal_spacing", defaults.distributeHorizontalSpacing),
                ortholinear = json.optBoolean("ortholinear", defaults.ortholinear),
                showLeds = json.optBoolean("show_leds", defaults.showLeds),
                suggestionsHeightScale = json.optDouble("suggestions_height_scale", defaults.suggestionsHeightScale.toDouble()).toFloat(),
                variationsHeightScale = json.optDouble("variations_height_scale", defaults.variationsHeightScale.toDouble()).toFloat(),
                keyPopupStyle = normalizeKeyboardThemePopupStyle(json.optString("key_popup_style", defaults.keyPopupStyle)),
                keyPopupAttached = json.optBoolean("key_popup_attached", defaults.keyPopupAttached),
                keyPopupTailEnabled = json.optBoolean("key_popup_tail_enabled", defaults.keyPopupTailEnabled),
                keyPreviewAfterLongPress = json.optBoolean("key_preview_after_long_press", defaults.keyPreviewAfterLongPress),
                keyAlternatesPopupEnabled = json.optBoolean("key_alternates_popup_enabled", defaults.keyAlternatesPopupEnabled)
            )
        } catch (error: Exception) {
            Log.e(TAG, "Error loading keyboard theme", error)
            defaults
        }
    }

    fun getKeyboardThemeAssignmentMode(context: Context, target: KeyboardThemeTarget): String {
        val stored = getPreferences(context).getString(
            keyboardThemeAssignmentModeKeyForTarget(target),
            KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED
        )
        return if (stored == KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
            KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        } else {
            KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED
        }
    }

    fun setKeyboardThemeAssignmentMode(context: Context, target: KeyboardThemeTarget, mode: String) {
        val normalized = if (mode == KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
            KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        } else {
            KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED
        }
        getPreferences(context).edit()
            .putString(keyboardThemeAssignmentModeKeyForTarget(target), normalized)
            .apply()
    }

    fun getKeyboardThemeSystemSlot(
        context: Context,
        target: KeyboardThemeTarget,
        dark: Boolean
    ): KeyboardThemeSettings {
        val defaults = defaultSystemKeyboardTheme(target, dark)
        val key = if (dark) keyboardThemeDarkKeyForTarget(target) else keyboardThemeLightKeyForTarget(target)
        val stored = getPreferences(context).getString(key, null) ?: return defaults
        return try {
            keyboardThemeFromJson(JSONObject(stored), defaults)
        } catch (error: Exception) {
            Log.e(TAG, "Error loading system keyboard theme", error)
            defaults
        }
    }

    fun setKeyboardThemeSystemSlot(
        context: Context,
        target: KeyboardThemeTarget,
        dark: Boolean,
        theme: KeyboardThemeSettings
    ) {
        val key = if (dark) keyboardThemeDarkKeyForTarget(target) else keyboardThemeLightKeyForTarget(target)
        getPreferences(context).edit()
            .putString(key, keyboardThemeToJson(theme).toString())
            .apply()
    }

    fun isSystemDarkTheme(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun getEffectiveKeyboardTheme(context: Context, target: KeyboardThemeTarget): KeyboardThemeSettings {
        return if (getKeyboardThemeAssignmentMode(context, target) == KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
            getKeyboardThemeSystemSlot(context, target, dark = isSystemDarkTheme(context))
        } else {
            getKeyboardTheme(context, target)
        }
    }

    /**
     * 1.0.1 and earlier could give a theme override to a language or layout. Numen has one input
     * style (en-US, qwerty), so the override that matched it becomes the fixed theme, keeping what
     * the user sees, and the rest are dropped. Does nothing once the old keys are gone.
     */
    fun migrateKeyboardThemeLayoutOverrides(context: Context) {
        val prefs = getPreferences(context)
        KeyboardThemeTarget.entries.forEach { target ->
            val legacyKey = legacyKeyboardThemeLayoutOverridesKeyForTarget(target)
            if (!prefs.contains(legacyKey)) return@forEach
            val theme = prefs.getString(legacyKey, null)
                ?.let { legacyThemeOverrideForInputStyle(it, defaultKeyboardTheme(target)) }
            prefs.edit().apply {
                if (theme != null) {
                    putString(keyboardThemeKeyForTarget(target), keyboardThemeToJson(theme).toString())
                    putString(keyboardThemeAssignmentModeKeyForTarget(target), KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED)
                }
                remove(legacyKey)
            }.apply()
        }
    }

    /** The old override that applied to en-US / qwerty: exact locale beats language, both beat layout only. */
    private fun legacyThemeOverrideForInputStyle(
        stored: String,
        defaults: KeyboardThemeSettings
    ): KeyboardThemeSettings? = try {
        val overrides = JSONArray(stored)
        var best: JSONObject? = null
        var bestScore = 0
        for (index in 0 until overrides.length()) {
            val item = overrides.optJSONObject(index) ?: continue
            val themeObject = item.optJSONObject("theme") ?: continue
            val locale = item.optString("locale", "").trim().replace('_', '-')
            val layout = item.optString("layout", "").trim()
            val localeScore = when {
                locale.isEmpty() -> 0
                locale.equals("en-US", ignoreCase = true) -> 16
                locale.substringBefore('-').equals("en", ignoreCase = true) -> 8
                else -> continue
            }
            val layoutScore = when {
                layout.isEmpty() -> 0
                layout.equals(DEFAULT_KEYBOARD_LAYOUT, ignoreCase = true) -> 4
                else -> continue
            }
            if (localeScore + layoutScore > bestScore) {
                bestScore = localeScore + layoutScore
                best = themeObject
            }
        }
        best?.let { keyboardThemeFromJson(it, defaults) }
    } catch (error: Exception) {
        Log.e(TAG, "Error migrating keyboard theme overrides", error)
        null
    }

    fun setKeyboardTheme(
        context: Context,
        target: KeyboardThemeTarget,
        theme: KeyboardThemeSettings
    ) {
        val json = keyboardThemeToJson(theme)
        getPreferences(context).edit()
            .putString(keyboardThemeKeyForTarget(target), json.toString())
            .apply()
    }

    fun keyboardThemeToJsonString(theme: KeyboardThemeSettings): String =
        keyboardThemeToJson(theme).toString()

    fun keyboardThemeFromJsonString(value: String): KeyboardThemeSettings? {
        return try {
            val json = JSONObject(value)
            if (!hasSupportedKeyboardThemeSchema(json)) return null
            keyboardThemeFromJson(json, defaultKeyboardTheme())
        } catch (error: Exception) {
            Log.e(TAG, "Error importing keyboard theme", error)
            null
        }
    }

    private fun hasSupportedKeyboardThemeSchema(json: JSONObject): Boolean {
        val requiredIntegerKeys = listOf(
            "background",
            "divider",
            "normal_key",
            "special_key",
            "text_and_icons",
            "led_inactive",
            "led_active",
            "led_locked",
            "accent",
            "cursor_swipe",
            "key_popup",
            "key_popup_selected",
            "suggestion",
            "status_bar_button"
        )
        val requiredFloatKeys = listOf(
            "key_corner_radius_ratio",
            "chrome_corner_radius_ratio",
            "key_height_scale",
            "key_width_scale",
            "row_gap_scale"
        )
        val optionalFloatKeys = listOf(
            "number_row_height_scale",
            "suggestions_height_scale",
            "variations_height_scale"
        )
        val requiredBooleanKeys = listOf(
            "distribute_horizontal_spacing",
            "ortholinear",
            "show_leds"
        )
        val optionalBooleanKeys = listOf(
            "key_popup_attached",
            "key_popup_tail_enabled",
            "key_preview_after_long_press",
            "key_alternates_popup_enabled"
        )

        if (!requiredIntegerKeys.all { key -> json.opt(key).isJsonInt() }) return false
        if (!requiredFloatKeys.all { key -> json.opt(key).isFiniteJsonNumber() }) return false
        if (!requiredBooleanKeys.all { key -> json.opt(key) is Boolean }) return false
        if (!optionalFloatKeys.all { key -> !json.has(key) || json.opt(key).isFiniteJsonNumber() }) return false
        if (!optionalBooleanKeys.all { key -> !json.has(key) || json.opt(key) is Boolean }) return false
        return !json.has("key_popup_style") || json.opt("key_popup_style") in setOf(
                KEYBOARD_THEME_POPUP_STYLE_FLOATING,
                KEYBOARD_THEME_POPUP_STYLE_CLASSIC
            )
    }

    private fun Any?.isJsonInt(): Boolean {
        val number = this as? Number ?: return false
        val doubleValue = number.toDouble()
        return doubleValue.isFinite() &&
            doubleValue % 1.0 == 0.0 &&
            doubleValue >= Int.MIN_VALUE.toDouble() &&
            doubleValue <= Int.MAX_VALUE.toDouble()
    }

    private fun Any?.isFiniteJsonNumber(): Boolean =
        (this as? Number)?.toDouble()?.isFinite() == true

    fun getSavedKeyboardThemes(context: Context): List<NamedKeyboardTheme> {
        val stored = getPreferences(context).getString(KEY_KEYBOARD_THEME_SAVED_THEMES, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val themeObject = item.optJSONObject("theme") ?: continue
                    if (name.isNotEmpty()) {
                        add(NamedKeyboardTheme(name, keyboardThemeFromJson(themeObject, defaultKeyboardTheme())))
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error loading saved keyboard themes", error)
            emptyList()
        }
    }

    fun saveKeyboardTheme(
        context: Context,
        name: String,
        theme: KeyboardThemeSettings
    ) {
        val normalizedName = name.trim().ifEmpty { "Custom" }
        val themes = getSavedKeyboardThemes(context)
            .filterNot { it.name.equals(normalizedName, ignoreCase = true) } +
            NamedKeyboardTheme(normalizedName, theme)
        persistSavedKeyboardThemes(context, themes)
    }

    fun deleteKeyboardTheme(context: Context, name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return

        val themes = getSavedKeyboardThemes(context)
            .filterNot { it.name.equals(normalizedName, ignoreCase = true) }
        persistSavedKeyboardThemes(context, themes)
    }

    fun getKeyboardThemeDrafts(context: Context): List<KeyboardThemeDraft> {
        val stored = getPreferences(context).getString(KEY_KEYBOARD_THEME_DRAFTS, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val themeObject = item.optJSONObject("theme") ?: continue
                    val populatedArray = item.optJSONArray("populated_fields")
                    val populatedFields = buildSet {
                        if (populatedArray != null) {
                            for (fieldIndex in 0 until populatedArray.length()) {
                                populatedArray.optString(fieldIndex).takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }
                    if (name.isNotEmpty()) {
                        add(
                            KeyboardThemeDraft(
                                name = name,
                                theme = keyboardThemeFromJson(themeObject, defaultKeyboardTheme()),
                                populatedFields = populatedFields
                            )
                        )
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error loading saved keyboard theme drafts", error)
            emptyList()
        }
    }

    fun saveKeyboardThemeDraft(context: Context, draft: KeyboardThemeDraft) {
        val normalizedName = draft.name.trim().ifEmpty { "Untitled theme" }
        val drafts = getKeyboardThemeDrafts(context)
            .filterNot { it.name.equals(normalizedName, ignoreCase = true) } +
            draft.copy(name = normalizedName)
        persistKeyboardThemeDrafts(context, drafts)
    }

    fun deleteKeyboardThemeDraft(context: Context, name: String) {
        val drafts = getKeyboardThemeDrafts(context)
            .filterNot { it.name.equals(name.trim(), ignoreCase = true) }
        persistKeyboardThemeDrafts(context, drafts)
    }

    private fun persistKeyboardThemeDrafts(context: Context, drafts: List<KeyboardThemeDraft>) {
        val array = JSONArray().apply {
            drafts.forEach { draft ->
                put(JSONObject().apply {
                    put("name", draft.name)
                    put("theme", keyboardThemeToJson(draft.theme))
                    put("populated_fields", JSONArray(draft.populatedFields.toList()))
                })
            }
        }
        getPreferences(context).edit()
            .putString(KEY_KEYBOARD_THEME_DRAFTS, array.toString())
            .apply()
    }

    private fun persistSavedKeyboardThemes(
        context: Context,
        themes: List<NamedKeyboardTheme>
    ) {
        val array = JSONArray().apply {
            themes.forEach { savedTheme ->
                put(JSONObject().apply {
                    put("name", savedTheme.name)
                    put("theme", keyboardThemeToJson(savedTheme.theme))
                })
            }
        }
        getPreferences(context).edit()
            .putString(KEY_KEYBOARD_THEME_SAVED_THEMES, array.toString())
            .apply()
    }

    private fun keyboardThemeFromJson(
        json: JSONObject,
        defaults: KeyboardThemeSettings
    ): KeyboardThemeSettings =
        KeyboardThemeSettings(
            background = json.optInt("background", defaults.background),
            divider = json.optInt("divider", defaults.divider),
            normalKey = json.optInt("normal_key", defaults.normalKey),
            specialKey = json.optInt("special_key", defaults.specialKey),
            textAndIcons = json.optInt("text_and_icons", defaults.textAndIcons),
            ledInactive = json.optInt("led_inactive", defaults.ledInactive),
            ledActive = json.optInt("led_active", defaults.ledActive),
            ledLocked = json.optInt("led_locked", defaults.ledLocked),
            accent = json.optInt("accent", defaults.accent),
            cursorSwipe = json.optInt("cursor_swipe", defaults.cursorSwipe),
            keyPopup = json.optInt("key_popup", defaults.keyPopup),
            keyPopupSelected = json.optInt("key_popup_selected", defaults.keyPopupSelected),
            suggestion = json.optInt("suggestion", defaults.suggestion),
            statusBarButton = json.optInt("status_bar_button", defaults.statusBarButton),
            keyCornerRadiusRatio = json.optDouble("key_corner_radius_ratio", defaults.keyCornerRadiusRatio.toDouble()).toFloat(),
            chromeCornerRadiusRatio = json.optDouble("chrome_corner_radius_ratio", defaults.chromeCornerRadiusRatio.toDouble()).toFloat(),
            keyHeightScale = json.optDouble("key_height_scale", defaults.keyHeightScale.toDouble()).toFloat(),
            numberRowHeightScale = json.optDouble("number_row_height_scale", defaults.numberRowHeightScale.toDouble()).toFloat(),
            keyWidthScale = json.optDouble("key_width_scale", defaults.keyWidthScale.toDouble()).toFloat(),
            rowGapScale = json.optDouble("row_gap_scale", defaults.rowGapScale.toDouble()).toFloat(),
            distributeHorizontalSpacing = json.optBoolean("distribute_horizontal_spacing", defaults.distributeHorizontalSpacing),
            ortholinear = json.optBoolean("ortholinear", defaults.ortholinear),
            showLeds = json.optBoolean("show_leds", defaults.showLeds),
            suggestionsHeightScale = json.optDouble("suggestions_height_scale", defaults.suggestionsHeightScale.toDouble()).toFloat(),
            variationsHeightScale = json.optDouble("variations_height_scale", defaults.variationsHeightScale.toDouble()).toFloat(),
            keyPopupStyle = normalizeKeyboardThemePopupStyle(json.optString("key_popup_style", defaults.keyPopupStyle)),
            keyPopupAttached = json.optBoolean("key_popup_attached", defaults.keyPopupAttached),
            keyPopupTailEnabled = json.optBoolean("key_popup_tail_enabled", defaults.keyPopupTailEnabled),
            keyPreviewAfterLongPress = json.optBoolean("key_preview_after_long_press", defaults.keyPreviewAfterLongPress),
            keyAlternatesPopupEnabled = json.optBoolean("key_alternates_popup_enabled", defaults.keyAlternatesPopupEnabled)
        )

    private fun keyboardThemeToJson(theme: KeyboardThemeSettings): JSONObject =
        JSONObject().apply {
            put("background", theme.background)
            put("divider", theme.divider)
            put("normal_key", theme.normalKey)
            put("special_key", theme.specialKey)
            put("text_and_icons", theme.textAndIcons)
            put("led_inactive", theme.ledInactive)
            put("led_active", theme.ledActive)
            put("led_locked", theme.ledLocked)
            put("accent", theme.accent)
            put("cursor_swipe", theme.cursorSwipe)
            put("key_popup", theme.keyPopup)
            put("key_popup_selected", theme.keyPopupSelected)
            put("suggestion", theme.suggestion)
            put("status_bar_button", theme.statusBarButton)
            put("key_corner_radius_ratio", theme.keyCornerRadiusRatio.toDouble())
            put("chrome_corner_radius_ratio", theme.chromeCornerRadiusRatio.toDouble())
            put("key_height_scale", theme.keyHeightScale.toDouble())
            put("number_row_height_scale", theme.numberRowHeightScale.toDouble())
            put("key_width_scale", theme.keyWidthScale.toDouble())
            put("row_gap_scale", theme.rowGapScale.toDouble())
            put("distribute_horizontal_spacing", theme.distributeHorizontalSpacing)
            put("ortholinear", theme.ortholinear)
            put("show_leds", theme.showLeds)
            put("suggestions_height_scale", theme.suggestionsHeightScale.toDouble())
            put("variations_height_scale", theme.variationsHeightScale.toDouble())
            put("key_popup_style", normalizeKeyboardThemePopupStyle(theme.keyPopupStyle))
            put("key_popup_attached", theme.keyPopupAttached)
            put("key_popup_tail_enabled", theme.keyPopupTailEnabled)
            put("key_preview_after_long_press", theme.keyPreviewAfterLongPress)
            put("key_alternates_popup_enabled", theme.keyAlternatesPopupEnabled)
        }

    private fun normalizeKeyboardThemePopupStyle(value: String): String =
        when (value) {
            KEYBOARD_THEME_POPUP_STYLE_CLASSIC -> KEYBOARD_THEME_POPUP_STYLE_CLASSIC
            else -> KEYBOARD_THEME_POPUP_STYLE_FLOATING
        }

    fun resolveEffectiveSoftwareKeyboardMode(context: Context): SoftwareKeyboardMode {
        getSoftwareKeyboardModeRuntimeOverride(context)?.let { return it }
        val configured = getSoftwareKeyboardMode(context)
        if (configured != SoftwareKeyboardMode.AUTO) {
            return configured
        }
        return it.palsoftware.pastiera.inputmethod.SoftwareKeyboardAutoDetector.resolve(context)
    }

    fun isTitan2LayoutEnabled(context: Context): Boolean {
        val prefs = getPreferences(context)
        if (prefs.contains(KEY_TITAN2_LAYOUT_ENABLED)) {
            return prefs.getBoolean(KEY_TITAN2_LAYOUT_ENABLED, false)
        }
        return DeviceSpecific.isTitan2Device()
    }

    fun setTitan2LayoutEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TITAN2_LAYOUT_ENABLED, enabled)
            .apply()
    }

    fun getTitan2EliteRoundedCornerInsetsEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(
            KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS,
            DeviceSpecific.isTitan2EliteDevice()
        )

    fun getTitan2EliteFillBottomCorners(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_TITAN2_ELITE_FILL_BOTTOM_CORNERS, true)

    fun setTitan2EliteFillBottomCorners(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TITAN2_ELITE_FILL_BOTTOM_CORNERS, enabled)
            .apply()
    }

    fun getTitan2EliteTopCornerMultiplier(context: Context): Int =
        getPreferences(context).getInt(KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER, 2).let {
            when (it) { 1, 4, 6 -> it; else -> 2 }
        }

    fun setTitan2EliteTopCornerMultiplier(context: Context, multiplier: Int) {
        getPreferences(context).edit()
            .putInt(KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER, when (multiplier) { 1, 4, 6 -> multiplier; else -> 2 })
            .apply()
    }

    fun getTitan2EliteMaxIconShrink(context: Context): Int =
        getPreferences(context).getInt(KEY_TITAN2_ELITE_MAX_ICON_SHRINK, 90).coerceIn(0, 90)

    fun setTitan2EliteMaxIconShrink(context: Context, percent: Int) {
        getPreferences(context).edit().putInt(KEY_TITAN2_ELITE_MAX_ICON_SHRINK, percent.coerceIn(0, 90)).apply()
    }

    fun setTitan2EliteRoundedCornerInsetsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS, enabled)
            .apply()
    }

    /**
     * Enables the calibrated rounded-corner layout once for Titan 2 Elite users receiving this
     * migration. Later user changes remain authoritative because the marker prevents reapplying it.
     */
    fun enforceTitan2EliteRoundedCornersOnce(context: Context) {
        val prefs = getPreferences(context)
        if (prefs.getBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNERS_ENFORCED_V1, false)) return

        prefs.edit().apply {
            if (DeviceSpecific.isTitan2EliteDevice()) {
                putBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS, true)
            }
            putBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNERS_ENFORCED_V1, true)
        }.apply()
    }

    
    /**
     * Returns the long-press threshold in milliseconds.
     */
    fun getLongPressThreshold(context: Context): Long {
        return getPreferences(context).getLong(KEY_LONG_PRESS_THRESHOLD, DEFAULT_LONG_PRESS_THRESHOLD)
    }
    
    /**
     * Sets the long-press threshold in milliseconds.
     * The value is automatically clamped between MIN and MAX.
     */
    fun setLongPressThreshold(context: Context, threshold: Long) {
        val clampedValue = threshold.coerceIn(MIN_LONG_PRESS_THRESHOLD, MAX_LONG_PRESS_THRESHOLD)
        getPreferences(context).edit()
            .putLong(KEY_LONG_PRESS_THRESHOLD, clampedValue)
            .apply()
    }

    fun getShiftTapLatches(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_SHIFT_TAP_LATCHES,
            DEFAULT_MODIFIER_TAP_LATCHES
        )
    }

    fun setShiftTapLatches(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SHIFT_TAP_LATCHES, enabled)
            .apply()
    }

    fun getAltTapLatches(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ALT_TAP_LATCHES,
            DEFAULT_MODIFIER_TAP_LATCHES
        )
    }

    fun setAltTapLatches(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_TAP_LATCHES, enabled)
            .apply()
    }

    fun getCtrlTapLatches(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_CTRL_TAP_LATCHES,
            DEFAULT_MODIFIER_TAP_LATCHES
        )
    }

    fun setCtrlTapLatches(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CTRL_TAP_LATCHES, enabled)
            .apply()
    }

    fun getAltLatchStaysOnSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ALT_LATCH_STAYS_ON_SPACE,
            // Numen: a double-tapped Alt is a number lock, so "555 1234" keeps it on, like Caps Lock.
            true
        )
    }

    fun setAltLatchStaysOnSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_LATCH_STAYS_ON_SPACE, enabled)
            .apply()
    }

    fun getCtrlLatchStaysOnSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_CTRL_LATCH_STAYS_ON_SPACE,
            DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE
        )
    }

    fun setCtrlLatchStaysOnSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CTRL_LATCH_STAYS_ON_SPACE, enabled)
            .apply()
    }
    
    /**
     * Returns the minimum allowed value for the long-press threshold.
     */
    fun getMinLongPressThreshold(): Long = MIN_LONG_PRESS_THRESHOLD
    
    /**
     * Returns the maximum allowed value for the long-press threshold.
     */
    fun getMaxLongPressThreshold(): Long = MAX_LONG_PRESS_THRESHOLD
    

    fun getTypingSoundMode(context: Context): String {
        val mode = getPreferences(context).getString(KEY_TYPING_SOUND_MODE, DEFAULT_TYPING_SOUND_MODE)
        return when (mode) {
            TYPING_SOUND_MODE_CLICK,
            TYPING_SOUND_MODE_TYPEWRITER,
            TYPING_SOUND_MODE_CUSTOM -> mode
            else -> TYPING_SOUND_MODE_OFF
        }
    }

    fun setTypingSoundMode(context: Context, mode: String) {
        val normalized = when (mode) {
            TYPING_SOUND_MODE_CLICK,
            TYPING_SOUND_MODE_TYPEWRITER,
            TYPING_SOUND_MODE_CUSTOM -> mode
            else -> TYPING_SOUND_MODE_OFF
        }
        getPreferences(context).edit()
            .putString(KEY_TYPING_SOUND_MODE, normalized)
            .apply()
    }

    fun getTypingSoundOutputMode(context: Context): String {
        val mode = getPreferences(context).getString(KEY_TYPING_SOUND_OUTPUT_MODE, DEFAULT_TYPING_SOUND_OUTPUT_MODE)
        return when (mode) {
            TYPING_SOUND_OUTPUT_SYSTEM,
            TYPING_SOUND_OUTPUT_NOTIFICATION -> mode
            else -> TYPING_SOUND_OUTPUT_MEDIA
        }
    }

    fun setTypingSoundOutputMode(context: Context, mode: String) {
        val normalized = when (mode) {
            TYPING_SOUND_OUTPUT_SYSTEM,
            TYPING_SOUND_OUTPUT_NOTIFICATION -> mode
            else -> TYPING_SOUND_OUTPUT_MEDIA
        }
        getPreferences(context).edit()
            .putString(KEY_TYPING_SOUND_OUTPUT_MODE, normalized)
            .apply()
    }

    fun getTapHapticUseSystem(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_TAP_HAPTIC_USE_SYSTEM, DEFAULT_TAP_HAPTIC_USE_SYSTEM)

    fun setTapHapticUseSystem(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TAP_HAPTIC_USE_SYSTEM, enabled)
            .apply()
    }

    fun getTapHapticDurationMs(context: Context): Long =
        getPreferences(context)
            .getLong(KEY_TAP_HAPTIC_DURATION_MS, DEFAULT_TAP_HAPTIC_DURATION_MS)
            .coerceIn(MIN_TAP_HAPTIC_DURATION_MS, MAX_TAP_HAPTIC_DURATION_MS)

    fun setTapHapticDurationMs(context: Context, durationMs: Long) {
        getPreferences(context).edit()
            .putLong(
                KEY_TAP_HAPTIC_DURATION_MS,
                durationMs.coerceIn(MIN_TAP_HAPTIC_DURATION_MS, MAX_TAP_HAPTIC_DURATION_MS)
            )
            .apply()
    }

    fun getMinTapHapticDurationMs(): Long = MIN_TAP_HAPTIC_DURATION_MS

    fun getMaxTapHapticDurationMs(): Long = MAX_TAP_HAPTIC_DURATION_MS

    fun getTypingSoundCustomDisplayName(context: Context): String? {
        return getPreferences(context)
            .getString(KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getTypingSoundCustomFile(context: Context): File? {
        val fileName = getPreferences(context)
            .getString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return File(File(context.filesDir, TYPING_SOUND_CUSTOM_DIR), fileName).takeIf { it.isFile }
    }

    fun getTypingSoundCustomGroupFiles(context: Context): Map<String, List<File>> {
        val storedName = getPreferences(context)
            .getString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        val root = File(File(context.filesDir, TYPING_SOUND_CUSTOM_DIR), storedName)
        if (!root.isDirectory) {
            getTypingSoundCustomFile(context)?.let { file ->
                return mapOf("normal" to listOf(file))
            }
            return emptyMap()
        }

        return TYPING_SOUND_GROUPS.associateWith { group ->
            File(root, group)
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.lowercase() in TYPING_SOUND_AUDIO_EXTENSIONS }
                .sortedBy { it.name }
        }.filterValues { it.isNotEmpty() }
    }

    fun importTypingSoundPack(context: Context, uri: Uri): Boolean {
        val displayName = queryDisplayName(context, uri) ?: return false
        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension != "zip") {
            return false
        }

        val targetDir = File(context.filesDir, TYPING_SOUND_CUSTOM_DIR).apply { mkdirs() }
        val stagingDir = File(targetDir, "${TYPING_SOUND_CUSTOM_PACK_DIR}_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val finalDir = File(targetDir, TYPING_SOUND_CUSTOM_PACK_DIR)

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                extractTypingSoundPack(input, stagingDir)
            } ?: return false

            if (File(stagingDir, "normal").listFiles().orEmpty().none { it.isFile }) {
                stagingDir.deleteRecursively()
                return false
            }

            finalDir.deleteRecursively()
            if (!stagingDir.renameTo(finalDir)) {
                stagingDir.copyRecursively(finalDir, overwrite = true)
                stagingDir.deleteRecursively()
            }

            getPreferences(context).edit()
                .putString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, finalDir.name)
                .putString(KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, displayName)
                .putString(KEY_TYPING_SOUND_MODE, TYPING_SOUND_MODE_CUSTOM)
                .putLong(KEY_TYPING_SOUND_UPDATED_AT, System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing typing sound", e)
            stagingDir.deleteRecursively()
            false
        }
    }

    fun importTypingSound(context: Context, uri: Uri): Boolean = importTypingSoundPack(context, uri)

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun extractTypingSoundPack(input: InputStream, targetDir: File) {
        var fileCount = 0
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            val canonicalTargetRoot = targetDir.canonicalFile

            while (entry != null) {
                val entryName = entry.name.removePrefix("./")
                if (!entry.isDirectory && !entryName.startsWith("__MACOSX/")) {
                    val group = resolveTypingSoundGroup(entryName)
                    val extension = entryName.substringAfterLast('.', "").lowercase()
                    if (group != null && extension in TYPING_SOUND_AUDIO_EXTENSIONS) {
                        fileCount += 1
                        if (fileCount > TYPING_SOUND_MAX_PACK_FILES) {
                            throw IllegalArgumentException("Typing sound pack has too many files")
                        }

                        val outFile = File(File(targetDir, group).apply { mkdirs() }, "${fileCount.toString().padStart(3, '0')}.$extension")
                        val canonicalOutFile = outFile.canonicalFile
                        if (!canonicalOutFile.path.startsWith(canonicalTargetRoot.path)) {
                            throw IllegalStateException("Refusing to unzip entry outside target dir: $entryName")
                        }

                        canonicalOutFile.outputStream().use { output ->
                            totalBytes += copyWithLimit(zip, output, TYPING_SOUND_MAX_FILE_BYTES, TYPING_SOUND_MAX_PACK_BYTES - totalBytes)
                        }
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun resolveTypingSoundGroup(entryName: String): String? {
        val parts = entryName.split('/').filter { it.isNotBlank() }
        return parts.firstOrNull { it.lowercase() in TYPING_SOUND_GROUPS }?.lowercase()
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long, remainingPackBytes: Long = Long.MAX_VALUE): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            copied += read
            if (copied > maxBytes || copied > remainingPackBytes) {
                throw IllegalArgumentException("Typing sound import exceeds size limits")
            }
            output.write(buffer, 0, read)
        }
        return copied
    }
    
    /**
     * Returns the swipe incremental threshold in DIP.
     * This is the distance that must be traveled to move the cursor one position.
     */
    fun getSwipeIncrementalThreshold(context: Context): Float {
        return getPreferences(context).getFloat(KEY_SWIPE_INCREMENTAL_THRESHOLD, DEFAULT_SWIPE_INCREMENTAL_THRESHOLD)
    }
    
    /**
     * Sets the swipe incremental threshold in DIP.
     * The value is automatically clamped between MIN and MAX.
     */
    fun setSwipeIncrementalThreshold(context: Context, threshold: Float) {
        val clampedValue = threshold.coerceIn(MIN_SWIPE_INCREMENTAL_THRESHOLD, MAX_SWIPE_INCREMENTAL_THRESHOLD)
        getPreferences(context).edit()
            .putFloat(KEY_SWIPE_INCREMENTAL_THRESHOLD, clampedValue)
            .apply()
    }
    
    /**
     * Returns the minimum allowed value for the swipe incremental threshold.
     */
    fun getMinSwipeIncrementalThreshold(): Float = MIN_SWIPE_INCREMENTAL_THRESHOLD
    
    /**
     * Returns the maximum allowed value for the swipe incremental threshold.
     */
    fun getMaxSwipeIncrementalThreshold(): Float = MAX_SWIPE_INCREMENTAL_THRESHOLD
    
    
    /**
     * Returns the state of auto-capitalization for the first letter.
     */
    fun getAutoCapitalizeFirstLetter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER)
    }
    
    /**
     * Sets the state of auto-capitalization for the first letter.
     */
    fun setAutoCapitalizeFirstLetter(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, enabled)
            .apply()
    }

    fun getAutoCapitalizeRespectManualShiftOff(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF,
            DEFAULT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF
        )
    }

    fun setAutoCapitalizeRespectManualShiftOff(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF, enabled)
            .apply()
    }

    fun getAutoCapitalizeRestrictedFields(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_AUTO_CAPITALIZE_RESTRICTED_FIELDS,
            DEFAULT_AUTO_CAPITALIZE_RESTRICTED_FIELDS
        )
    }

    fun setAutoCapitalizeRestrictedFields(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_RESTRICTED_FIELDS, enabled)
            .apply()
    }

    /**
     * Returns the state of auto-capitalization after period.
     */
    fun getAutoCapitalizeAfterPeriod(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_AFTER_PERIOD, DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD)
    }

    /**
     * Sets the state of auto-capitalization after period.
     */
    fun setAutoCapitalizeAfterPeriod(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_AFTER_PERIOD, enabled)
            .apply()
    }

    /**
     * Returns the state of the double-space-to-period feature.
     */
    fun getDoubleSpaceToPeriod(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_DOUBLE_SPACE_TO_PERIOD, DEFAULT_DOUBLE_SPACE_TO_PERIOD)
    }
    
    /**
     * Sets the state of the double-space-to-period feature.
     */
    fun setDoubleSpaceToPeriod(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_DOUBLE_SPACE_TO_PERIOD, enabled)
            .apply()
    }

    /**
     * Returns the state of spaced-hyphen-to-en-dash smart punctuation.
     */
    fun getSpacedHyphenToEnDash(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SPACED_HYPHEN_TO_EN_DASH, DEFAULT_SPACED_HYPHEN_TO_EN_DASH)
    }

    /**
     * Sets the state of spaced-hyphen-to-en-dash smart punctuation.
     */
    fun setSpacedHyphenToEnDash(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SPACED_HYPHEN_TO_EN_DASH, enabled)
            .apply()
    }

    fun getSpacedHyphenDashStyle(context: Context): String {
        val stored = getPreferences(context).getString(KEY_SPACED_HYPHEN_DASH_STYLE, DEFAULT_SPACED_HYPHEN_DASH_STYLE)
        return when (stored) {
            DASH_STYLE_EN,
            DASH_STYLE_EM -> stored
            else -> DEFAULT_SPACED_HYPHEN_DASH_STYLE
        }
    }

    fun setSpacedHyphenDashStyle(context: Context, style: String) {
        getPreferences(context).edit()
            .putString(
                KEY_SPACED_HYPHEN_DASH_STYLE,
                when (style) {
                    DASH_STYLE_EN,
                    DASH_STYLE_EM -> style
                    else -> DEFAULT_SPACED_HYPHEN_DASH_STYLE
                }
            )
            .apply()
    }

    fun getMidWordQuoteToApostrophe(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_MID_WORD_QUOTE_TO_APOSTROPHE,
            DEFAULT_MID_WORD_QUOTE_TO_APOSTROPHE
        )
    }

    fun setMidWordQuoteToApostrophe(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_MID_WORD_QUOTE_TO_APOSTROPHE, enabled)
            .apply()
    }

    fun getCommaSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_COMMA_SPACE,
            DEFAULT_COMMA_SPACE
        )
    }

    fun setCommaSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_COMMA_SPACE, enabled)
            .apply()
    }

    fun getAutoSpacePunctuation(context: Context): String {
        val stored = getPreferences(context).getString(
            KEY_AUTO_SPACE_PUNCTUATION,
            DEFAULT_AUTO_SPACE_PUNCTUATION
        ) ?: DEFAULT_AUTO_SPACE_PUNCTUATION
        return normalizeAutoSpacePunctuation(stored)
    }

    fun setAutoSpacePunctuation(context: Context, punctuation: String) {
        getPreferences(context).edit()
            .putString(KEY_AUTO_SPACE_PUNCTUATION, normalizeAutoSpacePunctuation(punctuation))
            .apply()
    }

    fun getSpaceAfterPunctuation(context: Context): String {
        val stored = getPreferences(context).getString(
            KEY_SPACE_AFTER_PUNCTUATION,
            DEFAULT_SPACE_AFTER_PUNCTUATION
        ) ?: DEFAULT_SPACE_AFTER_PUNCTUATION
        return normalizeAutoSpacePunctuation(stored)
    }

    fun setSpaceAfterPunctuation(context: Context, punctuation: String) {
        getPreferences(context).edit()
            .putString(KEY_SPACE_AFTER_PUNCTUATION, normalizeAutoSpacePunctuation(punctuation))
            .apply()
    }

    private fun normalizeAutoSpacePunctuation(punctuation: String): String =
        punctuation
            .filter { it in Punctuation.AUTO_SPACE_CANDIDATES }
            .toSet()
            .let { selected -> Punctuation.AUTO_SPACE_CANDIDATES.filter { it in selected } }

    fun getSmartQuotes(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SMART_QUOTES, DEFAULT_SMART_QUOTES)
    }

    fun setSmartQuotes(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SMART_QUOTES, enabled)
            .apply()
    }

    

    
    /**
     * Returns the state of automatically showing the keyboard when a field gains focus.
     */
    fun getAutoShowKeyboard(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_SHOW_KEYBOARD, DEFAULT_AUTO_SHOW_KEYBOARD)
    }
    
    /**
     * Sets the state of automatically showing the keyboard when a field gains focus.
     */
    fun setAutoShowKeyboard(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_SHOW_KEYBOARD, enabled)
            .apply()
    }

    /**
     * Returns whether Alt+Ctrl shortcut for speech recognition is enabled.
     */
    fun getAltCtrlSpeechShortcutEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, DEFAULT_ALT_CTRL_SPEECH_SHORTCUT)
    }

    /**
     * Sets whether Alt+Ctrl shortcut for speech recognition is enabled.
     */
    fun setAltCtrlSpeechShortcutEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, enabled)
            .apply()
    }

    /**
     * Returns whether accessibility live announcements are enabled.
     */
    fun getAccessibilityLiveAnnouncementsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED,
            DEFAULT_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED
        )
    }

    /**
     * Sets whether accessibility live announcements are enabled.
     */
    fun setAccessibilityLiveAnnouncementsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether accessibility should read the second IME row
     * (quick settings and variations).
     */
    fun getAccessibilityReadSecondRowEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED,
            DEFAULT_ACCESSIBILITY_READ_SECOND_ROW_ENABLED
        )
    }

    /**
     * Sets whether accessibility should read the second IME row
     * (quick settings and variations).
     */
    fun setAccessibilityReadSecondRowEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the delay before suggestion row accessibility is re-enabled after updates.
     */
    fun getAccessibilitySuggestionsAnnouncementDelayMs(context: Context): Long {
        return getPreferences(context).getLong(
            KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS,
            DEFAULT_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS
        )
    }

    /**
     * Sets the delay before suggestion row accessibility is re-enabled after updates.
     */
    fun setAccessibilitySuggestionsAnnouncementDelayMs(context: Context, delayMs: Long) {
        val clamped = delayMs.coerceIn(
            MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS,
            MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS
        )
        getPreferences(context).edit()
            .putLong(KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS, clamped)
            .apply()
    }

    fun getMinAccessibilitySuggestionsAnnouncementDelayMs(): Long =
        MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS

    fun getMaxAccessibilitySuggestionsAnnouncementDelayMs(): Long =
        MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS

    fun getBounceKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_ENABLED,
            DEFAULT_BOUNCE_KEYS_ENABLED
        )
    }

    fun setBounceKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysDelayMs(context: Context): Long {
        return getPreferences(context).getLong(
            KEY_BOUNCE_KEYS_DELAY_MS,
            DEFAULT_BOUNCE_KEYS_DELAY_MS
        ).coerceIn(MIN_BOUNCE_KEYS_DELAY_MS, MAX_BOUNCE_KEYS_DELAY_MS)
    }

    fun setBounceKeysDelayMs(context: Context, delayMs: Long) {
        getPreferences(context).edit()
            .putLong(
                KEY_BOUNCE_KEYS_DELAY_MS,
                delayMs.coerceIn(MIN_BOUNCE_KEYS_DELAY_MS, MAX_BOUNCE_KEYS_DELAY_MS)
            )
            .apply()
    }

    fun getMinBounceKeysDelayMs(): Long = MIN_BOUNCE_KEYS_DELAY_MS

    fun getMaxBounceKeysDelayMs(): Long = MAX_BOUNCE_KEYS_DELAY_MS

    fun getBounceKeysCharacterKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED,
            DEFAULT_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED
        )
    }

    fun setBounceKeysCharacterKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysModifierKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED,
            DEFAULT_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED
        )
    }

    fun setBounceKeysModifierKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysSpaceEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_SPACE_ENABLED,
            DEFAULT_BOUNCE_KEYS_SPACE_ENABLED
        )
    }

    fun setBounceKeysSpaceEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_SPACE_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysEnterEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_ENTER_ENABLED,
            DEFAULT_BOUNCE_KEYS_ENTER_ENABLED
        )
    }

    fun setBounceKeysEnterEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_ENTER_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysBackspaceEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_BACKSPACE_ENABLED,
            DEFAULT_BOUNCE_KEYS_BACKSPACE_ENABLED
        )
    }

    fun setBounceKeysBackspaceEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_BACKSPACE_ENABLED, enabled)
            .apply()
    }

    fun getOverlappingKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_OVERLAPPING_KEYS_ENABLED,
            DEFAULT_OVERLAPPING_KEYS_ENABLED
        )
    }

    fun setOverlappingKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_OVERLAPPING_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysCategoryEnabled(
        context: Context,
        category: it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category
    ): Boolean {
        return when (category) {
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.CHARACTER ->
                getBounceKeysCharacterKeysEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.MODIFIER ->
                getBounceKeysModifierKeysEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.SPACE ->
                getBounceKeysSpaceEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.ENTER ->
                getBounceKeysEnterEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.BACKSPACE ->
                getBounceKeysBackspaceEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.UNSUPPORTED -> false
        }
    }

    /**
     * Returns whether Shift+Backspace performs forward delete.
     */
    fun getShiftBackspaceDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SHIFT_BACKSPACE_DELETE, DEFAULT_SHIFT_BACKSPACE_DELETE)
    }

    /**
     * Sets whether Shift+Backspace performs forward delete.
     */
    fun setShiftBackspaceDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SHIFT_BACKSPACE_DELETE, enabled)
            .apply()
    }

    /**
     * Returns whether Alt+Backspace performs forward delete.
     */
    fun getAltBackspaceDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ALT_BACKSPACE_DELETE, DEFAULT_ALT_BACKSPACE_DELETE)
    }

    /**
     * Sets whether Alt+Backspace performs forward delete.
     */
    fun setAltBackspaceDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_BACKSPACE_DELETE, enabled)
            .apply()
    }

    /**
     * Returns whether Backspace at line start performs forward delete.
     */
    fun getBackspaceAtStartDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_BACKSPACE_AT_START_DELETE, DEFAULT_BACKSPACE_AT_START_DELETE)
    }

    /**
     * Sets whether Backspace at line start performs forward delete.
     */
    fun setBackspaceAtStartDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BACKSPACE_AT_START_DELETE, enabled)
            .apply()
    }

    /**
     * Returns whether the static variation bar mode is enabled.
     * When enabled, the variation row shows a fixed set of utility keys
     * instead of dynamic cursor-based character variations.
     */
    fun isStaticVariationBarModeEnabled(context: Context): Boolean {
        return getStaticVariationBarPreset(context) != STATIC_VARIATION_PRESET_OFF
    }

    fun getStaticVariationBarPreset(context: Context): String {
        val prefs = getPreferences(context)
        val stored = prefs.getString(KEY_STATIC_VARIATION_BAR_PRESET, null)
        val fallback = if (prefs.getBoolean(KEY_STATIC_VARIATION_BAR_MODE, DEFAULT_STATIC_VARIATION_BAR_MODE)) {
            if (prefs.getBoolean(
                    KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED,
                    DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED
                )
            ) {
                STATIC_VARIATION_PRESET_ALTERNATIVE
            } else {
                STATIC_VARIATION_PRESET_SYMBOLS
            }
        } else {
            STATIC_VARIATION_PRESET_OFF
        }
        return when (stored ?: fallback) {
            STATIC_VARIATION_PRESET_OFF,
            STATIC_VARIATION_PRESET_SYMBOLS,
            STATIC_VARIATION_PRESET_NUMBERS,
            STATIC_VARIATION_PRESET_ALTERNATIVE,
            STATIC_VARIATION_PRESET_DEV_CHOICE -> stored ?: fallback
            else -> fallback
        }
    }

    fun setStaticVariationBarPreset(context: Context, preset: String) {
        val normalized = when (preset) {
            STATIC_VARIATION_PRESET_OFF,
            STATIC_VARIATION_PRESET_SYMBOLS,
            STATIC_VARIATION_PRESET_NUMBERS,
            STATIC_VARIATION_PRESET_ALTERNATIVE,
            STATIC_VARIATION_PRESET_DEV_CHOICE -> preset
            else -> STATIC_VARIATION_PRESET_OFF
        }
        getPreferences(context).edit()
            .putString(KEY_STATIC_VARIATION_BAR_PRESET, normalized)
            .putBoolean(KEY_STATIC_VARIATION_BAR_MODE, normalized != STATIC_VARIATION_PRESET_OFF)
            .putBoolean(KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED, normalized == STATIC_VARIATION_PRESET_ALTERNATIVE)
            .apply()

        if (normalized != STATIC_VARIATION_PRESET_OFF) {
            saveStaticVariationRows(
                context = context,
                staticVariations = getStaticVariationBasePreset(normalized),
                staticVariationsShift = getStaticVariationShiftPreset(normalized),
                staticVariationsAlt = getStaticVariationAltPreset(normalized)
            )
        }
    }

    /**
     * Returns the top-row preset for the static variation bar based on toggle state.
     */
    fun getStaticVariationBasePreset(context: Context): List<String> {
        return getStaticVariationBasePreset(getStaticVariationBarPreset(context))
    }

    private fun getStaticVariationBasePreset(preset: String): List<String> {
        return when (preset) {
            STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
            STATIC_VARIATION_PRESET_ALTERNATIVE -> STATIC_VARIATION_BASE_PRESET_ALTERNATIVE
            STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
            else -> STATIC_VARIATION_BASE_PRESET_DEFAULT
        }
    }

    fun getDefaultStaticVariationShiftPreset(): List<String> = STATIC_VARIATION_SHIFT_PRESET_DEFAULT

    fun getDefaultStaticVariationAltPreset(): List<String> = STATIC_VARIATION_ALT_PRESET_DEFAULT

    private fun getStaticVariationShiftPreset(preset: String): List<String> {
        return when (preset) {
            STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
            STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
            else -> STATIC_VARIATION_SHIFT_PRESET_DEFAULT
        }
    }

    private fun getStaticVariationAltPreset(preset: String): List<String> {
        return when (preset) {
            STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
            STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
            else -> STATIC_VARIATION_ALT_PRESET_DEFAULT
        }
    }

    fun getStaticVariationNumbersPreset(): List<String> = STATIC_VARIATION_BASE_PRESET_NUMBERS

    fun getDevChoiceStaticVariationBasePreset(): List<String> = STATIC_VARIATION_BASE_PRESET_DEV_CHOICE

    /**
     * Returns true if the static variation layer should remain latched after modifier hold.
     */
    fun isStaticVariationBarLayerStickyEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION, true)
    }

    /**
     * Sets whether static variation layers should stay latched after modifier hold.
     */
    fun setStaticVariationBarLayerStickyEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION, enabled)
            .apply()
    }

    /**
     * Returns whether Alt/Alt-Lock should be cleared when pressing Space.
     */
    fun getClearAltOnSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CLEAR_ALT_ON_SPACE, DEFAULT_CLEAR_ALT_ON_SPACE)
    }

    /**
     * Sets whether Alt/Alt-Lock should be cleared when pressing Space.
     */
    fun setClearAltOnSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CLEAR_ALT_ON_SPACE, enabled)
            .apply()
    }
    
    /**
     * Returns custom SYM mappings.
     * Returns an empty map if there are no custom mappings.
     */
    fun getSymMappings(context: Context): Map<Int, String> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_MAPPINGS_CUSTOM, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            
            val result = mutableMapOf<Int, String>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val emoji = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    result[keyCode] = emoji
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom SYM mappings", e)
            emptyMap()
        }
    }
    
    /**
     * Saves custom SYM mappings.
     */
    fun saveSymMappings(context: Context, mappings: Map<Int, String>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, emoji) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    mappingsObject.put(keyName, emoji)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            getPreferences(context).edit()
                .putString(KEY_SYM_MAPPINGS_CUSTOM, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom SYM mappings", e)
        }
    }
    
    /**
     * Resets custom SYM mappings back to defaults.
     */
    fun resetSymMappings(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_SYM_MAPPINGS_CUSTOM)
            .apply()
    }
    
    
    /**
     * Returns custom SYM mappings for page 2.
     * Returns an empty map if there are no custom mappings.
     */
    fun getSymMappingsPage2(context: Context): Map<Int, String> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_MAPPINGS_PAGE2_CUSTOM, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            
            val result = mutableMapOf<Int, String>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val character = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    result[keyCode] = character
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom SYM page 2 mappings", e)
            emptyMap()
        }
    }
    
    /**
     * Saves custom SYM mappings for page 2.
     */
    fun saveSymMappingsPage2(context: Context, mappings: Map<Int, String>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, character) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    mappingsObject.put(keyName, character)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            getPreferences(context).edit()
                .putString(KEY_SYM_MAPPINGS_PAGE2_CUSTOM, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom SYM page 2 mappings", e)
        }
    }
    
    /**
     * Resets custom SYM mappings for page 2 back to defaults.
     */
    fun resetSymMappingsPage2(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_SYM_MAPPINGS_PAGE2_CUSTOM)
            .apply()
    }
    
    
    /**
     * Returns whether auto-correction is enabled.
     */
    fun getAutoCorrectEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CORRECT_ENABLED, DEFAULT_AUTO_CORRECT_ENABLED)
    }

    /**
     * Sets whether auto-correction is enabled.
     */
    fun setAutoCorrectEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CORRECT_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether inline suggestions are enabled.
     */
    fun getSuggestionsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SUGGESTIONS_ENABLED, DEFAULT_SUGGESTIONS_ENABLED)
    }

    /**
     * Enables or disables inline suggestions.
     */
    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SUGGESTIONS_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether accent matching should be applied to suggestions and auto-replace.
     */
    fun getAccentMatchingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ACCENT_MATCHING_ENABLED, DEFAULT_ACCENT_MATCHING_ENABLED)
    }

    /**
     * Toggles accent matching for suggestions.
     */
    fun setAccentMatchingEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ACCENT_MATCHING_ENABLED, enabled)
            .apply()
    }

    /**
     * Master toggle for the experimental dictionary/suggestion engine.
     * When disabled, the IME will skip initialization and hide suggestion UI.
     */
    fun isExperimentalSuggestionsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED, DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED)
    }

    fun setExperimentalSuggestionsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED, enabled)
            .apply()
    }

    /** Opt-in candidates lifecycle for the hardware keyboard; existing installs keep the input view. */
    fun getExperimentalCandidatesViewEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_EXPERIMENTAL_CANDIDATES_VIEW_ENABLED, false)
    }

    fun setExperimentalCandidatesViewEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_EXPERIMENTAL_CANDIDATES_VIEW_ENABLED, enabled)
            .apply()
    }

    /**
     * Optional debug logging for the suggestion engine.
     */
    fun isSuggestionDebugLoggingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SUGGESTION_DEBUG_LOGGING, DEFAULT_SUGGESTION_DEBUG_LOGGING)
    }

    /**
     * Optional debug logging for IME overlay / inset calculations.
     */
    fun isImeOverlayDebugLoggingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IME_OVERLAY_DEBUG_LOGGING, DEFAULT_IME_OVERLAY_DEBUG_LOGGING)
    }

    /**
     * Returns whether auto-replace on space/enter is enabled.
     */
    fun getAutoReplaceOnSpaceEnter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_REPLACE_ON_SPACE_ENTER, DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER)
    }

    /**
     * Enables or disables auto-replace on space/enter.
     */
    fun setAutoReplaceOnSpaceEnter(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_REPLACE_ON_SPACE_ENTER, enabled)
            .apply()
    }

    /**
     * Returns the maximum edit distance for auto-replace (0-3).
     * 0 = off (no auto-replace), 1-3 = maximum distance allowed.
     */
    fun getMaxAutoReplaceDistance(context: Context): Int {
        return getPreferences(context).getInt(KEY_MAX_AUTO_REPLACE_DISTANCE, DEFAULT_MAX_AUTO_REPLACE_DISTANCE)
            .coerceIn(0, 3)
    }

    /**
     * Sets the maximum edit distance for auto-replace (0-3).
     * 0 = off (no auto-replace), 1-3 = maximum distance allowed.
     */
    fun setMaxAutoReplaceDistance(context: Context, distance: Int) {
        getPreferences(context).edit()
            .putInt(KEY_MAX_AUTO_REPLACE_DISTANCE, distance.coerceIn(0, 3))
            .apply()
    }

    /**
     * Returns whether keyboard proximity ranking is enabled for suggestions.
     * When enabled, suggestions consider keyboard distance to filter out unlikely typos.
     */
    fun getUseKeyboardProximity(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_USE_KEYBOARD_PROXIMITY, DEFAULT_USE_KEYBOARD_PROXIMITY)
    }

    /**
     * Returns whether edit type ranking is enabled for suggestions.
     * When enabled, suggestions are ranked by edit type (insert > substitute > delete).
     */
    fun getUseEditTypeRanking(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_USE_EDIT_TYPE_RANKING, DEFAULT_USE_EDIT_TYPE_RANKING)
    }

    /**
     * Returns whether auto-replace runs in aggressive mode: missing or extra letters, two edits
     * on longer words, missed spaces, and learning a word kept twice after undo.
     */
    fun getAggressiveAutoReplace(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AGGRESSIVE_AUTO_REPLACE, DEFAULT_AGGRESSIVE_AUTO_REPLACE)
    }

    fun setAggressiveAutoReplace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AGGRESSIVE_AUTO_REPLACE, enabled)
            .apply()
    }

    /** Returns whether a real but wrong word is fixed from the surrounding sentence. */
    fun getContextAwareCorrection(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CONTEXT_AWARE_CORRECTION, DEFAULT_CONTEXT_AWARE_CORRECTION)
    }

    fun setContextAwareCorrection(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CONTEXT_AWARE_CORRECTION, enabled)
            .apply()
    }

    /** Stored JSON of the user's own text replacements (the "_en" suffix predates Numen being English only). */
    private const val KEY_CUSTOM_AUTO_CORRECTIONS = "auto_correct_custom_en"

    /** Legacy JSON field that held a language display name; skipped when reading. */
    private const val LEGACY_LANGUAGE_NAME_KEY = "__name"

    /**
     * Returns the user's own text replacements.
     */
    fun getCustomAutoCorrections(context: Context): Map<String, String> {
        val jsonString = getPreferences(context).getString(KEY_CUSTOM_AUTO_CORRECTIONS, null) ?: return emptyMap()

        return try {
            val jsonObject = JSONObject(jsonString)
            val corrections = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val correctionKey = keys.next()
                if (correctionKey != LEGACY_LANGUAGE_NAME_KEY) {
                    corrections[correctionKey] = jsonObject.getString(correctionKey)
                }
            }
            corrections
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom text replacements", e)
            emptyMap()
        }
    }

    /**
     * Saves the user's own text replacements.
     */
    fun saveCustomAutoCorrections(context: Context, corrections: Map<String, String>) {
        try {
            val jsonObject = JSONObject()
            corrections.forEach { (key, value) ->
                if (key != LEGACY_LANGUAGE_NAME_KEY) {
                    jsonObject.put(key, value)
                }
            }
            getPreferences(context).edit()
                .putString(KEY_CUSTOM_AUTO_CORRECTIONS, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom text replacements", e)
        }
    }

    fun getSnippetsEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_SNIPPETS_ENABLED, DEFAULT_SNIPPETS_ENABLED)

    fun setSnippetsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_SNIPPETS_ENABLED, enabled).apply()
    }

    fun getSnippetsPrefix(context: Context): String {
        val stored = getPreferences(context).getString(KEY_SNIPPETS_PREFIX, DEFAULT_SNIPPETS_PREFIX)
        return stored?.takeIf(TextExpansionEngine::isValidSnippetPrefix) ?: DEFAULT_SNIPPETS_PREFIX
    }

    fun setSnippetsPrefix(context: Context, prefix: String): Boolean {
        if (!TextExpansionEngine.isValidSnippetPrefix(prefix)) return false
        getPreferences(context).edit().putString(KEY_SNIPPETS_PREFIX, prefix).apply()
        return true
    }

    fun getSnippets(context: Context): LinkedHashMap<String, String> {
        val json = getPreferences(context).getString(KEY_SNIPPETS, null) ?: return linkedMapOf()
        return runCatching {
            val objectValue = JSONObject(json)
            linkedMapOf<String, String>().apply {
                objectValue.keys().forEach { key ->
                    if (TextExpansionEngine.isValidSnippetShortcut(key)) {
                        put(key.lowercase(java.util.Locale.ROOT), objectValue.getString(key))
                    }
                }
            }
        }.getOrElse {
            Log.e(TAG, "Error loading snippets", it)
            linkedMapOf()
        }
    }

    fun saveSnippets(context: Context, snippets: Map<String, String>) {
        val json = JSONObject()
        snippets.forEach { (shortcut, replacement) ->
            val normalized = shortcut.trim().lowercase(java.util.Locale.ROOT)
            if (TextExpansionEngine.isValidSnippetShortcut(normalized) && !replacement.isBlank()) {
                json.put(normalized, replacement)
            }
        }
        getPreferences(context).edit().putString(KEY_SNIPPETS, json.toString()).apply()
    }

    fun getSnippetsPresentation(context: Context): ExpansionPresentation = ExpansionPresentation.fromStorage(
        getPreferences(context).getString(KEY_SNIPPETS_PRESENTATION, null)
    )

    fun setSnippetsPresentation(context: Context, presentation: ExpansionPresentation) {
        getPreferences(context).edit().putString(KEY_SNIPPETS_PRESENTATION, presentation.storageValue).apply()
    }

    fun getSnippetsActivationPolicy(context: Context): ExpansionActivationPolicy {
        val prefs = getPreferences(context)
        return ExpansionActivationPolicy(
            exactOnSpace = prefs.getBoolean(KEY_SNIPPETS_EXACT_ON_SPACE, true),
            acceptPrefixWithSpace = prefs.getBoolean(KEY_SNIPPETS_ACCEPT_PREFIX_WITH_SPACE, false),
            acceptWithTab = prefs.getBoolean(KEY_SNIPPETS_ACCEPT_WITH_TAB, true),
            acceptWithEnter = prefs.getBoolean(KEY_SNIPPETS_ACCEPT_WITH_ENTER, false)
        )
    }

    fun setSnippetsActivationPolicy(context: Context, policy: ExpansionActivationPolicy) {
        getPreferences(context).edit()
            .putBoolean(KEY_SNIPPETS_EXACT_ON_SPACE, policy.exactOnSpace)
            .putBoolean(KEY_SNIPPETS_ACCEPT_PREFIX_WITH_SPACE, policy.acceptPrefixWithSpace)
            .putBoolean(KEY_SNIPPETS_ACCEPT_WITH_TAB, policy.acceptWithTab)
            .putBoolean(KEY_SNIPPETS_ACCEPT_WITH_ENTER, policy.acceptWithEnter)
            .apply()
    }

    fun getEmojiShortcodesEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_EMOJI_SHORTCODES_ENABLED, false)

    fun setEmojiShortcodesEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_EMOJI_SHORTCODES_ENABLED, enabled).apply()
    }

    fun getSymbolShortcodesEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_SYMBOL_SHORTCODES_ENABLED, false)

    fun setSymbolShortcodesEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_SYMBOL_SHORTCODES_ENABLED, enabled).apply()
    }

    fun getEmojiSymbolsPresentation(context: Context): ExpansionPresentation = ExpansionPresentation.fromStorage(
        getPreferences(context).getString(KEY_EMOJI_SYMBOLS_PRESENTATION, null)
    )

    fun setEmojiSymbolsPresentation(context: Context, presentation: ExpansionPresentation) {
        getPreferences(context).edit()
            .putString(KEY_EMOJI_SYMBOLS_PRESENTATION, presentation.storageValue)
            .apply()
    }

    fun getEmojiSymbolsActivationPolicy(context: Context): ExpansionActivationPolicy {
        val prefs = getPreferences(context)
        return ExpansionActivationPolicy(
            exactOnSpace = prefs.getBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_SPACE, false),
            acceptPrefixWithSpace = prefs.getBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_PREFIX_WITH_SPACE, false),
            acceptWithTab = prefs.getBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_TAB, true),
            acceptWithEnter = prefs.getBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_ENTER, false)
        )
    }

    fun setEmojiSymbolsActivationPolicy(context: Context, policy: ExpansionActivationPolicy) {
        getPreferences(context).edit()
            .putBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_SPACE, policy.exactOnSpace)
            .putBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_PREFIX_WITH_SPACE, policy.acceptPrefixWithSpace)
            .putBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_TAB, policy.acceptWithTab)
            .putBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_ENTER, policy.acceptWithEnter)
            .apply()
    }

    fun getEmojiSymbolsExactOnClose(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_CLOSE, true)

    fun setEmojiSymbolsExactOnClose(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_CLOSE, enabled).apply()
    }
    
    /**
     * Returns the long-press action, including optional fixed SYM key layers.
     */
    fun getLongPressModifier(context: Context): String {
        val stored = getPreferences(context).getString(KEY_LONG_PRESS_MODIFIER, DEFAULT_LONG_PRESS_MODIFIER)
            ?: DEFAULT_LONG_PRESS_MODIFIER
        return when (stored) {
            "alt", "shift", "variations", "sym", "sym_symbols", "sym_emoji" -> stored
            else -> DEFAULT_LONG_PRESS_MODIFIER
        }
    }
    
    /**
     * Sets the long-press action.
     */
    fun setLongPressModifier(context: Context, modifier: String) {
        val validModifier = when (modifier) {
            "shift" -> "shift"
            "variations" -> "variations"
            "sym" -> "sym"
            "sym_symbols" -> "sym_symbols"
            "sym_emoji" -> "sym_emoji"
            else -> "alt"
        }
        getPreferences(context).edit()
            .putString(KEY_LONG_PRESS_MODIFIER, validModifier)
            .apply()
    }
    

    /** Returns 1 for Emoji and 2 for Symbols. */
    fun resolveLongPressSymPage(context: Context): Int = when (getLongPressModifier(context)) {
        "sym_emoji" -> 1
        "sym_symbols" -> 2
        else -> if (getSymPagesConfig(context).prefersEmojiLongPressLayer()) 1 else 2
    }
    
    /**
     * A launcher shortcut.
     * Extensible to support other action types in the future (app, shortcut, etc.)
     */
    data class LauncherShortcut(
        val type: String = TYPE_APP, // Action type: "app", "shortcut", etc.
        val packageName: String? = null, // For type "app"
        val appName: String? = null, // For type "app"
        val action: String? = null, // For type "shortcut" or future types
        val data: String? = null, // Extra data for future types
        val commandId: String? = null,
        val commandSource: String? = null,
        val commandKind: String? = null,
        val commandTitle: String? = null,
        val commandSubtitle: String? = null,
        val commandLaunch: CommandLaunchSpec? = null
    ) {
        companion object {
            const val TYPE_APP = "app"
            const val TYPE_SHORTCUT = "shortcut"
            const val TYPE_QUICK_LAUNCHER = "quick_launcher"
            const val TYPE_COMMAND = "command"
            // Add future types here
        }
    }
    
    private const val KEY_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
    private const val KEY_LAUNCHER_SHORTCUTS_ENABLED = "launcher_shortcuts_enabled"
    private const val KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED = "quick_launcher_default_assigned"
    private const val KEY_QUICK_LAUNCHER_AUTO_START_SINGLE = "quick_launcher_auto_start_single"
    private const val KEY_QUICK_LAUNCHER_LIMIT_RESULTS = "quick_launcher_limit_results"
    private const val KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS = "quick_launcher_text_field_shortcuts"
    private const val KEY_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS = "quick_launcher_alt_space_in_text_fields"
    private const val KEY_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS = "quick_launcher_alt_shortcuts_outside_text_fields"
    private const val KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT = "quick_launcher_respect_keyboard_layout"
    private const val KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING = "quick_launcher_typo_tolerant_ranking"
    private const val KEY_QUICK_LAUNCHER_WIDTH_PERCENT = "quick_launcher_width_percent"
    private const val KEY_QUICK_LAUNCHER_PILL_MODE = "quick_launcher_pill_mode"
    private const val KEY_QUICK_LAUNCHER_BEHAVIOR = "quick_launcher_behavior"
    private const val KEY_QUICK_LAUNCHER_ANIMATION_DURATION_MS = "quick_launcher_animation_duration_ms"
    private const val KEY_COMMAND_SURFACE_SOURCES = "command_surface_sources"
    private const val KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS = "quick_launcher_command_customizations"
    private const val KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES = "quick_launcher_highlight_favorites"
    private const val KEY_QUICK_LAUNCHER_FAVORITE_COLOR = "quick_launcher_favorite_color"
    private const val KEY_QUICK_LAUNCHER_ICON_COLORS = "quick_launcher_icon_colors"
    private const val KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST = "quick_launcher_show_alias_first"
    private const val KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT = "quick_launcher_static_top_highlight"
    private const val KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = "quick_launcher_static_top_highlight_color"
    private const val DEFAULT_LAUNCHER_SHORTCUTS_ENABLED = false
    private const val DEFAULT_QUICK_LAUNCHER_AUTO_START_SINGLE = false
    private const val DEFAULT_QUICK_LAUNCHER_LIMIT_RESULTS = false
    private const val DEFAULT_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS = true
    private const val DEFAULT_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS = false
    private const val DEFAULT_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS = false
    private const val DEFAULT_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT = true
    private const val DEFAULT_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING = true
    private const val DEFAULT_QUICK_LAUNCHER_WIDTH_PERCENT = 100
    private const val DEFAULT_QUICK_LAUNCHER_PILL_MODE = false
    private const val DEFAULT_QUICK_LAUNCHER_ANIMATION_DURATION_MS = 120
    const val QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR = Int.MIN_VALUE
    private const val DEFAULT_QUICK_LAUNCHER_FAVORITE_COLOR = QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR
    private const val DEFAULT_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = 0x7A4285F4
    const val QUICK_LAUNCHER_BEHAVIOR_PASTIERA = "pastiera"
    const val QUICK_LAUNCHER_BEHAVIOR_NIAGARA = "niagara"
    const val QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS = 0
    const val QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS = 320
    
    // Nav mode settings
    private const val KEY_NAV_MODE_ENABLED = "nav_mode_enabled"
    private const val DEFAULT_NAV_MODE_ENABLED = true
    private const val KEY_NAV_MODE_CTRL_HOLD_ENABLED = "nav_mode_ctrl_hold_enabled"
    private const val DEFAULT_NAV_MODE_CTRL_HOLD_ENABLED = false
    private const val KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION = "nav_mode_default_mappings_version"
    private const val CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION = 3
    private const val NAV_MODE_MAPPINGS_FILE_NAME = "ctrl_key_mappings.json"
    private const val KEY_NAV_MODE_MAPPINGS_UPDATED = "nav_mode_mappings_updated"
    
    /**
     * Sets a launcher shortcut for a key (app type).
     */
    fun setLauncherShortcut(context: Context, keyCode: Int, packageName: String, appName: String) {
        setLauncherCommand(
            context = context,
            keyCode = keyCode,
            commandId = "app:$packageName",
            source = CommandSourceId.Apps.storageValue,
            kind = "App",
            title = appName,
            subtitle = packageName,
            launch = CommandLaunchSpec.AppPackage(packageName)
        )
    }

    fun setQuickLauncherShortcut(context: Context, keyCode: Int) {
        setLauncherCommand(
            context = context,
            keyCode = keyCode,
            commandId = PastieraCommandSource.COMMAND_QUICK_LAUNCHER,
            source = CommandSourceId.Pastiera.storageValue,
            kind = "PastieraAction",
            title = Branding.launcherName,
            subtitle = Branding.launcherSubtitle,
            launch = CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
        )
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
            .apply()
    }

    fun setLauncherCommand(
        context: Context,
        keyCode: Int,
        commandId: String,
        source: String,
        kind: String,
        title: String,
        subtitle: String?,
        launch: CommandLaunchSpec
    ) {
        setLauncherAction(
            context,
            keyCode,
            LauncherShortcut(
                type = LauncherShortcut.TYPE_COMMAND,
                packageName = (launch as? CommandLaunchSpec.AppPackage)?.packageName,
                appName = title,
                commandId = commandId,
                commandSource = source,
                commandKind = kind,
                commandTitle = title,
                commandSubtitle = subtitle,
                commandLaunch = launch
            )
        )
    }
    
    /**
     * Sets a launcher action for a key (generic, extensible).
     */
    fun setLauncherAction(context: Context, keyCode: Int, action: LauncherShortcut) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            if (action.isQuickLauncherCommand()) {
                val keys = shortcuts.keys()
                val keysToRemove = mutableListOf<String>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val shortcutObj = shortcuts.optJSONObject(key)
                    if (
                        shortcutObj?.optString("type") == LauncherShortcut.TYPE_QUICK_LAUNCHER ||
                        shortcutObj?.optString("commandId") == PastieraCommandSource.COMMAND_QUICK_LAUNCHER
                    ) {
                        keysToRemove.add(key)
                    }
                }
                keysToRemove.forEach { shortcuts.remove(it) }
            }
            shortcuts.put(keyCode.toString(), JSONObject().apply {
                put("type", action.type)
                if (action.packageName != null) put("packageName", action.packageName)
                if (action.appName != null) put("appName", action.appName)
                if (action.action != null) put("action", action.action)
                if (action.data != null) put("data", action.data)
                if (action.commandId != null) put("commandId", action.commandId)
                if (action.commandSource != null) put("source", action.commandSource)
                if (action.commandKind != null) put("kind", action.commandKind)
                if (action.commandTitle != null) put("title", action.commandTitle)
                if (action.commandSubtitle != null) put("subtitle", action.commandSubtitle)
                if (action.commandLaunch != null) put("launch", CommandJson.launchToJson(action.commandLaunch))
            })
            prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving action for key $keyCode", e)
        }
    }
    
    /**
     * Removes the launcher shortcut for a key.
     */
    fun removeLauncherShortcut(context: Context, keyCode: Int) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            shortcuts.remove(keyCode.toString())
            prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing shortcut for key $keyCode", e)
        }
    }
    
    /**
     * Swaps launcher shortcuts between two keys (atomic operation).
     * If one key has no shortcut, the other key's shortcut is moved.
     */
    fun swapLauncherShortcuts(context: Context, fromKeyCode: Int, toKeyCode: Int) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            
            // Get current shortcuts (if any)
            val fromShortcutObj = shortcuts.optJSONObject(fromKeyCode.toString())
            val toShortcutObj = shortcuts.optJSONObject(toKeyCode.toString())
            
            // Swap: remove both first
            shortcuts.remove(fromKeyCode.toString())
            shortcuts.remove(toKeyCode.toString())
            
            // Add swapped shortcuts
            if (fromShortcutObj != null) {
                shortcuts.put(toKeyCode.toString(), fromShortcutObj)
            }
            if (toShortcutObj != null) {
                shortcuts.put(fromKeyCode.toString(), toShortcutObj)
            }
            
            // Save atomically
            prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error swapping shortcuts between keys $fromKeyCode and $toKeyCode", e)
        }
    }
    
    /**
     * Returns all saved launcher shortcuts.
     */
    fun getLauncherShortcuts(context: Context): Map<Int, LauncherShortcut> {
        ensureQuickLauncherDefaultShortcut(context)
        return getLauncherShortcutsRaw(context)
    }

    private fun getLauncherShortcutsRaw(context: Context): Map<Int, LauncherShortcut> {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        val shortcuts = mutableMapOf<Int, LauncherShortcut>()
        
        try {
            val json = JSONObject(shortcutsJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val keyCode = key.toIntOrNull()
                if (keyCode != null) {
                    val shortcutObj = json.getJSONObject(key)
                    val type = shortcutObj.optString("type", LauncherShortcut.TYPE_APP)
                    
                    shortcuts[keyCode] = LauncherShortcut(
                        type = type,
                        packageName = shortcutObj.optString("packageName").takeIf { it.isNotEmpty() },
                        appName = shortcutObj.optString("appName").takeIf { it.isNotEmpty() },
                        action = shortcutObj.optString("action").takeIf { it.isNotEmpty() },
                        data = shortcutObj.optString("data").takeIf { it.isNotEmpty() },
                        commandId = shortcutObj.optString("commandId").takeIf { it.isNotEmpty() },
                        commandSource = shortcutObj.optString("source").takeIf { it.isNotEmpty() },
                        commandKind = shortcutObj.optString("kind").takeIf { it.isNotEmpty() },
                        // The built-in launcher always shows its current name, not the saved one.
                        commandTitle = if (shortcutObj.optString("commandId") == PastieraCommandSource.COMMAND_QUICK_LAUNCHER) {
                            Branding.launcherName
                        } else {
                            shortcutObj.optString("title").takeIf { it.isNotEmpty() }
                        },
                        commandSubtitle = if (shortcutObj.optString("commandId") == PastieraCommandSource.COMMAND_QUICK_LAUNCHER) {
                            Branding.launcherSubtitle
                        } else {
                            shortcutObj.optString("subtitle").takeIf { it.isNotEmpty() }
                        },
                        commandLaunch = CommandJson.launchFromJson(shortcutObj.optJSONObject("launch"))
                            ?: legacyLaunchSpec(type, shortcutObj)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading shortcuts", e)
        }
        
        return shortcuts
    }
    
    /**
     * Returns the launcher shortcut for a specific key.
     */
    fun getLauncherShortcut(context: Context, keyCode: Int): LauncherShortcut? {
        return getLauncherShortcuts(context)[keyCode]
    }

    fun ensureQuickLauncherDefaultShortcut(context: Context) {
        val prefs = getPreferences(context)
        if (prefs.getBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, false)) {
            return
        }
        val shortcuts = getLauncherShortcutsRaw(context)
        if (shortcuts.values.any { it.isQuickLauncherCommand() }) {
            prefs.edit()
                .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
                .apply()
            return
        }
        if (shortcuts[KeyEvent.KEYCODE_SPACE] != null) {
            return
        }
        setQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
        prefs.edit()
            .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
            .apply()
    }

    fun isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context: Context): Boolean {
        val prefs = getPreferences(context)
        if (prefs.getBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, false)) {
            return false
        }
        val shortcuts = getLauncherShortcutsRaw(context)
        if (shortcuts.values.any { it.isQuickLauncherCommand() }) {
            return false
        }
        val spaceShortcut = shortcuts[KeyEvent.KEYCODE_SPACE]
        return spaceShortcut != null && !spaceShortcut.isQuickLauncherCommand()
    }

    fun getQuickLauncherShortcutKey(context: Context): Int? {
        return getLauncherShortcuts(context)
            .entries
            .firstOrNull { it.value.isQuickLauncherCommand() }
            ?.key
    }

    fun isQuickLauncherShortcut(context: Context, keyCode: Int): Boolean {
        return getLauncherShortcut(context, keyCode)?.isQuickLauncherCommand() == true
    }

    private fun LauncherShortcut.isQuickLauncherCommand(): Boolean {
        return type == LauncherShortcut.TYPE_QUICK_LAUNCHER ||
            commandId == PastieraCommandSource.COMMAND_QUICK_LAUNCHER ||
            commandLaunch == CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
    }

    private fun legacyLaunchSpec(type: String, shortcutObj: JSONObject): CommandLaunchSpec? {
        return when (type) {
            LauncherShortcut.TYPE_APP -> shortcutObj.optString("packageName")
                .takeIf { it.isNotBlank() }
                ?.let { CommandLaunchSpec.AppPackage(it) }
            LauncherShortcut.TYPE_QUICK_LAUNCHER -> {
                CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
            }
            else -> null
        }
    }

    data class CommandSourceVisibility(
        val sourceId: String,
        val quickLauncherEnabled: Boolean
    )

    data class QuickLauncherCommandCustomization(
        val commandId: String,
        val favorite: Boolean = false,
        val hidden: Boolean = false,
        val customSearch: String = "",
        val favoriteOrder: Int = Int.MAX_VALUE,
        val color: Int? = null
    )

    fun getCommandSourceVisibility(context: Context): List<CommandSourceVisibility> {
        val defaults = defaultCommandSourceVisibility()
        val stored = getPreferences(context).getString(KEY_COMMAND_SURFACE_SOURCES, null) ?: return defaults
        return try {
            val json = JSONObject(stored)
            defaults.map { default ->
                val sourceJson = json.optJSONObject(default.sourceId)
                if (sourceJson == null) {
                    default
                } else {
                    CommandSourceVisibility(
                        sourceId = default.sourceId,
                        quickLauncherEnabled = sourceJson.optBoolean("quick_launcher", default.quickLauncherEnabled)
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error loading command source visibility", error)
            defaults
        }
    }

    fun setCommandSourceVisibility(context: Context, visibility: List<CommandSourceVisibility>) {
        val json = JSONObject()
        visibility.forEach { item ->
            json.put(item.sourceId, JSONObject().apply {
                put("quick_launcher", item.quickLauncherEnabled)
            })
        }
        getPreferences(context).edit()
            .putString(KEY_COMMAND_SURFACE_SOURCES, json.toString())
            .apply()
    }

    fun isCommandSourceEnabled(context: Context, sourceId: String, surface: CommandSurface): Boolean {
        if (surface != CommandSurface.QuickLauncher) return true
        val visibility = getCommandSourceVisibility(context).firstOrNull { it.sourceId == sourceId }
            ?: defaultCommandSourceVisibility().firstOrNull { it.sourceId == sourceId }
            ?: return false
        return visibility.quickLauncherEnabled
    }

    private fun defaultCommandSourceVisibility(): List<CommandSourceVisibility> {
        return listOf(
            CommandSourceVisibility(CommandSourceId.Apps.storageValue, quickLauncherEnabled = true),
            CommandSourceVisibility(CommandSourceId.Pastiera.storageValue, quickLauncherEnabled = true),
            CommandSourceVisibility(CommandSourceId.AppActions.storageValue, quickLauncherEnabled = false),
            CommandSourceVisibility(CommandSourceId.DeviceControl.storageValue, quickLauncherEnabled = false),
            CommandSourceVisibility(CommandSourceId.NavActions.storageValue, quickLauncherEnabled = false)
        )
    }

    fun getQuickLauncherCommandCustomizations(context: Context): Map<String, QuickLauncherCommandCustomization> {
        val stored = getPreferences(context).getString(KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS, null)
            ?: return emptyMap()
        return try {
            val json = JSONObject(stored)
            val result = mutableMapOf<String, QuickLauncherCommandCustomization>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val commandId = keys.next()
                val item = json.optJSONObject(commandId) ?: continue
                result[commandId] = QuickLauncherCommandCustomization(
                    commandId = commandId,
                    favorite = item.optBoolean("favorite", false),
                    hidden = item.optBoolean("hidden", false),
                    customSearch = item.optString("custom_search", ""),
                    favoriteOrder = item.optInt("favorite_order", Int.MAX_VALUE),
                    color = if (item.has("color")) item.optInt("color") else null
                )
            }
            result
        } catch (error: Exception) {
            Log.e(TAG, "Error loading quick launcher command customizations", error)
            emptyMap()
        }
    }

    fun setQuickLauncherCommandCustomization(
        context: Context,
        customization: QuickLauncherCommandCustomization
    ) {
        val current = getQuickLauncherCommandCustomizations(context).toMutableMap()
        if (
            !customization.favorite &&
            !customization.hidden &&
            customization.customSearch.isBlank() &&
            customization.favoriteOrder == Int.MAX_VALUE &&
            customization.color == null
        ) {
            current.remove(customization.commandId)
        } else {
            current[customization.commandId] = customization.copy(customSearch = customization.customSearch.trim())
        }
        val json = JSONObject()
        current.values.sortedBy { it.commandId }.forEach { item ->
            json.put(item.commandId, JSONObject().apply {
                if (item.favorite) put("favorite", true)
                if (item.hidden) put("hidden", true)
                if (item.customSearch.isNotBlank()) put("custom_search", item.customSearch)
                if (item.favoriteOrder != Int.MAX_VALUE) put("favorite_order", item.favoriteOrder)
                item.color?.let { put("color", it) }
            })
        }
        getPreferences(context).edit()
            .putString(KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS, json.toString())
            .apply()
    }

    fun getQuickLauncherHighlightFavorites(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES, true)
    }

    fun setQuickLauncherHighlightFavorites(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES, enabled)
            .apply()
    }

    fun getQuickLauncherFavoriteColor(context: Context): Int {
        return getPreferences(context).getInt(KEY_QUICK_LAUNCHER_FAVORITE_COLOR, DEFAULT_QUICK_LAUNCHER_FAVORITE_COLOR)
    }

    fun setQuickLauncherFavoriteColor(context: Context, color: Int) {
        getPreferences(context).edit()
            .putInt(KEY_QUICK_LAUNCHER_FAVORITE_COLOR, color)
            .apply()
    }

    fun getQuickLauncherIconColors(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_ICON_COLORS, false)
    }

    fun setQuickLauncherIconColors(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_ICON_COLORS, enabled)
            .apply()
    }

    fun getQuickLauncherShowAliasFirst(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST, true)
    }

    fun setQuickLauncherShowAliasFirst(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST, enabled)
            .apply()
    }

    fun getQuickLauncherStaticTopHighlight(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, false)
    }

    fun setQuickLauncherStaticTopHighlight(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, enabled)
            .apply()
    }

    fun getQuickLauncherStaticTopHighlightColor(context: Context): Int {
        return getPreferences(context).getInt(
            KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR,
            DEFAULT_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR
        )
    }

    fun setQuickLauncherStaticTopHighlightColor(context: Context, color: Int) {
        getPreferences(context).edit()
            .putInt(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR, color)
            .apply()
    }
    
    /**
     * Returns whether launcher shortcuts are enabled.
     */
    fun getLauncherShortcutsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_LAUNCHER_SHORTCUTS_ENABLED, DEFAULT_LAUNCHER_SHORTCUTS_ENABLED)
    }
    
    /**
     * Sets whether launcher shortcuts are enabled.
     */
    fun setLauncherShortcutsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_LAUNCHER_SHORTCUTS_ENABLED, enabled)
            .apply()
    }

    fun getQuickLauncherAutoStartSingle(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_AUTO_START_SINGLE,
            DEFAULT_QUICK_LAUNCHER_AUTO_START_SINGLE
        )
    }

    fun setQuickLauncherAutoStartSingle(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_AUTO_START_SINGLE, enabled)
            .apply()
    }

    fun getQuickLauncherLimitResults(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_LIMIT_RESULTS,
            DEFAULT_QUICK_LAUNCHER_LIMIT_RESULTS
        )
    }

    fun setQuickLauncherLimitResults(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_LIMIT_RESULTS, enabled)
            .apply()
    }

    fun getQuickLauncherTextFieldShortcuts(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS,
            DEFAULT_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS
        )
    }

    fun setQuickLauncherTextFieldShortcuts(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS, enabled)
            .apply()
    }

    fun getQuickLauncherAltSpaceInTextFields(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS,
            DEFAULT_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS
        )
    }

    fun setQuickLauncherAltSpaceInTextFields(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS, enabled)
            .apply()
    }

    fun getQuickLauncherAltShortcutsOutsideTextFields(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS,
            DEFAULT_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS
        )
    }

    fun setQuickLauncherAltShortcutsOutsideTextFields(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS, enabled)
            .apply()
    }

    fun getQuickLauncherRespectKeyboardLayout(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT,
            DEFAULT_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT
        )
    }

    fun setQuickLauncherRespectKeyboardLayout(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT, enabled)
            .apply()
    }

    fun getQuickLauncherTypoTolerantRanking(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING,
            DEFAULT_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING
        )
    }

    fun setQuickLauncherTypoTolerantRanking(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING, enabled)
            .apply()
    }

    fun getQuickLauncherWidthPercent(context: Context): Int {
        return getPreferences(context)
            .getInt(KEY_QUICK_LAUNCHER_WIDTH_PERCENT, DEFAULT_QUICK_LAUNCHER_WIDTH_PERCENT)
            .coerceIn(50, 100)
    }

    fun setQuickLauncherWidthPercent(context: Context, percent: Int) {
        getPreferences(context).edit()
            .putInt(KEY_QUICK_LAUNCHER_WIDTH_PERCENT, percent.coerceIn(50, 100))
            .apply()
    }

    fun getQuickLauncherPillMode(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_PILL_MODE,
            DEFAULT_QUICK_LAUNCHER_PILL_MODE
        )
    }

    fun setQuickLauncherPillMode(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_PILL_MODE, enabled)
            .apply()
    }

    fun getQuickLauncherAnimationDurationMs(context: Context): Int {
        return getPreferences(context)
            .getInt(KEY_QUICK_LAUNCHER_ANIMATION_DURATION_MS, DEFAULT_QUICK_LAUNCHER_ANIMATION_DURATION_MS)
            .coerceIn(QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS, QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS)
    }

    fun setQuickLauncherAnimationDurationMs(context: Context, durationMs: Int) {
        getPreferences(context).edit()
            .putInt(
                KEY_QUICK_LAUNCHER_ANIMATION_DURATION_MS,
                durationMs.coerceIn(
                    QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS,
                    QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS
                )
            )
            .apply()
    }

    fun getQuickLauncherBehavior(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_QUICK_LAUNCHER_BEHAVIOR,
            QUICK_LAUNCHER_BEHAVIOR_PASTIERA
        ) ?: QUICK_LAUNCHER_BEHAVIOR_PASTIERA
        return normalizeQuickLauncherBehavior(value)
    }

    fun setQuickLauncherBehavior(context: Context, behavior: String) {
        getPreferences(context).edit()
            .putString(KEY_QUICK_LAUNCHER_BEHAVIOR, normalizeQuickLauncherBehavior(behavior))
            .apply()
    }

    private fun normalizeQuickLauncherBehavior(behavior: String): String {
        return when (behavior.trim().lowercase()) {
            QUICK_LAUNCHER_BEHAVIOR_NIAGARA -> QUICK_LAUNCHER_BEHAVIOR_NIAGARA
            else -> QUICK_LAUNCHER_BEHAVIOR_PASTIERA
        }
    }
    
    // Power Shortcuts settings
    private const val KEY_POWER_SHORTCUTS_ENABLED = "power_shortcuts_enabled"
    private const val DEFAULT_POWER_SHORTCUTS_ENABLED = true
    
    /**
     * Returns whether Power Shortcuts are enabled.
     */
    fun getPowerShortcutsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_POWER_SHORTCUTS_ENABLED, DEFAULT_POWER_SHORTCUTS_ENABLED)
    }
    
    /**
     * Sets whether Power Shortcuts are enabled.
     */
    fun setPowerShortcutsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_POWER_SHORTCUTS_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Returns whether nav mode is enabled.
     */
    fun getNavModeEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_NAV_MODE_ENABLED, DEFAULT_NAV_MODE_ENABLED)
    }
    
    /**
     * Sets whether nav mode is enabled.
     */
    fun setNavModeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_NAV_MODE_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether holding Ctrl should use Nav Mode mappings in text fields.
     */
    fun getNavModeCtrlHoldEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_NAV_MODE_CTRL_HOLD_ENABLED, DEFAULT_NAV_MODE_CTRL_HOLD_ENABLED)
    }

    /**
     * Sets whether holding Ctrl should use Nav Mode mappings in text fields.
     */
    fun setNavModeCtrlHoldEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_NAV_MODE_CTRL_HOLD_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Returns the File for nav mode mappings in filesDir.
     */
    fun getNavModeMappingsFile(context: Context): File {
        return File(context.filesDir, NAV_MODE_MAPPINGS_FILE_NAME)
    }
    
    /**
     * Initializes the nav mode mappings file by copying from assets if it doesn't exist.
     */
    fun initializeNavModeMappingsFile(context: Context) {
        val mappingsFile = getNavModeMappingsFile(context)
        if (mappingsFile.exists()) {
            migrateNavModeMappingsFileIfNeeded(context)
            return // File already exists, don't overwrite
        }
        
        try {
            val inputStream: InputStream = context.assets.open("common/ctrl/$NAV_MODE_MAPPINGS_FILE_NAME")
            val outputStream = FileOutputStream(mappingsFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            migrateNavModeMappingsFileIfNeeded(context)
            Log.d(TAG, "Nav mode mappings file initialized from assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing nav mode mappings file", e)
        }
    }

    private fun migrateNavModeMappingsFileIfNeeded(context: Context) {
        val prefs = getPreferences(context)
        if (prefs.getInt(KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION, 1) >= CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION) {
            return
        }

        try {
            val mappingsFile = getNavModeMappingsFile(context)
            if (!mappingsFile.exists()) {
                return
            }

            val jsonObject = JSONObject(mappingsFile.readText())
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val defaultWordMappings = mapOf(
                "KEYCODE_N" to "move_word_left",
                "KEYCODE_M" to "move_word_right",
                "KEYCODE_U" to "expand_selection_word_left",
                "KEYCODE_I" to "expand_selection_word_right"
            )

            defaultWordMappings.forEach { (keyName, action) ->
                val existing = mappingsObject.optJSONObject(keyName)
                if (existing == null || existing.optString("type") == "none") {
                    mappingsObject.put(
                        keyName,
                        JSONObject().apply {
                            put("type", "action")
                            put("action", action)
                        }
                    )
                }
            }
            val ctrlBExisting = mappingsObject.optJSONObject("KEYCODE_B")
            if (ctrlBExisting == null || ctrlBExisting.optString("type") == "none") {
                mappingsObject.put(
                    "KEYCODE_B",
                    JSONObject().apply {
                        put("type", "command")
                        put("command", "pastiera.toggle_software_keyboard_mode")
                    }
                )
            }

            mappingsFile.writeText(jsonObject.toString())
            prefs.edit()
                .putInt(KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION, CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION)
                .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Nav mode mappings migrated to version $CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating nav mode mappings file", e)
        }
    }
    
    /**
     * Saves nav mode key mappings to the JSON file in filesDir.
     */
    fun saveNavModeKeyMappings(context: Context, mappings: Map<Int, it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, mapping) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    val mappingObject = JSONObject()
                    mappingObject.put("type", mapping.type)
                    when (mapping.type) {
                        "action" -> mappingObject.put("action", mapping.value)
                        "keycode" -> mappingObject.put("keycode", mapping.value)
                        "command" -> mappingObject.put("command", mapping.value)
                        "native_ctrl" -> { /* type is already set */ }
                        "none" -> { /* type is already set */ }
                    }
                    mappingsObject.put(keyName, mappingObject)
                }
            }
            
            // Also include all alphabetic keys that might not be in the mappings map
            // but should be saved as "none" if they're not explicitly set
            val allAlphabeticKeys = listOf(
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
                KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
                KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
            )
            
            // Ensure all alphabetic keys are in the JSON (even if "none")
            allAlphabeticKeys.forEach { keyCode ->
                val keyName = keyCodeToName[keyCode]
                if (keyName != null && !mappingsObject.has(keyName)) {
                    val mappingObject = JSONObject()
                    mappingObject.put("type", "none")
                    mappingsObject.put(keyName, mappingObject)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            val mappingsFile = getNavModeMappingsFile(context)
            mappingsFile.writeText(jsonObject.toString())
            
            // Update timestamp in SharedPreferences to notify the service
            getPreferences(context).edit()
                .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
            
            Log.d(TAG, "Nav mode key mappings saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving nav mode key mappings", e)
        }
    }
    
    /**
     * Resets nav mode key mappings to default by deleting the custom file.
     */
    fun resetNavModeKeyMappings(context: Context) {
        try {
            val mappingsFile = getNavModeMappingsFile(context)
            if (mappingsFile.exists()) {
                mappingsFile.delete()
                Log.d(TAG, "Nav mode key mappings reset to default")
            }
            // Re-initialize from assets
            initializeNavModeMappingsFile(context)
            
            // Update timestamp in SharedPreferences to notify the service
            getPreferences(context).edit()
                .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting nav mode key mappings", e)
        }
    }
    
    
    /** Numen is English only: the physical layout is always qwerty, whatever an older version saved. */
    @Suppress("UNUSED_PARAMETER")
    fun getKeyboardLayout(context: Context): String = DEFAULT_KEYBOARD_LAYOUT

    /**
     * Returns the manual physical keyboard profile override used for device-specific mappings.
     * Supported values: auto, titan, titan2, titan2elite_qwerty.
     */
    fun getPhysicalKeyboardProfileOverride(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE,
            DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
        ) ?: DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
        return normalizePhysicalKeyboardProfileOverride(value)
    }

    /**
     * Sets the manual physical keyboard profile override used for device-specific mappings.
     * Invalid values are normalized to "auto".
     */
    fun setPhysicalKeyboardProfileOverride(context: Context, profile: String) {
        val normalized = normalizePhysicalKeyboardProfileOverride(profile)
        getPreferences(context).edit()
            .putString(KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE, normalized)
            .apply()
    }

    /**
     * Returns the symbol used for dedicated hardware currency keys.
     */
    fun getPhysicalKeyboardCurrencySymbol(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL,
            DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
        ) ?: DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
        return normalizePhysicalKeyboardCurrencySymbol(value)
    }

    /**
     * Sets the symbol used for dedicated hardware currency keys.
     */
    fun setPhysicalKeyboardCurrencySymbol(context: Context, symbol: String) {
        getPreferences(context).edit()
            .putString(KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL, normalizePhysicalKeyboardCurrencySymbol(symbol))
            .apply()
    }

    private fun normalizePhysicalKeyboardProfileOverride(profile: String?): String {
        val normalized = profile?.trim().orEmpty()
        return when {
            normalized.equals("auto", ignoreCase = true) -> "auto"
            normalized.equals("titan", ignoreCase = true) -> "titan"
            normalized.equals("titan2", ignoreCase = true) -> "titan2"
            normalized.equals("titan2elite_qwerty", ignoreCase = true) -> "titan2elite_qwerty"
            else -> DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
        }
    }

    private fun normalizePhysicalKeyboardCurrencySymbol(symbol: String?): String {
        val normalized = symbol?.trim().orEmpty()
        return if (normalized in physicalKeyboardCurrencySymbols()) {
            normalized
        } else {
            DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
        }
    }

    fun physicalKeyboardCurrencySymbols(): List<String> = listOf("€", "$", "£", "¥", "₹", "₽", "₿", "¤")

    
    /**
     * Sets the SYM page to restore when returning from settings.
     * @param context The context
     * @param page The SYM page to restore (0=disabled, 1=page1 emoji, 2=page2 characters)
     */
    fun setRestoreSymPage(context: Context, page: Int) {
        getPreferences(context).edit()
            .putInt(KEY_RESTORE_SYM_PAGE, page)
            .apply()
    }
    
    /**
     * Gets the SYM page to restore when returning from settings.
     * @param context The context
     * @return The SYM page to restore (0=disabled, 1=page1 emoji, 2=page2 characters), or 0 if not set
     */
    fun getRestoreSymPage(context: Context): Int {
        return getPreferences(context).getInt(KEY_RESTORE_SYM_PAGE, 0)
    }
    
    /**
     * Clears the SYM page restore state.
     * @param context The context
     */
    fun clearRestoreSymPage(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_RESTORE_SYM_PAGE)
            .apply()
    }
    
    /**
     * Sets a pending SYM page state when opening SymCustomizationActivity.
     * This will be converted to restore_sym_page only if user presses back.
     * @param context The context
     * @param page The SYM page that was active (0=disabled, 1=page1 emoji, 2=page2 characters)
     */
    fun setPendingRestoreSymPage(context: Context, page: Int) {
        getPreferences(context).edit()
            .putInt(KEY_PENDING_RESTORE_SYM_PAGE, page)
            .apply()
    }
    
    /**
     * Gets the pending SYM page state.
     * @param context The context
     * @return The pending SYM page, or 0 if not set
     */
    fun getPendingRestoreSymPage(context: Context): Int {
        return getPreferences(context).getInt(KEY_PENDING_RESTORE_SYM_PAGE, 0)
    }
    
    /**
     * Clears the pending SYM page state.
     * @param context The context
     */
    fun clearPendingRestoreSymPage(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_PENDING_RESTORE_SYM_PAGE)
            .apply()
    }
    
    /**
     * Confirms the pending restore by moving it to restore_sym_page.
     * Called when user presses back from SymCustomizationActivity.
     * @param context The context
     */
    fun confirmPendingRestoreSymPage(context: Context) {
        val pendingPage = getPendingRestoreSymPage(context)
        if (pendingPage > 0) {
            setRestoreSymPage(context, pendingPage)
            clearPendingRestoreSymPage(context)
        }
    }

    /**
     * Reads the SYM pages configuration (enabled pages and order).
     */
    fun getSymPagesConfig(context: Context): SymPagesConfig {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_PAGES_CONFIG, null) ?: return DEFAULT_SYM_PAGES_CONFIG

        return try {
            val jsonObject = JSONObject(jsonString)
            val schemaVersion = jsonObject.optInt("schemaVersion", 1)
            val deviceEnabled = jsonObject.optBoolean("deviceEnabled", false)
            val emojiEnabled = jsonObject.optBoolean("emojiEnabled", true)
            val symbolsEnabled = jsonObject.optBoolean("symbolsEnabled", true)
            val clipboardEnabled = jsonObject.optBoolean("clipboardEnabled", false)
            val emojiPickerEnabled = jsonObject.optBoolean("emojiPickerEnabled", false)
            val legacyEmojiFirst = jsonObject.optBoolean("emojiFirst", true)

            val parsedOrder = if (jsonObject.has("symPageOrder")) {
                val orderArray = jsonObject.optJSONArray("symPageOrder")
                val collected = mutableListOf<String>()
                if (orderArray != null) {
                    for (i in 0 until orderArray.length()) {
                        val pageId = orderArray.optString(i, "").trim()
                        if (pageId.isNotEmpty()) {
                            collected.add(pageId)
                        }
                    }
                }
                collected
            } else {
                // Legacy migration from emojiFirst behavior.
                val cyclePages = mutableListOf(
                    SymPagesConfig.PAGE_EMOJI,
                    SymPagesConfig.PAGE_SYMBOLS,
                    SymPagesConfig.PAGE_CLIPBOARD
                )
                if (!legacyEmojiFirst) {
                    cyclePages.reverse()
                }
                cyclePages + SymPagesConfig.PAGE_EMOJI_PICKER
            }

            val parsedConfig = SymPagesConfig(
                deviceEnabled = deviceEnabled,
                emojiEnabled = emojiEnabled,
                symbolsEnabled = symbolsEnabled,
                clipboardEnabled = clipboardEnabled,
                emojiPickerEnabled = emojiPickerEnabled,
                symPageOrder = parsedOrder
            )
            val migratedConfig = if (schemaVersion < SYM_PAGES_SCHEMA_VERSION && parsedConfig.isLegacyDefault()) {
                parsedConfig.copy(deviceEnabled = true)
            } else {
                parsedConfig
            }
            if (schemaVersion < SYM_PAGES_SCHEMA_VERSION) {
                setSymPagesConfig(context, migratedConfig)
            }
            migratedConfig
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SYM pages config", e)
            DEFAULT_SYM_PAGES_CONFIG
        }
    }

    /**
     * Persists the SYM pages configuration (enabled pages and order).
     */
    fun setSymPagesConfig(context: Context, config: SymPagesConfig) {
        try {
            val jsonObject = JSONObject().apply {
                put("schemaVersion", SYM_PAGES_SCHEMA_VERSION)
                put("deviceEnabled", config.deviceEnabled)
                put("emojiEnabled", config.emojiEnabled)
                put("symbolsEnabled", config.symbolsEnabled)
                put("clipboardEnabled", config.clipboardEnabled)
                put("emojiPickerEnabled", config.emojiPickerEnabled)
                // Keep legacy field for backward compatibility with older builds.
                put("emojiFirst", config.prefersEmojiLongPressLayer())
                val orderArray = org.json.JSONArray()
                config.normalizedOrder().forEach { orderArray.put(it) }
                put("symPageOrder", orderArray)
            }

            getPreferences(context).edit()
                .putString(KEY_SYM_PAGES_CONFIG, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SYM pages config", e)
        }
    }

    private fun SymPagesConfig.isLegacyDefault(): Boolean =
        !deviceEnabled &&
            emojiEnabled &&
            symbolsEnabled &&
            !clipboardEnabled &&
            !emojiPickerEnabled &&
            normalizedOrder() == SymPagesConfig.OLDER_DEFAULT_ORDER

    fun getAltModifierBinding(context: Context): AltModifierBinding {
        val prefs = getPreferences(context)
        prefs.getString(KEY_ALT_MODIFIER_BINDING, null)?.let {
            return AltModifierBinding.fromPersistedValue(it)
        }

        val legacyValue = prefs.getString(LEGACY_KEY_ALT_CHARACTER_LAYER_BINDING, null)
        val binding = AltModifierBinding.fromPersistedValue(legacyValue)
        if (legacyValue != null) {
            prefs.edit()
                .putString(KEY_ALT_MODIFIER_BINDING, binding.persistedValue)
                .remove(LEGACY_KEY_ALT_CHARACTER_LAYER_BINDING)
                .apply()
        }
        return binding
    }

    fun setAltModifierBinding(context: Context, binding: AltModifierBinding) {
        getPreferences(context).edit()
            .putString(KEY_ALT_MODIFIER_BINDING, binding.persistedValue)
            .remove(LEGACY_KEY_ALT_CHARACTER_LAYER_BINDING)
            .apply()
    }
    
    /**
     * Gets whether SYM layout should auto-close after key press.
     * @param context The context
     * @return true if SYM should auto-close, false otherwise
     */
    fun getSymAutoClose(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SYM_AUTO_CLOSE, DEFAULT_SYM_AUTO_CLOSE)
    }
    
    /**
     * Sets whether SYM layout should auto-close after key press.
     * @param context The context
     * @param enabled true to enable auto-close, false to disable
     */
    fun setSymAutoClose(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SYM_AUTO_CLOSE, enabled)
            .apply()
    }

    fun getSymAutoCloseOnTouch(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_SYM_AUTO_CLOSE_ON_TOUCH,
            DEFAULT_SYM_AUTO_CLOSE_ON_TOUCH
        )
    }

    fun setSymAutoCloseOnTouch(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SYM_AUTO_CLOSE_ON_TOUCH, enabled)
            .apply()
    }

    fun getEmojiPickerExpandedHeight(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_EMOJI_PICKER_EXPANDED_HEIGHT,
            DEFAULT_EMOJI_PICKER_EXPANDED_HEIGHT
        )
    }

    fun setEmojiPickerExpandedHeight(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_EMOJI_PICKER_EXPANDED_HEIGHT, enabled)
            .apply()
    }

    
    
    
    /**
     * Checks if the tutorial has been completed.
     * @param context The context
     * @return true if the tutorial has been completed, false otherwise
     */
    fun isTutorialCompleted(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Marks the tutorial as completed.
     * @param context The context
     */
    fun setTutorialCompleted(context: Context) {
        getPreferences(context).edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, true)
            .apply()
    }
    
    /**
     * Resets the tutorial completion status, allowing it to be shown again.
     * @param context The context
     */
    fun resetTutorialCompleted(context: Context) {
        getPreferences(context).edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, false)
            .apply()
    }

    /**
     * Returns whether clipboard history is enabled.
     * @param context The context
     * @return true if clipboard history is enabled, false otherwise
     */
    fun getClipboardHistoryEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, DEFAULT_CLIPBOARD_HISTORY_ENABLED)
    }

    /**
     * Sets whether clipboard history is enabled.
     * @param context The context
     * @param enabled Whether to enable clipboard history
     */
    fun setClipboardHistoryEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the clipboard retention time in minutes.
     * Entries older than this will be automatically deleted (unless pinned).
     * @param context The context
     * @return Retention time in minutes (e.g. 120 = 2 hours)
     */
    fun getClipboardRetentionTime(context: Context): Long {
        return getPreferences(context).getLong(KEY_CLIPBOARD_RETENTION_TIME, DEFAULT_CLIPBOARD_RETENTION_TIME)
    }

    /**
     * Sets the clipboard retention time in minutes.
     * @param context The context
     * @param minutes Retention time in minutes (e.g. 120 = 2 hours)
     */
    fun setClipboardRetentionTime(context: Context, minutes: Long) {
        getPreferences(context).edit()
            .putLong(KEY_CLIPBOARD_RETENTION_TIME, minutes)
            .apply()
    }

    /**
     * Returns the File for variations.json in filesDir.
     */
    fun getVariationsFile(context: Context): File {
        return File(context.filesDir, VARIATIONS_FILE_NAME)
    }
    
    /**
     * Helper to load current JSON from file or assets.
     */
    private fun loadCurrentJson(context: Context): JSONObject? {
        return try {
            val variationsFile = getVariationsFile(context)
            val jsonString = if (variationsFile.exists()) {
                variationsFile.readText()
            } else {
                context.assets.open("common/variations/variations.json").bufferedReader().use { it.readText() }
            }
            JSONObject(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading current JSON", e)
            null
        }
    }

    private fun saveStaticVariationRows(
        context: Context,
        staticVariations: List<String>,
        staticVariationsShift: List<String>,
        staticVariationsAlt: List<String>
    ) {
        try {
            val currentJson = loadCurrentJson(context)
            val jsonObject = if (currentJson != null) {
                JSONObject(currentJson.toString())
            } else {
                JSONObject()
            }

            val baseArray = org.json.JSONArray()
            staticVariations.forEach { baseArray.put(it) }
            jsonObject.put("staticVariations", baseArray)

            val shiftArray = org.json.JSONArray()
            staticVariationsShift.forEach { shiftArray.put(it) }
            jsonObject.put("staticVariationsShift", shiftArray)

            val altArray = org.json.JSONArray()
            staticVariationsAlt.forEach { altArray.put(it) }
            jsonObject.put("staticVariationsAlt", altArray)

            FileOutputStream(getVariationsFile(context)).use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving static variation rows", e)
        }
    }
    
    /**
     * Saves variations to variations.json file in filesDir.
     */
    fun saveVariations(
        context: Context,
        variations: Map<String, List<String>>,
        staticVariations: List<String>? = null,
        staticVariationsShift: List<String>? = null,
        staticVariationsAlt: List<String>? = null
    ) {
        try {
            val variationsObject = JSONObject()
            for ((letter, chars) in variations) {
                val variationsArray = org.json.JSONArray()
                for (char in chars) {
                    variationsArray.put(char)
                }
                variationsObject.put(letter, variationsArray)
            }
            
            val currentJson = loadCurrentJson(context)
            val jsonObject = if (currentJson != null) {
                JSONObject(currentJson.toString())
            } else {
                JSONObject()
            }
            jsonObject.put("variations", variationsObject)

            // Preserve/update staticVariations
            if (staticVariations != null) {
                val staticArray = org.json.JSONArray()
                staticVariations.forEach { staticArray.put(it) }
                jsonObject.put("staticVariations", staticArray)
            }

            // Preserve/update staticVariationsShift
            if (staticVariationsShift != null) {
                val staticArray = org.json.JSONArray()
                staticVariationsShift.forEach { staticArray.put(it) }
                jsonObject.put("staticVariationsShift", staticArray)
            }

            // Preserve/update staticVariationsAlt
            if (staticVariationsAlt != null) {
                val staticArray = org.json.JSONArray()
                staticVariationsAlt.forEach { staticArray.put(it) }
                jsonObject.put("staticVariationsAlt", staticArray)
            }
            
            FileOutputStream(getVariationsFile(context)).use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
            }
            
            notifyVariationsUpdated(context)
            
            Log.d(TAG, "Variations saved to ${getVariationsFile(context).absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving variations", e)
        }
    }

    fun saveStaticVariationBasePreset(context: Context, staticVariations: List<String>) {
        try {
            val jsonObject = loadCurrentJson(context) ?: JSONObject()
            val staticArray = org.json.JSONArray()
            staticVariations.forEach { staticArray.put(it) }
            jsonObject.put("staticVariations", staticArray)

            FileOutputStream(getVariationsFile(context)).use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
            }

            notifyVariationsUpdated(context)
            Log.d(TAG, "Static variation base preset saved to ${getVariationsFile(context).absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving static variation base preset", e)
        }
    }
    
    /**
     * Resets variations back to defaults by copying defaultvariations.json from assets.
     */
    fun resetVariationsToDefault(context: Context) {
        try {
            val variationsFile = getVariationsFile(context)
            val inputStream = context.assets.open("common/variations/defaultvariations.json")
            FileOutputStream(variationsFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            notifyVariationsUpdated(context)
            
            Log.d(TAG, "Variations reset to default from assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting variations to default", e)
        }
    }
    
    /**
     * Returns true if custom variations file exists.
     */
    fun hasCustomVariations(context: Context): Boolean {
        return getVariationsFile(context).exists()
    }
    
    /**
     * Touch the variations_updated flag so the IME reloads variations/static bar content.
     */
    fun notifyVariationsUpdated(context: Context) {
        getPreferences(context).edit()
            .putLong(KEY_VARIATIONS_UPDATED, System.currentTimeMillis())
            .apply()
    }

    // ========================
    // Status Bar Button Slots
    // ========================
    
    /**
     * Gets the button assigned to the left slot.
     */
    fun getStatusBarSlotLeft(context: Context): String {
        return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_LEFT, DEFAULT_SLOT_LEFT)
            ?: DEFAULT_SLOT_LEFT
    }

    data class StatusBarSlotDefaults(
        val left: String,
        val right1: String,
        val right2: String
    )

    /**
     * Returns the default slot assignment for the status bar.
     */
    fun getDefaultStatusBarSlots(): StatusBarSlotDefaults {
        return StatusBarSlotDefaults(
            left = DEFAULT_SLOT_LEFT,
            right1 = DEFAULT_SLOT_RIGHT_1,
            right2 = DEFAULT_SLOT_RIGHT_2
        )
    }

    fun getDefaultStatusBarSlotsLeft(): List<String> = listOf(DEFAULT_SLOT_LEFT)

    fun getDefaultStatusBarSlotsRight(): List<String> = listOf(DEFAULT_SLOT_RIGHT_1)

    /**
     * Resets status bar slots to the defaults and returns the applied values.
     */
    fun resetStatusBarSlotsToDefault(context: Context): StatusBarSlotDefaults {
        val defaults = getDefaultStatusBarSlots()
        setStatusBarSlotLeft(context, defaults.left)
        setStatusBarSlotRight1(context, defaults.right1)
        setStatusBarSlotRight2(context, defaults.right2)
        setStatusBarSlotsLeft(context, getDefaultStatusBarSlotsLeft())
        setStatusBarSlotsRight(context, getDefaultStatusBarSlotsRight())
        setStatusBarVariationsVisible(context, DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE)
        setDynamicVariationBarSlotCount(context, DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
        setDynamicVariationBarResizeToContent(context, DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT)
        return defaults
    }

    fun getStatusBarSlotsLeft(context: Context): List<String> {
        return getStatusBarSlotsList(
            context = context,
            key = KEY_STATUS_BAR_SLOTS_LEFT,
            fallback = listOf(getStatusBarSlotLeft(context))
        )
    }

    fun setStatusBarSlotsLeft(context: Context, buttonIds: List<String>) {
        val normalized = normalizeStatusBarSlots(buttonIds)
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOTS_LEFT, statusBarSlotsToJson(normalized))
            .putString(KEY_STATUS_BAR_SLOT_LEFT, normalized.firstOrNull() ?: STATUS_BAR_BUTTON_NONE)
            .apply()
    }

    fun getStatusBarSlotsRight(context: Context): List<String> {
        return getStatusBarSlotsList(
            context = context,
            key = KEY_STATUS_BAR_SLOTS_RIGHT,
            fallback = listOf(getStatusBarSlotRight1(context), getStatusBarSlotRight2(context))
                .filter { it != STATUS_BAR_BUTTON_NONE }
        )
    }

    fun setStatusBarSlotsRight(context: Context, buttonIds: List<String>) {
        val normalized = normalizeStatusBarSlots(buttonIds)
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOTS_RIGHT, statusBarSlotsToJson(normalized))
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_1, normalized.getOrNull(0) ?: STATUS_BAR_BUTTON_NONE)
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_2, normalized.getOrNull(1) ?: STATUS_BAR_BUTTON_NONE)
            .apply()
    }

    fun getDefaultPastierinaStatusBarSlotsLeft(): List<String> = listOf(DEFAULT_PASTIERINA_SLOT_LEFT)

    fun getDefaultPastierinaStatusBarSlotsRight(): List<String> = listOf(DEFAULT_PASTIERINA_SLOT_RIGHT)

    fun resetPastierinaStatusBarSlotsToDefault(context: Context) {
        setPastierinaStatusBarSlotsLeft(context, getDefaultPastierinaStatusBarSlotsLeft())
        setPastierinaStatusBarSlotsRight(context, getDefaultPastierinaStatusBarSlotsRight())
    }

    fun getPastierinaStatusBarSlotsLeft(context: Context): List<String> {
        return getStatusBarSlotsList(
            context = context,
            key = KEY_PASTIERINA_STATUS_BAR_SLOTS_LEFT,
            fallback = getDefaultPastierinaStatusBarSlotsLeft()
        )
    }

    fun setPastierinaStatusBarSlotsLeft(context: Context, buttonIds: List<String>) {
        val normalized = normalizeStatusBarSlots(buttonIds)
        getPreferences(context).edit()
            .putString(KEY_PASTIERINA_STATUS_BAR_SLOTS_LEFT, statusBarSlotsToJson(normalized))
            .apply()
    }

    fun getPastierinaStatusBarSlotsRight(context: Context): List<String> {
        return getStatusBarSlotsList(
            context = context,
            key = KEY_PASTIERINA_STATUS_BAR_SLOTS_RIGHT,
            fallback = getDefaultPastierinaStatusBarSlotsRight()
        )
    }

    fun setPastierinaStatusBarSlotsRight(context: Context, buttonIds: List<String>) {
        val normalized = normalizeStatusBarSlots(buttonIds)
        getPreferences(context).edit()
            .putString(KEY_PASTIERINA_STATUS_BAR_SLOTS_RIGHT, statusBarSlotsToJson(normalized))
            .apply()
    }
    
    /**
     * Sets the button for the left slot.
     */
    fun setStatusBarSlotLeft(context: Context, buttonId: String) {
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOT_LEFT, buttonId)
            .apply()
    }
    
    /**
     * Gets the button assigned to the first right slot.
     */
    fun getStatusBarSlotRight1(context: Context): String {
        return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_RIGHT_1, DEFAULT_SLOT_RIGHT_1)
            ?: DEFAULT_SLOT_RIGHT_1
    }
    
    /**
     * Sets the button for the first right slot.
     */
    fun setStatusBarSlotRight1(context: Context, buttonId: String) {
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_1, buttonId)
            .apply()
    }
    
    /**
     * Gets the button assigned to the second right slot.
     */
    fun getStatusBarSlotRight2(context: Context): String {
        return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_RIGHT_2, DEFAULT_SLOT_RIGHT_2)
            ?: DEFAULT_SLOT_RIGHT_2
    }
    
    /**
     * Sets the button for the second right slot.
     */
    fun setStatusBarSlotRight2(context: Context, buttonId: String) {
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_2, buttonId)
            .apply()
    }

    private fun getStatusBarSlotsList(
        context: Context,
        key: String,
        fallback: List<String>
    ): List<String> {
        val stored = getPreferences(context).getString(key, null)
        if (!stored.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(stored)
                val parsed = buildList {
                    for (index in 0 until array.length()) {
                        add(array.optString(index, STATUS_BAR_BUTTON_NONE))
                    }
                }
                return normalizeStatusBarSlots(parsed)
            }
        }
        return normalizeStatusBarSlots(fallback)
    }

    private fun normalizeStatusBarSlots(buttonIds: List<String>): List<String> {
        val available = getAvailableStatusBarButtons().toSet()
        return buttonIds.map { stored ->
            val buttonId = if (stored == LEGACY_STATUS_BAR_BUTTON_LANGUAGE) STATUS_BAR_BUTTON_EMOJI else stored
            if (buttonId in available) buttonId else STATUS_BAR_BUTTON_NONE
        }
    }

    private fun statusBarSlotsToJson(buttonIds: List<String>): String {
        val array = JSONArray()
        buttonIds.forEach { array.put(it) }
        return array.toString()
    }

    fun getStatusBarVariationsVisible(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_STATUS_BAR_VARIATIONS_VISIBLE,
            DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE
        )
    }

    fun setStatusBarVariationsVisible(context: Context, visible: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATUS_BAR_VARIATIONS_VISIBLE, visible)
            .apply()
    }

    fun areStatusBarVariationsEnabled(context: Context): Boolean {
        return getStatusBarVariationsVisible(context)
    }

    fun setStatusBarVariationsEnabled(context: Context, enabled: Boolean) {
        setStatusBarVariationsVisible(context, enabled)
    }

    fun getDynamicVariationBarSlotCount(context: Context): Int {
        return getPreferences(context).getInt(
            KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
            DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT
        ).coerceIn(MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT, MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
    }

    fun setDynamicVariationBarSlotCount(context: Context, count: Int) {
        getPreferences(context).edit()
            .putInt(
                KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
                count.coerceIn(MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT, MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
            )
            .apply()
    }

    fun getDynamicVariationBarResizeToContent(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT,
            DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT
        )
    }

    fun setDynamicVariationBarResizeToContent(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT, enabled)
            .apply()
    }

    fun getModifierIndicators(context: Context): Set<String> {
        return normalizeModifierIndicators(
            getPreferences(context).getString(
                KEY_MODIFIER_INDICATOR_MODE,
                encodeModifierIndicators(DEFAULT_MODIFIER_INDICATORS)
            )
        )
    }

    fun setModifierIndicators(context: Context, indicators: Set<String>) {
        getPreferences(context).edit()
            .putString(KEY_MODIFIER_INDICATOR_MODE, encodeModifierIndicators(normalizeModifierIndicators(indicators)))
            .apply()
    }

    fun getModifierIndicatorShowsBottomStrip(context: Context): Boolean {
        return MODIFIER_INDICATOR_BOTTOM_STRIP in getModifierIndicators(context)
    }

    fun getModifierIndicatorShowsMenuBar(context: Context): Boolean {
        return MODIFIER_INDICATOR_MENU_BAR in getModifierIndicators(context)
    }

    fun getModifierIndicatorShowsStatusBar(context: Context): Boolean {
        return MODIFIER_INDICATOR_STATUS_BAR in getModifierIndicators(context)
    }

    private fun normalizeModifierIndicators(stored: String?): Set<String> {
        return when (stored) {
            MODIFIER_INDICATOR_MODE_OFF -> emptySet()
            MODIFIER_INDICATOR_MODE_BOTTOM -> setOf(MODIFIER_INDICATOR_BOTTOM_STRIP)
            MODIFIER_INDICATOR_MODE_BOTTOM_AND_MENU -> setOf(
                MODIFIER_INDICATOR_BOTTOM_STRIP,
                MODIFIER_INDICATOR_STATUS_BAR
            )
            MODIFIER_INDICATOR_MODE_MENU -> setOf(MODIFIER_INDICATOR_STATUS_BAR)
            else -> normalizeModifierIndicators(
                stored
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: DEFAULT_MODIFIER_INDICATORS
            )
        }
    }

    private fun normalizeModifierIndicators(indicators: Set<String>): Set<String> {
        val allowed = setOf(
            MODIFIER_INDICATOR_BOTTOM_STRIP,
            MODIFIER_INDICATOR_MENU_BAR,
            MODIFIER_INDICATOR_STATUS_BAR
        )
        val normalized = indicators.filter { it in allowed }.toSet()
        return if (normalized.isEmpty() && indicators.isNotEmpty()) {
            DEFAULT_MODIFIER_INDICATORS
        } else {
            normalized
        }
    }

    private fun encodeModifierIndicators(indicators: Set<String>): String {
        val order = listOf(
            MODIFIER_INDICATOR_BOTTOM_STRIP,
            MODIFIER_INDICATOR_MENU_BAR,
            MODIFIER_INDICATOR_STATUS_BAR
        )
        return order.filter { it in indicators }.joinToString(",")
    }
    
    /**
     * Returns all available button options for dropdown selection.
     */
    fun getAvailableStatusBarButtons(): List<String> {
        return listOf(
            STATUS_BAR_BUTTON_NONE,
            STATUS_BAR_BUTTON_CLIPBOARD,
            STATUS_BAR_BUTTON_EMOJI,
            STATUS_BAR_BUTTON_MICROPHONE,
            STATUS_BAR_BUTTON_HAMBURGER,
            STATUS_BAR_BUTTON_MINIMAL_UI,
            STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE,
            STATUS_BAR_BUTTON_SETTINGS,
            STATUS_BAR_BUTTON_SYMBOLS,
            STATUS_BAR_BUTTON_UNDO,
            STATUS_BAR_BUTTON_REDO
        )
    }

    data class AppEnterBehaviorOverride(
        val packageName: String,
        val behavior: String,
        val sendStrategy: String = ENTER_SEND_STRATEGY_AUTO,
        val additionalSendShortcut: String = ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    )

    fun getAppEnterBehaviorEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_APP_ENTER_BEHAVIOR_ENABLED, true)
    }

    fun setAppEnterBehaviorEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_APP_ENTER_BEHAVIOR_ENABLED, enabled)
            .apply()
    }

    fun getAppEnterBehaviorPreset(context: Context): String {
        val stored = getPreferences(context).getString(
            KEY_APP_ENTER_BEHAVIOR_PRESET,
            ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE
        ) ?: ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE
        return normalizeEnterBehaviorPreset(stored)
    }

    fun setAppEnterBehaviorPreset(context: Context, preset: String) {
        getPreferences(context).edit()
            .putString(KEY_APP_ENTER_BEHAVIOR_PRESET, normalizeEnterBehaviorPreset(preset))
            .apply()
    }

    fun getAppEnterBehaviorOverrides(context: Context): List<AppEnterBehaviorOverride> {
        val stored = getPreferences(context).getString(KEY_APP_ENTER_BEHAVIOR_OVERRIDES, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                val seen = mutableSetOf<String>()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val packageName = item.optString("packageName", "")
                    if (packageName.isBlank() || !seen.add(packageName)) continue
                    add(
                        AppEnterBehaviorOverride(
                            packageName = packageName,
                            behavior = normalizeEnterBehavior(item.optString("behavior", ENTER_BEHAVIOR_APP_DEFAULT)),
                            sendStrategy = normalizeEnterSendStrategy(
                                item.optString("sendStrategy", ENTER_SEND_STRATEGY_AUTO)
                            ),
                            additionalSendShortcut = normalizeEnterAdditionalSendShortcut(
                                item.optString("additionalSendShortcut", ENTER_ADDITIONAL_SEND_SHORTCUT_NONE)
                            )
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "Error loading app enter behavior overrides", it)
            emptyList()
        }
    }

    fun setAppEnterBehaviorOverrides(context: Context, overrides: List<AppEnterBehaviorOverride>) {
        val array = JSONArray()
        overrides
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .forEach { override ->
                array.put(
                    JSONObject().apply {
                        put("packageName", override.packageName)
                        put("behavior", normalizeEnterBehavior(override.behavior))
                        put("sendStrategy", normalizeEnterSendStrategy(override.sendStrategy))
                        put("additionalSendShortcut", normalizeEnterAdditionalSendShortcut(override.additionalSendShortcut))
                    }
                )
            }
        getPreferences(context).edit()
            .putString(KEY_APP_ENTER_BEHAVIOR_OVERRIDES, array.toString())
            .apply()
    }

    private fun normalizeEnterBehaviorPreset(preset: String): String {
        return when (preset) {
            ENTER_BEHAVIOR_PRESET_APP_DEFAULT,
            ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE,
            ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND,
            ENTER_BEHAVIOR_PRESET_CUSTOM -> preset
            else -> ENTER_BEHAVIOR_PRESET_APP_DEFAULT
        }
    }

    private fun normalizeEnterBehavior(behavior: String): String {
        return when (behavior) {
            ENTER_BEHAVIOR_APP_DEFAULT,
            ENTER_BEHAVIOR_ENTER_NEWLINE,
            ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
            ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND -> behavior
            else -> ENTER_BEHAVIOR_APP_DEFAULT
        }
    }

    private fun normalizeEnterSendStrategy(strategy: String): String {
        return when (strategy) {
            ENTER_SEND_STRATEGY_AUTO,
            ENTER_SEND_STRATEGY_EDITOR_ACTION,
            ENTER_SEND_STRATEGY_CTRL_ENTER,
            ENTER_SEND_STRATEGY_PLAIN_ENTER -> strategy
            else -> ENTER_SEND_STRATEGY_AUTO
        }
    }

    private fun normalizeEnterAdditionalSendShortcut(shortcut: String): String {
        return when (shortcut) {
            ENTER_ADDITIONAL_SEND_SHORTCUT_NONE,
            ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER -> shortcut
            else -> ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        }
    }
}
