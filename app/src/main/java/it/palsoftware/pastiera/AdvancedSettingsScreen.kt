package it.palsoftware.pastiera

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.ViewAgenda
import it.palsoftware.pastiera.ui.SettingsDropdownRow
import it.palsoftware.pastiera.ui.SettingsRowIcon
import it.palsoftware.pastiera.ui.SettingsSliderRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.backup.BackupManager
import it.palsoftware.pastiera.backup.RestoreManager
import it.palsoftware.pastiera.backup.RestoreInspectionResult
import androidx.compose.material3.Surface
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Advanced settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs = remember { SettingsManager.getPreferences(context) }

    // Store the actual value (3 to 25), but display it inverted in the slider (25 to 3)
    var swipeIncrementalThreshold by remember {
        mutableStateOf(SettingsManager.getSwipeIncrementalThreshold(context))
    }
    var clipboardRetentionTime by remember {
        mutableStateOf(SettingsManager.getClipboardRetentionTime(context).toString())
    }
    var experimentalCandidatesViewEnabled by remember {
        mutableStateOf(SettingsManager.getExperimentalCandidatesViewEnabled(context))
    }
    var pendingDeviceChangeRestore by remember {
        mutableStateOf<Pair<Uri, RestoreManager.DeviceChange>?>(null)
    }
    val highlightedSettingId = LocalSettingHighlightId.current
    // Listen to SharedPreferences changes to update UI when values are restored
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "swipe_incremental_threshold" -> {
                    swipeIncrementalThreshold = SettingsManager.getSwipeIncrementalThreshold(context)
                }
                "clipboard_retention_time" -> {
                    clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
                }
                "experimental_candidates_view_enabled" -> {
                    experimentalCandidatesViewEnabled = SettingsManager.getExperimentalCandidatesViewEnabled(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun navigateBack() { context.settingsActivity().finish() }

    fun defaultBackupName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        return "pastiera-backup-${formatter.format(Date())}.zip"
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = BackupManager.createBackup(context, uri)
                val message = when (result) {
                    is it.palsoftware.pastiera.backup.BackupResult.Success ->
                        context.getString(R.string.backup_completed)
                    is it.palsoftware.pastiera.backup.BackupResult.Failure ->
                        context.getString(R.string.backup_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    fun performRestore(uri: Uri, importMode: RestoreManager.ImportMode) {
        scope.launch {
            val result = RestoreManager.restore(context, uri, importMode)
            val message = when (result) {
                is it.palsoftware.pastiera.backup.RestoreResult.Success ->
                    context.getString(R.string.restore_completed)
                is it.palsoftware.pastiera.backup.RestoreResult.Failure ->
                    context.getString(R.string.restore_failed, result.reason)
            }
            snackbarHostState.showSnackbar(message)

            // Wait a bit for SharedPreferences to be written (apply() is asynchronous)
            kotlinx.coroutines.delay(100)

            // Explicitly reload values after restore to ensure UI is updated
            swipeIncrementalThreshold = SettingsManager.getSwipeIncrementalThreshold(context)
            clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val inspection = RestoreManager.inspect(context, uri)) {
                    is RestoreInspectionResult.Success -> {
                        val deviceChange = inspection.deviceChange
                        if (deviceChange == null) {
                            performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                        } else {
                            pendingDeviceChangeRestore = uri to deviceChange
                        }
                    }
                    is RestoreInspectionResult.Failure -> {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.restore_failed, inspection.reason)
                        )
                    }
                }
            }
        }
    }

    pendingDeviceChangeRestore?.let { (uri, deviceChange) ->
        AlertDialog(
            onDismissRequest = { pendingDeviceChangeRestore = null },
            title = { Text(stringResource(R.string.restore_device_change_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_device_change_message,
                        deviceChange.source.displayName,
                        deviceChange.target.displayName
                    )
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            pendingDeviceChangeRestore = null
                            performRestore(uri, RestoreManager.ImportMode.ADAPT_TO_CURRENT_DEVICE)
                        }
                    ) {
                        Text(stringResource(R.string.restore_device_change_adapt))
                    }
                    TextButton(
                        onClick = {
                            pendingDeviceChangeRestore = null
                            performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                        }
                    ) {
                        Text(stringResource(R.string.restore_device_change_unchanged))
                    }
                    TextButton(onClick = { pendingDeviceChangeRestore = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
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
                        IconButton(onClick = { navigateBack() }) {
                            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_content_description)
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_category_advanced),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Backup
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .settingRow(SettingLinkIds.ADVANCED_BACKUP) {
                            backupLauncher.launch(defaultBackupName())
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_now),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.backup_now_description),
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

                // Restore
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .settingRow(SettingLinkIds.ADVANCED_RESTORE) {
                            restoreLauncher.launch(arrayOf("application/zip"))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.restore_from_file),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.restore_from_file_description),
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

                // Cursor speed (swipe incremental threshold)
                SettingsSliderRow(
                    icon = Icons.Filled.Swipe,
                    title = stringResource(R.string.swipe_incremental_threshold_title),
                    value = null,
                    description = stringResource(R.string.swipe_incremental_threshold_description),
                    modifier = Modifier.settingRow(SettingLinkIds.ADVANCED_SWIPE_INCREMENTAL_THRESHOLD)
                ) {
                    // The slider is inverted: right = shorter swipe per character = faster.
                    Slider(
                        value = SettingsManager.getMaxSwipeIncrementalThreshold() +
                            SettingsManager.getMinSwipeIncrementalThreshold() - swipeIncrementalThreshold,
                        onValueChange = { newInvertedValue ->
                            // Invert the slider value (25 to 3) back to stored value (3 to 25)
                            val actualValue = SettingsManager.getMaxSwipeIncrementalThreshold() +
                                SettingsManager.getMinSwipeIncrementalThreshold() - newInvertedValue
                            swipeIncrementalThreshold = actualValue
                            SettingsManager.setSwipeIncrementalThreshold(context, actualValue)
                        },
                        valueRange = SettingsManager.getMinSwipeIncrementalThreshold()..SettingsManager.getMaxSwipeIncrementalThreshold(),
                        steps = 16,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.swipe_cursor_speed_slower),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.swipe_cursor_speed_faster),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Clipboard Retention Time: a few fixed choices. A saved value that is not
                // one of them (set by an older version or a restore) is listed too, so it is kept.
                val retentionMinutes = clipboardRetentionTime.toLongOrNull()
                    ?: SettingsManager.getClipboardRetentionTime(context)
                val retentionChoices = (CLIPBOARD_RETENTION_CHOICES_MINUTES + retentionMinutes).distinct().sorted()
                SettingsDropdownRow(
                    title = stringResource(R.string.clipboard_retention_time_title),
                    value = clipboardRetentionLabel(retentionMinutes),
                    description = stringResource(R.string.clipboard_retention_choice_description),
                    options = retentionChoices,
                    selected = retentionMinutes,
                    optionLabel = { clipboardRetentionLabel(it) },
                    onSelect = { minutes ->
                        clipboardRetentionTime = minutes.toString()
                        SettingsManager.setClipboardRetentionTime(context, minutes)
                    },
                    icon = Icons.Filled.ContentPaste,
                    linkId = SettingLinkIds.ADVANCED_CLIPBOARD_RETENTION_TIME
                )

                if (SettingsManager.getExperimentalCandidatesViewEnabled(context)) { // Numen only offers turning this off, since this mode cannot hide the bar.
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingRow(SettingLinkIds.ADVANCED_EXPERIMENTAL_CANDIDATES_VIEW)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsRowIcon(Icons.Filled.ViewAgenda)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.experimental_candidates_view_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.experimental_candidates_view_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = experimentalCandidatesViewEnabled,
                                onCheckedChange = { enabled ->
                                    experimentalCandidatesViewEnabled = enabled
                                    SettingsManager.setExperimentalCandidatesViewEnabled(context, enabled)
                                }
                            )
                        }
                    }
                }

                // Diagnostics
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .settingRow(SettingLinkIds.ADVANCED_DIAGNOSTICS) {
                            context.startActivity(Intent(context, DiagnosticsActivity::class.java))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_diagnostics_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.home_diagnostics_description),
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

                // Show tutorial
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .settingRow(SettingLinkIds.ADVANCED_SHOW_TUTORIAL) {
                            SettingsManager.resetTutorialCompleted(context)
                            val intent = Intent(context, TutorialActivity::class.java)
                            context.startActivity(intent)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.tutorial_show),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.tutorial_review_description),
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
        }
}

/** Clipboard retention choices in minutes; 0 keeps copied items until cleared by hand. */
private val CLIPBOARD_RETENTION_CHOICES_MINUTES = listOf(0L, 15L, 60L, 120L, 1440L)

@Composable
private fun clipboardRetentionLabel(minutes: Long): String = when (minutes) {
    0L -> stringResource(R.string.clipboard_retention_never)
    15L -> stringResource(R.string.clipboard_retention_15_minutes)
    60L -> stringResource(R.string.clipboard_retention_1_hour)
    120L -> stringResource(R.string.clipboard_retention_2_hours)
    1440L -> stringResource(R.string.clipboard_retention_1_day)
    else -> stringResource(R.string.clipboard_retention_custom_minutes, minutes.toInt())
}
