package it.palsoftware.pastiera

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.ui.SettingsDropdownMenuItem
import it.palsoftware.pastiera.ui.SettingsDropdownRow
import it.palsoftware.pastiera.ui.SettingsDropdownRowLayout
import it.palsoftware.pastiera.ui.SettingsRowIcon
import it.palsoftware.pastiera.ui.SettingsSliderRow
import it.palsoftware.pastiera.ui.SettingsSubRowTextInset

@Composable
fun TypingSoundSettingsRow() {
    val context = LocalContext.current
    var typingSoundMode by remember {
        mutableStateOf(SettingsManager.getTypingSoundMode(context))
    }
    var typingSoundOutputMode by remember {
        mutableStateOf(SettingsManager.getTypingSoundOutputMode(context))
    }
    var customTypingSoundName by remember {
        mutableStateOf(SettingsManager.getTypingSoundCustomDisplayName(context))
    }
    var tapHapticUseSystem by remember {
        mutableStateOf(SettingsManager.getTapHapticUseSystem(context))
    }
    var tapHapticDurationMs by remember {
        mutableStateOf(SettingsManager.getTapHapticDurationMs(context).toFloat())
    }
    var showTypingSoundMenu by remember { mutableStateOf(false) }

    val typingSoundImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val imported = SettingsManager.importTypingSound(context, uri)
            if (imported) {
                typingSoundMode = SettingsManager.getTypingSoundMode(context)
                customTypingSoundName = SettingsManager.getTypingSoundCustomDisplayName(context)
                Toast.makeText(context, R.string.typing_sound_import_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.typing_sound_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsDropdownRowLayout(
        title = stringResource(R.string.typing_sound_title),
        value = typingSoundModeLabel(typingSoundMode, customTypingSoundName),
        expanded = showTypingSoundMenu,
        onExpandedChange = { showTypingSoundMenu = it },
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        linkId = "sounds.typing_mode"
    ) {
        listOf(
            SettingsManager.TYPING_SOUND_MODE_OFF to R.string.typing_sound_off,
            SettingsManager.TYPING_SOUND_MODE_CLICK to R.string.typing_sound_click,
            SettingsManager.TYPING_SOUND_MODE_TYPEWRITER to R.string.typing_sound_typewriter
        ).forEach { (mode, labelRes) ->
            SettingsDropdownMenuItem(
                text = stringResource(labelRes),
                selected = typingSoundMode == mode,
                onClick = {
                    typingSoundMode = mode
                    SettingsManager.setTypingSoundMode(context, typingSoundMode)
                    showTypingSoundMenu = false
                }
            )
        }
        // Custom asks for a sound pack first; the mode changes only once one is imported.
        SettingsDropdownMenuItem(
            text = stringResource(R.string.typing_sound_custom),
            selected = typingSoundMode == SettingsManager.TYPING_SOUND_MODE_CUSTOM,
            onClick = {
                showTypingSoundMenu = false
                typingSoundImportLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                    )
                )
            }
        )
    }

    // The volume choice only matters while typing sounds are on.
    if (typingSoundMode != SettingsManager.TYPING_SOUND_MODE_OFF) {
        // The option labels say which volumes follow silent mode, so one line is enough.
        SettingsDropdownRow(
            title = stringResource(R.string.typing_sound_output_title),
            value = typingSoundOutputModeLabel(typingSoundOutputMode),
            options = listOf(
                SettingsManager.TYPING_SOUND_OUTPUT_MEDIA,
                SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM,
                SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION
            ),
            selected = typingSoundOutputMode,
            optionLabel = { typingSoundOutputModeLabel(it) },
            onSelect = { mode ->
                typingSoundOutputMode = mode
                SettingsManager.setTypingSoundOutputMode(context, mode)
            },
            icon = Icons.Filled.Equalizer,
            linkId = "sounds.output"
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth().settingRow("sounds.system_haptics")
            .heightIn(min = 72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsRowIcon(Icons.Filled.Vibration)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tap_haptic_system_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.tap_haptic_system_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = tapHapticUseSystem,
                onCheckedChange = { enabled ->
                    tapHapticUseSystem = enabled
                    SettingsManager.setTapHapticUseSystem(context, enabled)
                }
            )
        }
    }

    if (!tapHapticUseSystem) {
        // Sub-row of the switch above; grows with the font size instead of a fixed height.
        SettingsSliderRow(
            icon = null,
            title = stringResource(R.string.tap_haptic_duration_label),
            value = stringResource(R.string.settings_value_milliseconds, tapHapticDurationMs.toInt()),
            description = stringResource(R.string.tap_haptic_duration_description),
            subRow = true,
            modifier = Modifier.settingRow("sounds.haptic_duration")
        ) {
            Slider(
                value = tapHapticDurationMs,
                onValueChange = { value ->
                    val rounded = (value / 5f).toInt() * 5L
                    if (rounded == tapHapticDurationMs.toLong()) {
                        return@Slider
                    }
                    tapHapticDurationMs = rounded.toFloat()
                    SettingsManager.setTapHapticDurationMs(context, rounded)
                    NotificationHelper.triggerFixedDurationHapticFeedback(context, rounded)
                },
                valueRange = SettingsManager.getMinTapHapticDurationMs().toFloat()..
                    SettingsManager.getMaxTapHapticDurationMs().toFloat(),
                steps = 14
            )
        }
    }
}

@Composable
private fun typingSoundModeLabel(mode: String, customName: String?): String {
    return when (mode) {
        SettingsManager.TYPING_SOUND_MODE_CLICK -> stringResource(R.string.typing_sound_click)
        SettingsManager.TYPING_SOUND_MODE_TYPEWRITER -> stringResource(R.string.typing_sound_typewriter)
        SettingsManager.TYPING_SOUND_MODE_CUSTOM -> customName ?: stringResource(R.string.typing_sound_custom)
        else -> stringResource(R.string.typing_sound_off)
    }
}

@Composable
private fun typingSoundOutputModeLabel(mode: String): String {
    return when (mode) {
        SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM -> stringResource(R.string.typing_sound_output_system)
        SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION -> stringResource(R.string.typing_sound_output_notification)
        else -> stringResource(R.string.typing_sound_output_media)
    }
}
