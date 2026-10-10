package it.palsoftware.pastiera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import androidx.compose.ui.graphics.vector.ImageVector
import it.palsoftware.pastiera.ui.SettingsDropdownRow

@Composable
internal fun VirtualKeyboardBehaviorSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenKeyboardTheme: () -> Unit
) {
    val context = LocalContext.current
    var softwareKeyboardLayoutStyle by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLayoutStyle(context))
    }
    var nearestKeyTouchEnabled by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardNearestKeyTouchEnabled(context))
    }
    var longPressLayerPopupEnabled by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context))
    }
    var longPressLayerPopupBelowKey by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context))
    }
    var numberRowEnabled by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardNumberRowEnabled(context))
    }
    var leftModifierKey by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardLeftModifierKey(context))
    }
    var rightModifierKey by remember {
        mutableStateOf(SettingsManager.getSoftwareKeyboardRightModifierKey(context))
    }

    DisposableEffect(context) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "software_keyboard_left_modifier_key" -> {
                    leftModifierKey = SettingsManager.getSoftwareKeyboardLeftModifierKey(context)
                }
                "software_keyboard_right_modifier_key" -> {
                    rightModifierKey = SettingsManager.getSoftwareKeyboardRightModifierKey(context)
                }
                "software_keyboard_long_press_layer_popup_enabled" -> {
                    longPressLayerPopupEnabled =
                        SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context)
                }
                "software_keyboard_long_press_layer_popup_below_key" -> {
                    longPressLayerPopupBelowKey =
                        SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }


    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.virtual_keyboard_behaviour_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VirtualKeyboardSectionTitle(stringResource(R.string.on_screen_keyboard_section_layout))
            val softwareKeyboardLayoutStyleLabel = when (softwareKeyboardLayoutStyle) {
                SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT ->
                    stringResource(R.string.software_keyboard_layout_style_compact)
                SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO ->
                    stringResource(R.string.software_keyboard_layout_style_extended_iso)
                SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI ->
                    stringResource(R.string.software_keyboard_layout_style_full_ansi)
                SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO ->
                    stringResource(R.string.software_keyboard_layout_style_full_iso)
            }

            SettingsDropdownRow(
                title = stringResource(R.string.software_keyboard_layout_style_title),
                value = stringResource(
                    R.string.software_keyboard_layout_style_description,
                    softwareKeyboardLayoutStyleLabel
                ),
                options = listOf(
                    SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT,
                    SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO,
                    SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI,
                    SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO
                ),
                selected = softwareKeyboardLayoutStyle,
                optionLabel = { style ->
                    when (style) {
                        SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT ->
                            stringResource(R.string.software_keyboard_layout_style_compact)
                        SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO ->
                            stringResource(R.string.software_keyboard_layout_style_extended_iso)
                        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI ->
                            stringResource(R.string.software_keyboard_layout_style_full_ansi)
                        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO ->
                            stringResource(R.string.software_keyboard_layout_style_full_iso)
                    }
                },
                onSelect = { style ->
                    softwareKeyboardLayoutStyle = style
                    SettingsManager.setSoftwareKeyboardLayoutStyle(context, style)
                },
                icon = Icons.Filled.GridOn,
                linkId = "on_screen.layout_style"
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.software_keyboard_number_row_title),
                icon = Icons.Filled.Pin,
                linkId = "on_screen.number_row",
                description = stringResource(R.string.software_keyboard_number_row_description),
                checked = numberRowEnabled,
                onCheckedChange = { enabled ->
                    numberRowEnabled = enabled
                    SettingsManager.setSoftwareKeyboardNumberRowEnabled(context, enabled)
                }
            )

            Text(
                text = stringResource(R.string.software_keyboard_modifier_keys_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            )

            SoftwareKeyboardModifierSelection(
                title = stringResource(R.string.software_keyboard_left_modifier_key_title),
                linkId = "on_screen.left_modifier",
                selected = leftModifierKey,
                onSelected = { selected ->
                    leftModifierKey = selected
                    SettingsManager.setSoftwareKeyboardLeftModifierKey(context, selected)
                }
            )
            SoftwareKeyboardModifierSelection(
                title = stringResource(R.string.software_keyboard_right_modifier_key_title),
                linkId = "on_screen.right_modifier",
                selected = rightModifierKey,
                onSelected = { selected ->
                    rightModifierKey = selected
                    SettingsManager.setSoftwareKeyboardRightModifierKey(context, selected)
                }
            )

            VirtualKeyboardSectionTitle(stringResource(R.string.on_screen_keyboard_section_touch))

            ModifierTapLatchRow(
                title = stringResource(R.string.software_keyboard_nearest_key_touch_title),
                icon = Icons.Filled.Adjust,
                linkId = "on_screen.nearest_key_touch",
                description = stringResource(R.string.software_keyboard_nearest_key_touch_description),
                checked = nearestKeyTouchEnabled,
                onCheckedChange = { enabled ->
                    nearestKeyTouchEnabled = enabled
                    SettingsManager.setSoftwareKeyboardNearestKeyTouchEnabled(context, enabled)
                }
            )

            ModifierTapLatchRow(
                title = stringResource(R.string.software_keyboard_long_press_layer_popup_title),
                icon = Icons.Filled.PictureInPictureAlt,
                linkId = "on_screen.layer_popup",
                description = stringResource(R.string.software_keyboard_long_press_layer_popup_description),
                checked = longPressLayerPopupEnabled,
                onCheckedChange = { enabled ->
                    longPressLayerPopupEnabled = enabled
                    SettingsManager.setSoftwareKeyboardLongPressLayerPopupEnabled(context, enabled)
                }
            )

            if (longPressLayerPopupEnabled) {
                ModifierTapLatchRow(
                    title = stringResource(R.string.software_keyboard_long_press_layer_popup_below_key_title),
                    linkId = "on_screen.layer_popup_below",
                    description = stringResource(R.string.software_keyboard_long_press_layer_popup_below_key_description),
                    checked = longPressLayerPopupBelowKey,
                    indent = true,
                    onCheckedChange = { enabled ->
                        longPressLayerPopupBelowKey = enabled
                        SettingsManager.setSoftwareKeyboardLongPressLayerPopupBelowKey(context, enabled)
                    }
                )
            }

            VirtualKeyboardSectionTitle(stringResource(R.string.on_screen_keyboard_section_appearance))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .settingRow("on_screen.theme", onOpenKeyboardTheme)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    it.palsoftware.pastiera.ui.SettingsRowIcon(Icons.Filled.Palette)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.software_keyboard_theme_link_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.software_keyboard_theme_link_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyboardSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** One bottom-row modifier key choice, a sub-row under the "Bottom-row modifier keys" label. */
@Composable
private fun SoftwareKeyboardModifierSelection(
    title: String,
    linkId: String? = null,
    selected: SettingsManager.SoftwareKeyboardModifierKey,
    onSelected: (SettingsManager.SoftwareKeyboardModifierKey) -> Unit
) {
    SettingsDropdownRow(
        title = title,
        value = softwareKeyboardModifierKeyLabel(selected),
        options = SettingsManager.SoftwareKeyboardModifierKey.values().toList(),
        selected = selected,
        optionLabel = { softwareKeyboardModifierKeyLabel(it) },
        onSelect = onSelected,
        linkId = linkId,
        subRow = true
    )
}

@Composable
private fun softwareKeyboardModifierKeyLabel(
    modifierKey: SettingsManager.SoftwareKeyboardModifierKey
): String {
    return when (modifierKey) {
        SettingsManager.SoftwareKeyboardModifierKey.CTRL ->
            stringResource(R.string.software_keyboard_modifier_key_ctrl)
        SettingsManager.SoftwareKeyboardModifierKey.ALT ->
            stringResource(R.string.software_keyboard_modifier_key_alt)
    }
}

@Composable
private fun ModifierTapLatchRow(
    title: String,
    icon: ImageVector? = null,
    linkId: String? = null,
    description: String,
    checked: Boolean,
    indent: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.settingRow(linkId)
            .fillMaxWidth()
            .height(74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (indent) 52.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // A sub-row keeps an empty icon slot so its title sits one step in.
            it.palsoftware.pastiera.ui.SettingsRowIcon(if (indent) null else icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
