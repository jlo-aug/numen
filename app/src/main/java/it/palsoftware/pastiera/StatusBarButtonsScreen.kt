package it.palsoftware.pastiera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Visibility
import it.palsoftware.pastiera.ui.SettingsDropdownMenuItem
import it.palsoftware.pastiera.ui.SettingsDropdownRowLayout
import it.palsoftware.pastiera.ui.SettingsRowIcon
import it.palsoftware.pastiera.ui.SettingsSliderRow
import it.palsoftware.pastiera.ui.SettingsSubRowStartInset
import it.palsoftware.pastiera.ui.SettingsSubRowTextInset
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R

private enum class StatusBarEditorMode { Extended, Pastierina }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBarButtonsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCustomizeVariations: () -> Unit
) {
    val context = LocalContext.current
    var leftSlots by remember { mutableStateOf(SettingsManager.getStatusBarSlotsLeft(context)) }
    var rightSlots by remember { mutableStateOf(SettingsManager.getStatusBarSlotsRight(context)) }
    var pastierinaLeftSlots by remember {
        mutableStateOf(SettingsManager.getPastierinaStatusBarSlotsLeft(context))
    }
    var pastierinaRightSlots by remember {
        mutableStateOf(SettingsManager.getPastierinaStatusBarSlotsRight(context))
    }
    var variationsVisible by remember {
        mutableStateOf(SettingsManager.areStatusBarVariationsEnabled(context))
    }
    var dynamicVariationSlotCount by remember {
        mutableStateOf(SettingsManager.getDynamicVariationBarSlotCount(context))
    }
    var dynamicVariationsResizeToContent by remember {
        mutableStateOf(SettingsManager.getDynamicVariationBarResizeToContent(context))
    }
    var titan2EliteRoundedCornerInsetsEnabled by remember {
        mutableStateOf(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
    }
    var titan2EliteFillBottomCorners by remember {
        mutableStateOf(SettingsManager.getTitan2EliteFillBottomCorners(context))
    }
    var topCornerMultiplier by remember {
        mutableStateOf(SettingsManager.getTitan2EliteTopCornerMultiplier(context))
    }
    var maxIconShrink by remember { mutableStateOf(SettingsManager.getTitan2EliteMaxIconShrink(context)) }
    var autoShowKeyboard by remember { mutableStateOf(SettingsManager.getAutoShowKeyboard(context)) }
    var editorMode by remember {
        mutableStateOf(
            if (
                SettingsManager.getStatusBarPresentationMode(context) ==
                    SettingsManager.StatusBarPresentationMode.PASTIERINA
            ) {
                StatusBarEditorMode.Pastierina
            } else {
                StatusBarEditorMode.Extended
            }
        )
    }

    fun selectExtendedButton(buttonId: String, targetSide: String, targetIndex: Int) {
        if (buttonId != SettingsManager.STATUS_BAR_BUTTON_NONE) {
            leftSlots = leftSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "left" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
            rightSlots = rightSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "right" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
        }
        if (targetSide == "left") {
            leftSlots = leftSlots.toMutableList().also { it[targetIndex] = buttonId }
        } else {
            rightSlots = rightSlots.toMutableList().also { it[targetIndex] = buttonId }
        }
        SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
        SettingsManager.setStatusBarSlotsRight(context, rightSlots)
    }

    fun selectPastierinaButton(buttonId: String, targetSide: String, targetIndex: Int) {
        if (buttonId != SettingsManager.STATUS_BAR_BUTTON_NONE) {
            pastierinaLeftSlots = pastierinaLeftSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "left" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
            pastierinaRightSlots = pastierinaRightSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "right" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
        }
        if (targetSide == "left") {
            pastierinaLeftSlots = pastierinaLeftSlots.toMutableList().also { it[targetIndex] = buttonId }
        } else {
            pastierinaRightSlots = pastierinaRightSlots.toMutableList().also { it[targetIndex] = buttonId }
        }
        SettingsManager.setPastierinaStatusBarSlotsLeft(context, pastierinaLeftSlots)
        SettingsManager.setPastierinaStatusBarSlotsRight(context, pastierinaRightSlots)
    }

    // The title bar stays put; only the settings scroll.
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
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
                    text = stringResource(R.string.status_bar_buttons_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                // Resets only the bar's buttons and variation layout, so it is not styled as destructive.
                TextButton(
                    onClick = {
                        val defaults = SettingsManager.resetStatusBarSlotsToDefault(context)
                        leftSlots = listOf(defaults.left)
                        rightSlots = listOf(defaults.right1, defaults.right2)
                        SettingsManager.resetPastierinaStatusBarSlotsToDefault(context)
                        pastierinaLeftSlots = SettingsManager.getPastierinaStatusBarSlotsLeft(context)
                        pastierinaRightSlots = SettingsManager.getPastierinaStatusBarSlotsRight(context)
                        variationsVisible = SettingsManager.areStatusBarVariationsEnabled(context)
                        dynamicVariationSlotCount = SettingsManager.getDynamicVariationBarSlotCount(context)
                        dynamicVariationsResizeToContent = SettingsManager.getDynamicVariationBarResizeToContent(context)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.status_bar_buttons_reset))
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

        // Moved here from the former Keyboard & Devices screen.
        Surface(modifier = Modifier.fillMaxWidth().settingRow(SettingLinkIds.TEXT_INPUT_AUTO_SHOW_KEYBOARD), color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsRowIcon(Icons.Filled.Visibility)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_show_keyboard_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.auto_show_keyboard_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoShowKeyboard,
                    onCheckedChange = { enabled ->
                        autoShowKeyboard = enabled
                        SettingsManager.setAutoShowKeyboard(context, enabled)
                    }
                )
            }
        }

        SettingsSectionDivider(stringResource(R.string.status_bar_style_section))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth().settingRow("status_bar.presentation")
                .padding(start = 52.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            StatusBarEditorMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = editorMode == mode,
                    onClick = {
                        editorMode = mode
                        SettingsManager.setStatusBarPresentationMode(
                            context,
                            if (mode == StatusBarEditorMode.Pastierina) {
                                SettingsManager.StatusBarPresentationMode.PASTIERINA
                            } else {
                                SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR
                            }
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, StatusBarEditorMode.entries.size)
                ) {
                    Text(
                        text = stringResource(
                            if (mode == StatusBarEditorMode.Extended) {
                                R.string.extended_status_bar_title
                            } else {
                                R.string.pastierina_status_bar_buttons_title
                            }
                        )
                    )
                }
            }
        }

        StatusBarLayoutPreview(
            leftSlots = if (editorMode == StatusBarEditorMode.Extended) leftSlots else pastierinaLeftSlots,
            rightSlots = if (editorMode == StatusBarEditorMode.Extended) rightSlots else pastierinaRightSlots,
            centerText = if (editorMode == StatusBarEditorMode.Extended && variationsVisible) {
                "· · ·"
            } else if (editorMode == StatusBarEditorMode.Pastierina) {
                stringResource(R.string.pastierina_preview_suggestions)
            } else null
        )
        if (editorMode == StatusBarEditorMode.Extended) {
            // Bar gesture, shown with the bar itself rather than with variations.
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.status_bar_swipe_cursor_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Titan 2 Elite display corners: Numen shows them only on an Elite or if already enabled.
        val showEliteCorners = remember {
            it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2EliteDevice() ||
                SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        }
        if (showEliteCorners) {
            SettingsSectionDivider(stringResource(R.string.device_specific_interface_section))
            Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.rounded_corners"), color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsRowIcon(Icons.Filled.RoundedCorner)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.titan2_elite_rounded_corners_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.titan2_elite_rounded_corners_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FeatureStatusIcon(FeatureStatus.Construction)
                    Switch(
                        checked = titan2EliteRoundedCornerInsetsEnabled,
                        onCheckedChange = { enabled ->
                            titan2EliteRoundedCornerInsetsEnabled = enabled
                            SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(context, enabled)
                        }
                    )
                }
            }

            if (titan2EliteRoundedCornerInsetsEnabled) {
                Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.fill_bottom_corners"), color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = SettingsSubRowStartInset, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsRowIcon(null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.titan2_elite_fill_bottom_corners_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.titan2_elite_fill_bottom_corners_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = titan2EliteFillBottomCorners,
                            onCheckedChange = { enabled ->
                                titan2EliteFillBottomCorners = enabled
                                SettingsManager.setTitan2EliteFillBottomCorners(context, enabled)
                            }
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = SettingsSubRowTextInset, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
                    Text(stringResource(R.string.titan2_elite_top_corner_title), modifier = Modifier.fillMaxWidth().settingRow("status_bar.top_corner"), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.titan2_elite_top_corner_description),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4, 6).forEach { multiplier ->
                            FilterChip(
                                selected = topCornerMultiplier == multiplier,
                                onClick = {
                                    topCornerMultiplier = multiplier
                                    SettingsManager.setTitan2EliteTopCornerMultiplier(context, multiplier)
                                },
                                label = { Text("${multiplier}×") }
                            )
                        }
                    }
                    Text(stringResource(R.string.titan2_elite_max_icon_shrink_title, maxIconShrink),
                        modifier = Modifier.fillMaxWidth().settingRow("status_bar.max_icon_shrink"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.titan2_elite_max_icon_shrink_description),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = maxIconShrink.toFloat(), valueRange = 0f..90f, steps = 8,
                        onValueChange = { maxIconShrink = (it / 10f).toInt() * 10 },
                        onValueChangeFinished = { SettingsManager.setTitan2EliteMaxIconShrink(context, maxIconShrink) })
                }
            }
        }

        if (editorMode == StatusBarEditorMode.Extended) {
            SettingsSectionDivider(stringResource(R.string.extended_status_bar_features_section))
            Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.variations_visible"), color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsRowIcon(Icons.Filled.ViewWeek)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.status_bar_variations_visible_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.status_bar_variations_visible_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = variationsVisible,
                        onCheckedChange = { visible ->
                            variationsVisible = visible
                            SettingsManager.setStatusBarVariationsEnabled(context, visible)
                        }
                    )
                }
            }

            if (variationsVisible) {
                SettingsSliderRow(
                    icon = null,
                    title = stringResource(R.string.dynamic_variation_slot_count_title),
                    value = stringResource(
                        R.string.dynamic_variation_slot_count_description,
                        dynamicVariationSlotCount
                    ),
                    subRow = true,
                    modifier = Modifier.settingRow("status_bar.variation_slots")
                ) {
                    Slider(
                        value = dynamicVariationSlotCount.toFloat(),
                        onValueChange = { value ->
                            dynamicVariationSlotCount = value.toInt().coerceIn(
                                SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
                                SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT
                            )
                        },
                        onValueChangeFinished = {
                            SettingsManager.setDynamicVariationBarSlotCount(context, dynamicVariationSlotCount)
                        },
                        valueRange = SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT.toFloat()..
                            SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT.toFloat(),
                        steps = SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT -
                            SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT - 1
                    )
                }

                Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.resize_variations"), color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = SettingsSubRowStartInset, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsRowIcon(null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dynamic_variation_resize_to_content_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.dynamic_variation_resize_to_content_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicVariationsResizeToContent,
                            onCheckedChange = { enabled ->
                                dynamicVariationsResizeToContent = enabled
                                SettingsManager.setDynamicVariationBarResizeToContent(context, enabled)
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().settingRow("customization.variations", onCustomizeVariations),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = SettingsSubRowStartInset, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsRowIcon(null)
                        Text(
                            text = stringResource(R.string.variation_customize_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSectionDivider(stringResource(R.string.status_bar_buttons_section))
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_left),
                icon = Icons.AutoMirrored.Filled.AlignHorizontalLeft,
                linkId = "status_bar.extended_left",
                slots = leftSlots,
                onSlotSelected = { index, buttonId -> selectExtendedButton(buttonId, "left", index) },
                onAddSlot = {
                    leftSlots = leftSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
                },
                onRemoveSlot = { index ->
                    leftSlots = leftSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
                }
            )
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_right),
                icon = Icons.AutoMirrored.Filled.AlignHorizontalRight,
                linkId = "status_bar.extended_right",
                slots = rightSlots,
                onSlotSelected = { index, buttonId -> selectExtendedButton(buttonId, "right", index) },
                onAddSlot = {
                    rightSlots = rightSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setStatusBarSlotsRight(context, rightSlots)
                },
                onRemoveSlot = { index ->
                    rightSlots = rightSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setStatusBarSlotsRight(context, rightSlots)
                }
            )
        } else {
            SettingsSectionDivider(stringResource(R.string.pastierina_status_bar_buttons_title))
            Text(
                text = stringResource(R.string.pastierina_status_bar_buttons_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            SettingsSectionDivider(stringResource(R.string.status_bar_buttons_section))
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_left),
                icon = Icons.AutoMirrored.Filled.AlignHorizontalLeft,
                linkId = "status_bar.pastierina_left",
                slots = pastierinaLeftSlots,
                onSlotSelected = { index, buttonId -> selectPastierinaButton(buttonId, "left", index) },
                onAddSlot = {
                    pastierinaLeftSlots = pastierinaLeftSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setPastierinaStatusBarSlotsLeft(context, pastierinaLeftSlots)
                },
                onRemoveSlot = { index ->
                    pastierinaLeftSlots = pastierinaLeftSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setPastierinaStatusBarSlotsLeft(context, pastierinaLeftSlots)
                }
            )
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_right),
                icon = Icons.AutoMirrored.Filled.AlignHorizontalRight,
                linkId = "status_bar.pastierina_right",
                slots = pastierinaRightSlots,
                onSlotSelected = { index, buttonId -> selectPastierinaButton(buttonId, "right", index) },
                onAddSlot = {
                    pastierinaRightSlots = pastierinaRightSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setPastierinaStatusBarSlotsRight(context, pastierinaRightSlots)
                },
                onRemoveSlot = { index ->
                    pastierinaRightSlots = pastierinaRightSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setPastierinaStatusBarSlotsRight(context, pastierinaRightSlots)
                }
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}
@Composable
private fun StatusBarLayoutPreview(
    leftSlots: List<String>,
    rightSlots: List<String>,
    centerText: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                leftSlots.forEach { SlotPreview(buttonId = it, label = "") }
            }
            if (centerText != null) {
                Surface(
                    modifier = Modifier.weight(1f).height(32.dp).padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = centerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rightSlots.forEach { SlotPreview(buttonId = it, label = "") }
            }
        }
    }
}

@Composable
private fun SlotGroup(
    linkId: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    slots: List<String>,
    onSlotSelected: (Int, String) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsRowIcon(icon)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddSlot) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.status_bar_slot_add),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Each button of the group is a sub-row dropdown, with its remove button at the end.
            slots.forEachIndexed { index, buttonId ->
                SlotDropdown(
                    // "Button 1" only helps when the group has more than one button.
                    label = if (slots.size > 1) {
                        stringResource(R.string.status_bar_slot_button_label, index + 1)
                    } else {
                        stringResource(R.string.status_bar_slot_single_button_label)
                    },
                    selectedButton = buttonId,
                    excludedButtons = emptyList(),
                    onButtonSelected = { selected -> onSlotSelected(index, selected) },
                    trailing = {
                        IconButton(
                            onClick = { onRemoveSlot(index) },
                            enabled = slots.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.status_bar_slot_remove),
                                tint = if (slots.size > 1) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ModifierIndicatorMultiSelect(
    modifier: Modifier = Modifier,
    selectedIndicators: Set<String>,
    onIndicatorsSelected: (Set<String>) -> Unit
) {
    val options = listOf(
        SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP,
        SettingsManager.MODIFIER_INDICATOR_MENU_BAR,
        SettingsManager.MODIFIER_INDICATOR_STATUS_BAR
    )
    // Drawn like the segmented buttons used for other choices (outlined, joined, a check on
    // the selected ones); kept as plain surfaces so each part stays long-pressable as a link.
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        ) {
            options.forEachIndexed { index, indicator ->
                val selected = indicator in selectedIndicators
                val linkId = when (indicator) {
                    SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP ->
                        SettingLinkIds.MODIFIERS_INDICATOR_BOTTOM_STRIP
                    SettingsManager.MODIFIER_INDICATOR_MENU_BAR ->
                        SettingLinkIds.MODIFIERS_INDICATOR_MENU_BAR
                    SettingsManager.MODIFIER_INDICATOR_STATUS_BAR ->
                        SettingLinkIds.MODIFIERS_INDICATOR_STATUS_BAR
                    else -> null
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .fillMaxHeight()
                        // Overlap the shared borders, as SegmentedButtonRow does.
                        .offset(x = (-1).dp * index)
                        .settingRow(linkId) {
                            onIndicatorsSelected(
                                if (selected) selectedIndicators - indicator else selectedIndicators + indicator
                            )
                        },
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = getModifierIndicatorLabel(indicator),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        if (selectedIndicators.isEmpty()) {
            Text(
                text = stringResource(R.string.modifier_indicators_off_state),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun getModifierIndicatorLabel(indicator: String): String {
    return when (indicator) {
        SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP -> stringResource(R.string.modifier_indicators_bottom_strip)
        SettingsManager.MODIFIER_INDICATOR_MENU_BAR -> stringResource(R.string.modifier_indicators_menu_bar)
        SettingsManager.MODIFIER_INDICATOR_STATUS_BAR -> stringResource(R.string.modifier_indicators_status_bar)
        else -> indicator
    }
}

@Composable
private fun SlotPreview(
    buttonId: String,
    label: String
) {
    Surface(
        modifier = Modifier.size(32.dp),
        color = if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    painter = painterResource(id = getButtonIconRes(buttonId)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SlotDropdown(
    label: String,
    selectedButton: String,
    excludedButtons: List<String> = emptyList(),
    onButtonSelected: (String) -> Unit,
    trailing: @Composable RowScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Filter out buttons that are already used in other slots (but always keep "none" available)
    val availableButtons = SettingsManager.getAvailableStatusBarButtons()
        .filter { it == SettingsManager.STATUS_BAR_BUTTON_NONE || it !in excludedButtons }

    SettingsDropdownRowLayout(
        title = label,
        value = getButtonDisplayName(selectedButton),
        expanded = expanded,
        onExpandedChange = { expanded = it },
        subRow = true,
        trailing = trailing
    ) {
        availableButtons.forEach { buttonId ->
            SettingsDropdownMenuItem(
                text = getButtonDisplayName(buttonId),
                supportingText = getButtonDescription(buttonId),
                selected = buttonId == selectedButton,
                icon = {
                    if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(id = getButtonIconRes(buttonId)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = {
                    onButtonSelected(buttonId)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun getButtonDisplayName(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji)
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> stringResource(R.string.status_bar_button_hamburger)
        SettingsManager.STATUS_BAR_BUTTON_MINIMAL_UI -> stringResource(R.string.status_bar_button_minimal_ui)
        SettingsManager.STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE -> stringResource(R.string.status_bar_button_software_keyboard_mode)
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> stringResource(R.string.status_bar_button_settings)
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> stringResource(R.string.status_bar_button_symbols)
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> stringResource(R.string.status_bar_button_undo)
        SettingsManager.STATUS_BAR_BUTTON_REDO -> stringResource(R.string.status_bar_button_redo)
        else -> buttonId
    }
}

@Composable
private fun getButtonDescription(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none_description)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard_description)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone_description)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji_description)
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> stringResource(R.string.status_bar_button_hamburger_description)
        SettingsManager.STATUS_BAR_BUTTON_MINIMAL_UI -> stringResource(R.string.status_bar_button_minimal_ui_description)
        SettingsManager.STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE -> stringResource(R.string.status_bar_button_software_keyboard_mode_description)
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> stringResource(R.string.status_bar_button_settings_description)
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> stringResource(R.string.status_bar_button_symbols_description)
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> stringResource(R.string.status_bar_button_undo_description)
        SettingsManager.STATUS_BAR_BUTTON_REDO -> stringResource(R.string.status_bar_button_redo_description)
        else -> ""
    }
}

/**
 * Returns the drawable resource ID for the button icon.
 * Note: STATUS_BAR_BUTTON_NONE should be handled separately (no icon).
 */
private fun getButtonIconRes(buttonId: String): Int {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> R.drawable.ic_content_paste_24
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> R.drawable.ic_baseline_mic_24
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> R.drawable.ic_emoji_emotions_24
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> R.drawable.ic_menu_24
        SettingsManager.STATUS_BAR_BUTTON_MINIMAL_UI -> R.drawable.ic_minimal_ui_24
        SettingsManager.STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE -> R.drawable.expansion_panels_24
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> R.drawable.ic_settings_24
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> R.drawable.ic_emoji_symbols_24
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> R.drawable.ic_undo_24
        SettingsManager.STATUS_BAR_BUTTON_REDO -> R.drawable.ic_redo_24
        else -> R.drawable.ic_settings_24 // Fallback
    }
}
