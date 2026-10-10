package it.palsoftware.pastiera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.KeyboardControlKey
import androidx.compose.material.icons.filled.KeyboardOptionKey
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.SettingsRowIcon
import it.palsoftware.pastiera.ui.SettingsSliderRow
import it.palsoftware.pastiera.ui.SettingsRowStartInset
import it.palsoftware.pastiera.ui.SettingsSubRowStartInset

@Composable
fun ModifierSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenSymLayers: () -> Unit,
    onOpenNavMode: () -> Unit,
    onOpenOnScreenKeyboard: () -> Unit,
    onOpenBuiltInKeyboard: () -> Unit
) {
    val context = LocalContext.current
    var longPressModifier by remember { mutableStateOf(SettingsManager.getLongPressModifier(context)) }
    var longPressThreshold by remember { mutableStateOf(SettingsManager.getLongPressThreshold(context)) }
    var altBinding by remember { mutableStateOf(SettingsManager.getAltModifierBinding(context)) }
    var shiftTapLatches by remember { mutableStateOf(SettingsManager.getShiftTapLatches(context)) }
    var altTapLatches by remember { mutableStateOf(SettingsManager.getAltTapLatches(context)) }
    var clearAltOnSpace by remember { mutableStateOf(SettingsManager.getClearAltOnSpace(context)) }
    var altLatchStaysOnSpace by remember { mutableStateOf(SettingsManager.getAltLatchStaysOnSpace(context)) }
    var altCtrlSpeechShortcut by remember { mutableStateOf(SettingsManager.getAltCtrlSpeechShortcutEnabled(context)) }
    var ctrlTapLatches by remember { mutableStateOf(SettingsManager.getCtrlTapLatches(context)) }
    var ctrlLatchStaysOnSpace by remember { mutableStateOf(SettingsManager.getCtrlLatchStaysOnSpace(context)) }
    var modifierIndicators by remember { mutableStateOf(SettingsManager.getModifierIndicators(context)) }
    var longPressExpanded by remember { mutableStateOf(false) }
    var altBindingExpanded by remember { mutableStateOf(false) }

    // On the Titan 2 Elite, Alt types what is printed on the key. The choice stays
    // only for users who changed it, so they can set it back (decided once per visit).
    val showAltBinding = remember {
        SettingsManager.getAltModifierBinding(context) != AltModifierBinding.DeviceSym
    }

    val longPressOptions = listOf(
        "alt" to stringResource(R.string.long_press_modifier_alt),
        "shift" to stringResource(R.string.long_press_modifier_shift),
        "variations" to stringResource(R.string.long_press_modifier_variations),
        "sym" to stringResource(R.string.long_press_modifier_sym),
        "sym_symbols" to stringResource(R.string.long_press_modifier_sym_symbols),
        "sym_emoji" to stringResource(R.string.long_press_modifier_sym_emoji)
    )
    val altBindingOptions = listOf(
        AltModifierBinding.DeviceSym to stringResource(R.string.alt_binding_current_device),
        AltModifierBinding.FirstEnabledSymKeyLayer to stringResource(R.string.alt_binding_first_key_layer),
        AltModifierBinding.Emoji to stringResource(R.string.sym_cycle_emoji_layer),
        AltModifierBinding.Symbols to stringResource(R.string.sym_cycle_symbols_layer),
        AltModifierBinding.DeviceSymProfile("titan") to stringResource(R.string.keyboard_profile_option_titan),
        AltModifierBinding.DeviceSymProfile("titan2") to stringResource(R.string.keyboard_profile_option_titan2),
        AltModifierBinding.DeviceSymProfile("titan2elite_qwerty") to stringResource(R.string.keyboard_profile_option_titan2elite_qwerty)
    )

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back_content_description))
                    }
                    Text(
                        text = stringResource(R.string.modifiers_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier.fillMaxWidth().padding(paddingValues).verticalScroll(rememberScrollState())
        ) {
            SettingsSectionDivider(stringResource(R.string.modifiers_section_long_press))
            ModifierDropdownRow(
                title = stringResource(R.string.long_press_modifier_title),
                icon = Icons.Filled.TouchApp,
                value = longPressOptions.firstOrNull { it.first == longPressModifier }?.second.orEmpty(),
                linkId = SettingLinkIds.MODIFIERS_LONG_PRESS_MODIFIER,
                expanded = longPressExpanded,
                onExpand = { longPressExpanded = true },
                onDismiss = { longPressExpanded = false },
                options = longPressOptions,
                onSelected = { value ->
                    longPressModifier = value
                    SettingsManager.setLongPressModifier(context, value)
                    longPressExpanded = false
                }
            )
            ModifierLongPressThresholdRow(
                threshold = longPressThreshold,
                linkId = SettingLinkIds.MODIFIERS_LONG_PRESS_THRESHOLD,
                onThresholdChanged = { threshold ->
                    longPressThreshold = threshold
                    SettingsManager.setLongPressThreshold(context, threshold)
                }
            )

            SettingsSectionDivider(stringResource(R.string.modifiers_section_sym))
            ModifierNavigationRow(
                iconRes = R.drawable.ic_emoji_symbols_24,
                title = stringResource(R.string.sym_customization_title),
                description = stringResource(R.string.sym_customization_description),
                linkId = SettingLinkIds.MODIFIERS_SYM_LAYERS,
                onClick = onOpenSymLayers
            )
            // App shortcuts opens from the main list only; its old link id falls back there.

            SettingsSectionDivider(stringResource(R.string.modifiers_section_shift))
            ModifierSwitchRow(
                title = stringResource(R.string.shift_tap_latches_title),
                icon = Icons.Filled.KeyboardCapslock,
                description = stringResource(R.string.shift_tap_latches_description),
                checked = shiftTapLatches,
                linkId = SettingLinkIds.MODIFIERS_SHIFT_TAP_LATCHES
            ) {
                shiftTapLatches = it
                SettingsManager.setShiftTapLatches(context, it)
            }

            SettingsSectionDivider(stringResource(R.string.modifiers_section_alt_modifier))
            ModifierSwitchRow(
                title = stringResource(R.string.alt_tap_latches_title),
                icon = Icons.Filled.KeyboardOptionKey,
                description = stringResource(R.string.alt_tap_latches_description),
                checked = altTapLatches,
                linkId = SettingLinkIds.MODIFIERS_ALT_TAP_LATCHES
            ) {
                altTapLatches = it
                SettingsManager.setAltTapLatches(context, it)
            }
            ModifierSwitchRow(
                title = stringResource(R.string.clear_alt_on_space_title),
                icon = Icons.Filled.SpaceBar,
                description = stringResource(R.string.clear_alt_on_space_description),
                checked = clearAltOnSpace,
                linkId = SettingLinkIds.TEXT_INPUT_CLEAR_ALT_ON_SPACE
            ) {
                clearAltOnSpace = it
                SettingsManager.setClearAltOnSpace(context, it)
            }
            // Only matters while Space and Enter release Alt (see clearAltOnBoundaryIfNeeded).
            if (clearAltOnSpace) {
                ModifierSwitchRow(
                    title = stringResource(R.string.alt_latch_stays_on_space_title),
                    icon = null,
                    description = stringResource(R.string.alt_latch_stays_on_space_description),
                    checked = altLatchStaysOnSpace,
                    indent = true,
                    linkId = SettingLinkIds.MODIFIERS_ALT_LATCH_STAYS_ON_SPACE
                ) {
                    altLatchStaysOnSpace = it
                    SettingsManager.setAltLatchStaysOnSpace(context, it)
                }
            }
            ModifierSwitchRow(
                title = stringResource(R.string.alt_ctrl_speech_shortcut_title),
                icon = Icons.Filled.Mic,
                description = stringResource(R.string.alt_ctrl_speech_shortcut_description),
                checked = altCtrlSpeechShortcut,
                linkId = SettingLinkIds.TEXT_INPUT_ALT_CTRL_SPEECH_SHORTCUT
            ) {
                altCtrlSpeechShortcut = it
                SettingsManager.setAltCtrlSpeechShortcutEnabled(context, it)
            }
            if (showAltBinding) {
                ModifierDropdownRow(
                    title = stringResource(R.string.alt_binding_title),
                    icon = Icons.Filled.KeyboardAlt,
                    description = stringResource(R.string.alt_binding_description),
                    value = altBindingOptions.firstOrNull { it.first == altBinding }?.second
                        ?: altBindingOptions.first().second,
                    linkId = SettingLinkIds.MODIFIERS_ALT_BINDING,
                    expanded = altBindingExpanded,
                    onExpand = { altBindingExpanded = true },
                    onDismiss = { altBindingExpanded = false },
                    options = altBindingOptions,
                    onSelected = { value ->
                        altBinding = value
                        SettingsManager.setAltModifierBinding(context, value)
                        altBindingExpanded = false
                    }
                )
            }
            Surface(modifier = Modifier.fillMaxWidth().settingRow("hardware.currency")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsRowIcon(Icons.Filled.AttachMoney, modifier = Modifier.padding(top = 2.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.keyboard_currency_symbol_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.keyboard_currency_symbol_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CurrencySymbolPicker()
                    }
                }
            }

            SettingsSectionDivider(stringResource(R.string.modifiers_section_control))
            ModifierNavigationRow(
                iconRes = R.drawable.navigation_24,
                title = stringResource(R.string.modifier_control_nav_mode_title),
                description = stringResource(R.string.modifier_control_nav_mode_description),
                linkId = SettingLinkIds.MODIFIERS_CONTROL_NAV_MODE,
                onClick = onOpenNavMode
            )
            ModifierSwitchRow(
                title = stringResource(R.string.ctrl_tap_latches_title),
                icon = Icons.Filled.KeyboardControlKey,
                description = stringResource(R.string.ctrl_tap_latches_description),
                checked = ctrlTapLatches,
                linkId = SettingLinkIds.MODIFIERS_CTRL_TAP_LATCHES
            ) {
                ctrlTapLatches = it
                SettingsManager.setCtrlTapLatches(context, it)
            }
            if (ctrlTapLatches) {
                ModifierSwitchRow(
                    title = stringResource(R.string.ctrl_latch_stays_on_space_title),
                    icon = null,
                    description = stringResource(R.string.ctrl_latch_stays_on_space_description),
                    checked = ctrlLatchStaysOnSpace,
                    indent = true,
                    linkId = SettingLinkIds.MODIFIERS_CTRL_LATCH_STAYS_ON_SPACE
                ) {
                    ctrlLatchStaysOnSpace = it
                    SettingsManager.setCtrlLatchStaysOnSpace(context, it)
                }
            }

            SettingsSectionDivider(stringResource(R.string.modifiers_section_indicators))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingRow(SettingLinkIds.MODIFIERS_INDICATORS)
            ) {
                Column(
                    // Starts at the title column, like the section label above it.
                    modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.modifier_indicators_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModifierIndicatorMultiSelect(
                        modifier = Modifier.fillMaxWidth(),
                        selectedIndicators = modifierIndicators,
                        onIndicatorsSelected = { indicators ->
                            modifierIndicators = indicators
                            SettingsManager.setModifierIndicators(context, indicators)
                        }
                    )
                }
            }

            OtherKeyboardsSection(
                onOpenOnScreenKeyboard = onOpenOnScreenKeyboard,
                onOpenBuiltInKeyboard = onOpenBuiltInKeyboard
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Leftovers of the former Keyboard & Devices screen: switching between physical and
 * on-screen keyboards, and settings for other devices. Each row appears only if its saved
 * value differed from the default when the screen opened, so a user who changed it can
 * set it back; with everything at the default the whole section is hidden.
 */
@Composable
private fun OtherKeyboardsSection(
    onOpenOnScreenKeyboard: () -> Unit,
    onOpenBuiltInKeyboard: () -> Unit
) {
    val context = LocalContext.current
    val showSwitching = remember { HiddenKeyboardSettings.keyboardSwitchingChanged(context) }
    val showSwitchMessage = remember { HiddenKeyboardSettings.switchMessageChanged(context) }
    val showOnScreen = remember { HiddenKeyboardSettings.onScreenKeyboardChanged(context) }
    val showBuiltIn = remember { HiddenKeyboardSettings.builtInKeyboardChanged(context) }
    if (!(showSwitching || showSwitchMessage || showOnScreen || showBuiltIn)) return

    var automaticSwitching by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.AUTO)
    }
    var switchMessage by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context))
    }

    SettingsSectionDivider(stringResource(R.string.other_keyboards_title))
    Text(
        text = stringResource(R.string.other_keyboards_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 4.dp)
    )
    if (showSwitching) {
        ModifierSwitchRow(
            title = stringResource(R.string.keyboard_switching_auto_title),
            icon = Icons.Filled.SwapHoriz,
            description = stringResource(R.string.keyboard_switching_auto_description),
            checked = automaticSwitching,
            linkId = SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_AUTO
        ) { enabled ->
            // Same as the old Keyboard & Devices switch: off keeps the keyboard in use now.
            val newMode = if (enabled) {
                SettingsManager.SoftwareKeyboardMode.AUTO
            } else {
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
            }
            SettingsManager.setSoftwareKeyboardMode(context, newMode)
            automaticSwitching = enabled
        }
    }
    if (showSwitchMessage) {
        ModifierSwitchRow(
            title = stringResource(R.string.software_keyboard_mode_toggle_toasts_title),
            icon = Icons.AutoMirrored.Filled.Message,
            description = stringResource(R.string.software_keyboard_mode_toggle_toasts_description),
            checked = switchMessage,
            linkId = SettingLinkIds.KEYBOARDS_DEVICES_TOGGLE_TOASTS
        ) {
            switchMessage = it
            SettingsManager.setSoftwareKeyboardModeToggleToastsEnabled(context, it)
        }
    }
    if (showOnScreen) {
        ModifierNavigationRow(
            iconRes = R.drawable.modifier_keys_24,
            title = stringResource(R.string.on_screen_keyboard_title),
            description = stringResource(R.string.other_keyboards_on_screen_description),
            linkId = SettingLinkIds.KEYBOARDS_DEVICES_ON_SCREEN_KEYBOARD,
            onClick = onOpenOnScreenKeyboard
        )
    }
    if (showBuiltIn) {
        ModifierNavigationRow(
            iconRes = R.drawable.modifier_keys_24,
            title = stringResource(R.string.built_in_keyboards_title),
            description = stringResource(R.string.other_keyboards_built_in_description),
            linkId = SettingLinkIds.KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS,
            onClick = onOpenBuiltInKeyboard
        )
    }
}

@Composable
private fun ModifierLongPressThresholdRow(
    threshold: Long,
    linkId: String? = null,
    onThresholdChanged: (Long) -> Unit
) {
    SettingsSliderRow(
        icon = Icons.Filled.Timer,
        title = stringResource(R.string.long_press_title),
        value = stringResource(R.string.keyboard_timing_long_press_value, threshold),
        modifier = Modifier.settingRow(linkId)
    ) {
        Slider(
            value = threshold.toFloat(),
            onValueChange = { value ->
                onThresholdChanged(
                    value.toLong().coerceIn(
                        SettingsManager.getMinLongPressThreshold(),
                        SettingsManager.getMaxLongPressThreshold()
                    )
                )
            },
            valueRange = SettingsManager.getMinLongPressThreshold().toFloat()..
                SettingsManager.getMaxLongPressThreshold().toFloat(),
            steps = 18
        )
    }
}

@Composable
private fun ModifierNavigationRow(
    iconRes: Int,
    title: String,
    description: String,
    linkId: String? = null,
    onClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).settingRow(linkId, onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun <T> ModifierDropdownRow(
    title: String,
    icon: ImageVector?,
    value: String,
    linkId: String? = null,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    description: String? = null
) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).settingRow(linkId, onExpand)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsRowIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                options.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(id) })
                }
            }
        }
    }
}

@Composable
private fun ModifierSwitchRow(
    title: String,
    icon: ImageVector?,
    description: String,
    checked: Boolean,
    indent: Boolean = false,
    linkId: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).settingRow(linkId)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = if (indent) SettingsSubRowStartInset else SettingsRowStartInset,
                end = 16.dp, top = 8.dp, bottom = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsRowIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
