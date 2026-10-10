package it.palsoftware.pastiera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val hardwareKeyboardProfiles = listOf(
    "auto" to R.string.keyboard_profile_option_auto,
    "titan" to R.string.keyboard_profile_option_titan,
    "titan2" to R.string.keyboard_profile_option_titan2,
    "titan2elite_qwerty" to R.string.keyboard_profile_option_titan2elite_qwerty
)

/**
 * Built-in keyboard settings for other devices. Reached from Keys & modifiers only while
 * one of them differs from its default; each row likewise shows only if it was changed
 * when the screen opened, so the user can set it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareKeyboardSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val showProfile = remember { HiddenKeyboardSettings.keyboardProfileChanged(context) }
    val showTitan2Layout = remember { HiddenKeyboardSettings.titan2LayoutChanged(context) }
    var selectedProfile by remember {
        mutableStateOf(SettingsManager.getPhysicalKeyboardProfileOverride(context))
    }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var titan2LayoutEnabled by remember {
        mutableStateOf(SettingsManager.isTitan2LayoutEnabled(context))
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
                        text = stringResource(R.string.hardware_keyboards_title),
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
            Text(
                text = stringResource(R.string.other_keyboards_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (showProfile) {
                val selectedProfileLabel = hardwareKeyboardProfiles
                    .firstOrNull { it.first == selectedProfile }
                    ?.let { stringResource(it.second) }
                    ?: stringResource(R.string.keyboard_profile_option_auto)
                ExposedDropdownMenuBox(
                    expanded = profileMenuExpanded,
                    onExpandedChange = { profileMenuExpanded = it },
                    modifier = Modifier.settingRow("hardware.profile").padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = selectedProfileLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.hardware_keyboard_profile_select_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false }
                    ) {
                        hardwareKeyboardProfiles.forEach { (profile, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    selectedProfile = profile
                                    SettingsManager.setPhysicalKeyboardProfileOverride(context, profile)
                                    profileMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.keyboard_profile_override_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (showTitan2Layout) {
                HardwareKeyboardSwitchRow(
                    title = stringResource(R.string.titan2_layout_title),
                    linkId = "hardware.titan2_layout",
                    description = stringResource(R.string.titan2_layout_description),
                    checked = titan2LayoutEnabled,
                    onCheckedChange = { enabled ->
                        titan2LayoutEnabled = enabled
                        SettingsManager.setTitan2LayoutEnabled(context, enabled)
                    }
                )
            }
        }
    }
}

/**
 * Chooses the symbol the currency key types (read by DeviceSymMappingRepository).
 * Shown in Keys & modifiers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencySymbolPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currencySymbol by remember {
        mutableStateOf(SettingsManager.getPhysicalKeyboardCurrencySymbol(context))
    }
    // Same segmented style as the other single-choice settings (e.g. Full bar / Compact bar);
    // no check mark, so eight symbols fit on one line.
    val symbols = SettingsManager.physicalKeyboardCurrencySymbols()
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        symbols.forEachIndexed { index, symbol ->
            SegmentedButton(
                selected = currencySymbol == symbol,
                onClick = {
                    currencySymbol = symbol
                    SettingsManager.setPhysicalKeyboardCurrencySymbol(context, symbol)
                },
                shape = SegmentedButtonDefaults.itemShape(index, symbols.size),
                icon = {}
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HardwareKeyboardSwitchRow(
    title: String,
    linkId: String? = null,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.settingRow(linkId).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
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
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
