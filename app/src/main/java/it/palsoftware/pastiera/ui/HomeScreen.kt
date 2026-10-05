package it.palsoftware.pastiera.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.suggestions.TypingStats
import java.text.NumberFormat

/*
 * Home screen pieces, sized for the Titan 2 Elite's nearly square screen (about 574 x 640 dp)
 * so everything fits without scrolling.
 */

/** Compact title bar with the app name and a settings button. */
@Composable
fun HomeHeader(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBar)
            .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (BuildConfig.UPSTREAM_BRANDING) {
                Text(
                    text = it.palsoftware.pastiera.Branding.appName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                NumenWordmark(name = it.palsoftware.pastiera.Branding.appName, pixel = 3.dp)
            }
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
        }
    }
}

/** Shows the next missing setup step, or a one-line confirmation once setup is done. */
@Composable
fun SetupStatusCard(
    isEnabled: Boolean,
    isSelected: Boolean,
    onEnable: () -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ready = isEnabled && isSelected
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (ready) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when {
                            ready -> R.string.home_setup_ready
                            !isEnabled -> R.string.home_setup_enable_title
                            else -> R.string.home_setup_select_title
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!ready) {
                    Text(
                        text = stringResource(
                            if (!isEnabled) R.string.home_setup_enable_body else R.string.home_setup_select_body
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (ready) {
                TextButton(onClick = onChoose) { Text(stringResource(R.string.home_setup_switch)) }
            } else {
                Button(onClick = if (!isEnabled) onEnable else onChoose) {
                    Text(stringResource(if (!isEnabled) R.string.home_setup_enable_action else R.string.home_setup_select_action))
                }
            }
        }
    }
}

/** Typing stats card (see TypingStats); updates live while the keyboard saves. */
@Composable
fun TypingStatsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(TypingStats.PREFS_NAME, Context.MODE_PRIVATE) }
    var summary by remember { mutableStateOf(TypingStats.summary(prefs)) }
    var confirmReset by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> summary = TypingStats.summary(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val numbers = remember { NumberFormat.getIntegerInstance() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (summary.words == 0L) {
            Text(
                text = stringResource(R.string.home_stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp)
            )
            return@Surface
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 0.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                StatTile(numbers.format(summary.words), stringResource(R.string.home_stats_words), Modifier.weight(1f))
                StatTile(numbers.format(summary.typosFixed), stringResource(R.string.home_stats_typos_fixed), Modifier.weight(1f))
                StatTile(summary.wordsPerMinute?.toString() ?: "–", stringResource(R.string.home_stats_wpm), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.favoriteWord?.let { stringResource(R.string.home_stats_favorite, it) }
                        ?: stringResource(R.string.home_stats_favorite_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.home_stats_reset)) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.home_stats_reset_title)) },
            text = { Text(stringResource(R.string.home_stats_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    TypingStats.reset(prefs)
                    confirmReset = false
                }) { Text(stringResource(R.string.home_stats_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            maxLines = 1
        )
    }
}

/** Quick toggles for the most used settings, kept in sync with the Settings screens. */
@Composable
fun QuickSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(QuickSettings.read(context)) }
    DisposableEffect(context) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state = QuickSettings.read(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val correctionAvailable = state.suggestionEngine
    val autocorrectOn = correctionAvailable && state.autocorrect

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            QuickSwitchRow(
                title = stringResource(R.string.auto_correct_auto_replace_title),
                subtitle = stringResource(R.string.home_autocorrect_description),
                checked = autocorrectOn,
                enabled = correctionAvailable,
                onCheckedChange = { SettingsManager.setAutoReplaceOnSpaceEnter(context, it) }
            )
            QuickSwitchRow(
                title = stringResource(R.string.auto_correct_aggressive_title),
                subtitle = stringResource(R.string.home_aggressive_description),
                checked = autocorrectOn && state.aggressive,
                enabled = autocorrectOn,
                onCheckedChange = { SettingsManager.setAggressiveAutoReplace(context, it) }
            )
            QuickSwitchRow(
                title = stringResource(R.string.auto_correct_context_aware_title),
                subtitle = stringResource(R.string.auto_correct_context_aware_description),
                checked = autocorrectOn && state.sentenceAware,
                enabled = autocorrectOn,
                onCheckedChange = { SettingsManager.setContextAwareCorrection(context, it) }
            )
            QuickSwitchRow(
                title = stringResource(R.string.auto_show_keyboard_title),
                subtitle = stringResource(R.string.home_auto_show_description),
                checked = state.autoShowKeyboard,
                enabled = true,
                onCheckedChange = { SettingsManager.setAutoShowKeyboard(context, it) }
            )
        }
    }
}

@Composable
private fun QuickSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            // One accessible switch for the whole row (title, subtitle and state read together).
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Opens the key grid where each key gets its app or action (SYM + key). */
fun openKeyShortcuts(context: Context) {
    context.startActivity(
        android.content.Intent(context, it.palsoftware.pastiera.SettingsActivity::class.java).apply {
            putExtra(it.palsoftware.pastiera.SettingsActivity.EXTRA_DESTINATION, it.palsoftware.pastiera.SettingsActivity.DESTINATION_CUSTOMIZATION)
            putExtra(it.palsoftware.pastiera.SettingsActivity.EXTRA_CUSTOMIZATION_DESTINATION, "launcher_shortcut_assignments")
        }
    )
}

/** Title bar of the diagnostics screen (Settings > Advanced > Diagnostics). */
@Composable
fun DiagnosticsHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBar)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.home_diagnostics_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.home_diagnostics_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class QuickSettings(
    val suggestionEngine: Boolean,
    val autocorrect: Boolean,
    val aggressive: Boolean,
    val sentenceAware: Boolean,
    val autoShowKeyboard: Boolean
) {
    companion object {
        fun read(context: Context) = QuickSettings(
            suggestionEngine = SettingsManager.isExperimentalSuggestionsEnabled(context),
            autocorrect = SettingsManager.getAutoReplaceOnSpaceEnter(context),
            aggressive = SettingsManager.getAggressiveAutoReplace(context),
            sentenceAware = SettingsManager.getContextAwareCorrection(context),
            autoShowKeyboard = SettingsManager.getAutoShowKeyboard(context)
        )
    }
}
