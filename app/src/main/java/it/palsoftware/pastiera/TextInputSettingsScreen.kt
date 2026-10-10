package it.palsoftware.pastiera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.KeyboardBackspace
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Title
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import it.palsoftware.pastiera.ui.SettingsDropdownRow
import it.palsoftware.pastiera.ui.SettingsRowIcon
import it.palsoftware.pastiera.ui.SettingsRowStartInset
import it.palsoftware.pastiera.ui.SettingsSubRowStartInset
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
import it.palsoftware.pastiera.core.Punctuation

/**
 * Text Input settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showTextExpansion by remember { mutableStateOf(settingsChild(context, "text") == "expansion") }
    val linkedSetting = LocalSettingHighlightId.current
    LaunchedEffect(linkedSetting) {
        if (linkedSetting != null) {
            showTextExpansion = linkedSetting.startsWith("text_expansion.")
        }
    }

    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }

    var autoCapitalizeAfterPeriod by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeAfterPeriod(context))
    }

    var autoCapitalizeRespectManualShiftOff by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeRespectManualShiftOff(context))
    }

    var autoCapitalizeRestrictedFields by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeRestrictedFields(context))
    }

    var doubleSpaceToPeriod by remember {
        mutableStateOf(SettingsManager.getDoubleSpaceToPeriod(context))
    }

    var spacedHyphenToEnDash by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenToEnDash(context))
    }

    var spacedHyphenDashStyle by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenDashStyle(context))
    }


    var midWordQuoteToApostrophe by remember {
        mutableStateOf(SettingsManager.getMidWordQuoteToApostrophe(context))
    }

    var commaSpace by remember {
        mutableStateOf(SettingsManager.getCommaSpace(context))
    }

    var autoSpacePunctuation by remember {
        mutableStateOf(SettingsManager.getAutoSpacePunctuation(context))
    }
    var spaceAfterPunctuation by remember {
        mutableStateOf(SettingsManager.getSpaceAfterPunctuation(context))
    }

    var autoSpacePunctuationDialogVisible by remember { mutableStateOf(false) }
    var autoSpacePunctuationHelpVisible by remember { mutableStateOf(false) }

    var smartQuotes by remember {
        mutableStateOf(SettingsManager.getSmartQuotes(context))
    }


    var shiftBackspaceDelete by remember {
        mutableStateOf(SettingsManager.getShiftBackspaceDelete(context))
    }

    var altBackspaceDelete by remember {
        mutableStateOf(SettingsManager.getAltBackspaceDelete(context))
    }

    var backspaceAtStartDelete by remember {
        mutableStateOf(SettingsManager.getBackspaceAtStartDelete(context))
    }

    // Handle system back button


    if (showTextExpansion) {
        TextExpansionSettingsScreen(onBack = { context.settingsActivity().finish() })
        return
    }

    if (autoSpacePunctuationDialogVisible) {
        AlertDialog(
            onDismissRequest = { autoSpacePunctuationDialogVisible = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.auto_space_punctuation_title),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { autoSpacePunctuationHelpVisible = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.auto_space_punctuation_help_action)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.auto_space_punctuation_dialog_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auto_space_punctuation_character_column),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.auto_space_punctuation_before_column),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = stringResource(R.string.auto_space_punctuation_after_column),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(72.dp)
                        )
                    }
                    Column {
                        autoSpacePunctuationOptions().forEach { punctuation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = autoSpacePunctuationLabel(punctuation),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = punctuation in autoSpacePunctuation,
                                    onCheckedChange = { checked ->
                                        autoSpacePunctuation = if (checked) {
                                            addAutoSpacePunctuation(autoSpacePunctuation, punctuation)
                                        } else {
                                            autoSpacePunctuation.filterNot { it == punctuation }
                                        }
                                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                                    },
                                    modifier = Modifier.width(72.dp)
                                )
                                Checkbox(
                                    checked = punctuation in spaceAfterPunctuation,
                                    onCheckedChange = { checked ->
                                        spaceAfterPunctuation = if (checked) {
                                            addAutoSpacePunctuation(spaceAfterPunctuation, punctuation)
                                        } else {
                                            spaceAfterPunctuation.filterNot { it == punctuation }
                                        }
                                        SettingsManager.setSpaceAfterPunctuation(context, spaceAfterPunctuation)
                                    },
                                    modifier = Modifier.width(72.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoSpacePunctuationDialogVisible = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        autoSpacePunctuation = Punctuation.DEFAULT_AUTO_SPACE
                        spaceAfterPunctuation = ""
                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                        SettingsManager.setSpaceAfterPunctuation(context, spaceAfterPunctuation)
                    }
                ) {
                    Text(stringResource(R.string.auto_space_punctuation_reset))
                }
            }
        )
    }
    if (autoSpacePunctuationHelpVisible) {
        AlertDialog(
            onDismissRequest = { autoSpacePunctuationHelpVisible = false },
            title = { Text(stringResource(R.string.auto_space_punctuation_help_title)) },
            text = { Text(stringResource(R.string.auto_space_punctuation_help_text)) },
            confirmButton = {
                TextButton(onClick = { autoSpacePunctuationHelpVisible = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
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
                        text = stringResource(R.string.settings_category_text_input),
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
            SettingsNavigationRow(
                title = stringResource(R.string.text_expansion_title),
                icon = Icons.AutoMirrored.Filled.ShortText,
                description = stringResource(R.string.text_expansion_description),
                linkId = SettingLinkIds.TEXT_INPUT_TEXT_EXPANSION,
                onClick = { openSettingsChild(context, "text", "expansion") }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_capitalization))
            SettingsSwitchRow(
                title = stringResource(R.string.auto_capitalize_title),
                icon = Icons.Filled.Title,
                description = stringResource(R.string.auto_capitalize_description),
                checked = autoCapitalizeFirstLetter,
                linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE,
                onCheckedChange = { enabled ->
                    autoCapitalizeFirstLetter = enabled
                    SettingsManager.setAutoCapitalizeFirstLetter(context, enabled)
                }
            )
            AnimatedVisibility(visible = autoCapitalizeFirstLetter) {
                Column {
                    SettingsSwitchRow(
                                title = stringResource(R.string.auto_capitalize_respect_manual_shift_off_title),
                        icon = null,
                        description = stringResource(R.string.auto_capitalize_respect_manual_shift_off_description),
                        checked = autoCapitalizeRespectManualShiftOff,
                        inset = SettingsSubRowStartInset,
                        linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF,
                        onCheckedChange = { enabled ->
                            autoCapitalizeRespectManualShiftOff = enabled
                            SettingsManager.setAutoCapitalizeRespectManualShiftOff(context, enabled)
                        }
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.auto_capitalize_restricted_fields_title),
                        icon = null,
                        description = stringResource(R.string.auto_capitalize_restricted_fields_description),
                        checked = autoCapitalizeRestrictedFields,
                        inset = SettingsSubRowStartInset,
                        linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_RESTRICTED_FIELDS,
                        onCheckedChange = { enabled ->
                            autoCapitalizeRestrictedFields = enabled
                            SettingsManager.setAutoCapitalizeRestrictedFields(context, enabled)
                        }
                    )
                }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.auto_capitalize_after_period_title),
                icon = Icons.Filled.TextFormat,
                description = stringResource(R.string.auto_capitalize_after_period_description),
                checked = autoCapitalizeAfterPeriod,
                linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_AFTER_PERIOD,
                onCheckedChange = { enabled ->
                    autoCapitalizeAfterPeriod = enabled
                    SettingsManager.setAutoCapitalizeAfterPeriod(context, enabled)
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_spacing_punctuation))
            SettingsSwitchRow(
                title = stringResource(R.string.double_space_to_period_title),
                icon = Icons.Filled.SpaceBar,
                description = stringResource(R.string.double_space_to_period_description),
                checked = doubleSpaceToPeriod,
                linkId = SettingLinkIds.TEXT_INPUT_DOUBLE_SPACE_TO_PERIOD,
                onCheckedChange = { enabled ->
                    doubleSpaceToPeriod = enabled
                    SettingsManager.setDoubleSpaceToPeriod(context, enabled)
                }
            )
            SettingsNavigationRow(
                title = stringResource(R.string.auto_space_punctuation_title),
                icon = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                description = autoSpacePunctuationSummary(
                    beforePunctuation = autoSpacePunctuation,
                    afterPunctuation = spaceAfterPunctuation,
                    beforeLabel = stringResource(R.string.auto_space_punctuation_before_column),
                    afterLabel = stringResource(R.string.auto_space_punctuation_after_column),
                    offLabel = stringResource(R.string.auto_space_punctuation_choose_off)
                ),
                linkId = SettingLinkIds.TEXT_INPUT_AUTO_SPACE_PUNCTUATION,
                onClick = { autoSpacePunctuationDialogVisible = true }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.comma_space_title),
                icon = ImageVector.vectorResource(R.drawable.ic_comma_space_24),
                description = stringResource(R.string.comma_space_description),
                checked = commaSpace,
                linkId = SettingLinkIds.TEXT_INPUT_COMMA_SPACE,
                onCheckedChange = { enabled ->
                    commaSpace = enabled
                    SettingsManager.setCommaSpace(context, enabled)
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_typography))
            SettingsSwitchRow(
                title = stringResource(R.string.spaced_hyphen_to_en_dash_title),
                icon = Icons.AutoMirrored.Filled.TrendingFlat,
                description = stringResource(R.string.spaced_hyphen_to_en_dash_description),
                checked = spacedHyphenToEnDash,
                linkId = SettingLinkIds.TEXT_INPUT_SPACED_HYPHEN_TO_EN_DASH,
                onCheckedChange = { enabled ->
                    spacedHyphenToEnDash = enabled
                    SettingsManager.setSpacedHyphenToEnDash(context, enabled)
                }
            )
            // The dash choice only matters while the switch above is on.
            if (spacedHyphenToEnDash) {
                SettingsDropdownRow(
                    title = stringResource(R.string.spaced_hyphen_dash_style_title),
                    value = dashStyleLabel(spacedHyphenDashStyle),
                    options = dashStyleOptions(),
                    selected = spacedHyphenDashStyle,
                    optionLabel = { dashStyleLabel(it) },
                    onSelect = { style ->
                        spacedHyphenDashStyle = style
                        SettingsManager.setSpacedHyphenDashStyle(context, style)
                    },
                    subRow = true
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.mid_word_quote_to_apostrophe_title),
                icon = ImageVector.vectorResource(R.drawable.ic_apostrophe_24),
                description = stringResource(R.string.mid_word_quote_to_apostrophe_description),
                checked = midWordQuoteToApostrophe,
                linkId = SettingLinkIds.TEXT_INPUT_MID_WORD_QUOTE_TO_APOSTROPHE,
                onCheckedChange = { enabled ->
                    midWordQuoteToApostrophe = enabled
                    SettingsManager.setMidWordQuoteToApostrophe(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.smart_quotes_title),
                icon = Icons.Filled.FormatQuote,
                description = stringResource(R.string.smart_quotes_description),
                checked = smartQuotes,
                linkId = SettingLinkIds.TEXT_INPUT_SMART_QUOTES,
                onCheckedChange = { enabled ->
                    smartQuotes = enabled
                    SettingsManager.setSmartQuotes(context, enabled)
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_delete))
            SettingsSwitchRow(
                title = stringResource(R.string.shift_backspace_delete_title),
                icon = Icons.AutoMirrored.Filled.KeyboardBackspace,
                description = stringResource(R.string.shift_backspace_delete_description),
                checked = shiftBackspaceDelete,
                linkId = SettingLinkIds.TEXT_INPUT_SHIFT_BACKSPACE_DELETE,
                onCheckedChange = { enabled ->
                    shiftBackspaceDelete = enabled
                    SettingsManager.setShiftBackspaceDelete(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.alt_backspace_delete_title),
                icon = Icons.AutoMirrored.Filled.Backspace,
                description = stringResource(R.string.alt_backspace_delete_description),
                checked = altBackspaceDelete,
                linkId = SettingLinkIds.TEXT_INPUT_ALT_BACKSPACE_DELETE,
                onCheckedChange = { enabled ->
                    altBackspaceDelete = enabled
                    SettingsManager.setAltBackspaceDelete(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.backspace_at_start_delete_title),
                icon = Icons.Filled.FirstPage,
                description = stringResource(R.string.backspace_at_start_delete_description),
                checked = backspaceAtStartDelete,
                linkId = SettingLinkIds.TEXT_INPUT_BACKSPACE_AT_START_DELETE,
                onCheckedChange = { enabled ->
                    backspaceAtStartDelete = enabled
                    SettingsManager.setBackspaceAtStartDelete(context, enabled)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    it.palsoftware.pastiera.ui.SettingsSectionLabel(text)
}

@Composable
private fun SettingsRowFrame(
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 64.dp,
    inset: androidx.compose.ui.unit.Dp = SettingsRowStartInset,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    icon: ImageVector?,
    description: String? = null,
    checked: Boolean,
    inset: androidx.compose.ui.unit.Dp = SettingsRowStartInset,
    onCheckedChange: (Boolean) -> Unit,
    linkId: String? = null
) {
    SettingsRowFrame(
        modifier = Modifier.settingRow(linkId),
        inset = inset,
        minHeight = if (description == null) 64.dp else 72.dp
    ) {
        SettingsRowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    icon: ImageVector?,
    description: String,
    linkId: String? = null,
    onClick: () -> Unit
) {
    SettingsRowFrame(
        modifier = Modifier.settingRow(linkId, onClick),
        minHeight = 72.dp
    ) {
        SettingsRowIcon(icon)
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun dashStyleOptions(): List<String> {
    return listOf(
        SettingsManager.DASH_STYLE_EN,
        SettingsManager.DASH_STYLE_EM
    )
}

@Composable
private fun dashStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.DASH_STYLE_EM -> stringResource(R.string.dash_style_em)
        else -> stringResource(R.string.dash_style_en)
    }
}

internal fun autoSpacePunctuationOptions(): List<Char> {
    return Punctuation.AUTO_SPACE_CANDIDATES.toList()
}

private fun autoSpacePunctuationSummary(
    beforePunctuation: String,
    afterPunctuation: String,
    beforeLabel: String,
    afterLabel: String,
    offLabel: String
): String {
    val parts = buildList {
        beforePunctuation.takeIf { it.isNotEmpty() }?.let {
            add("$beforeLabel: ${it.map(::autoSpacePunctuationLabel).joinToString(" ")}")
        }
        afterPunctuation.takeIf { it.isNotEmpty() }?.let {
            add("$afterLabel: ${it.map(::autoSpacePunctuationLabel).joinToString(" ")}")
        }
    }
    return parts.joinToString(" · ").ifEmpty { offLabel }
}

internal fun autoSpacePunctuationLabel(punctuation: Char): String {
    return when (punctuation) {
        '\\' -> "\\"
        '"' -> "\""
        else -> punctuation.toString()
    }
}

private fun addAutoSpacePunctuation(current: String, punctuation: Char): String {
    val selected = current.toSet() + punctuation
    return Punctuation.AUTO_SPACE_CANDIDATES.filter { it in selected }
}

internal fun toggleAutoSpacePunctuation(current: String, punctuation: Char): String {
    return if (punctuation in current) {
        current.filterNot { it == punctuation }
    } else {
        addAutoSpacePunctuation(current, punctuation)
    }
}
