package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.core.suggestions.UserDictionaryStore
import it.palsoftware.pastiera.R
import org.json.JSONObject
import java.util.LinkedHashMap

/**
 * Screen for editing the text replacements.
 * Shows corrections in the format "original -> corrected" and allows editing them.
 */
@Composable
fun AutoCorrectEditScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Load corrections (custom first, then default)
    // Use LinkedHashMap to maintain insertion order (newest first)
    var corrections by remember {
        mutableStateOf(loadEditableCorrections(context).toLinkedHashMap())
    }

    // State for the add/edit dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    val userDictionaryStore = remember { UserDictionaryStore() }

    // State for search query
    var searchQuery by remember { mutableStateOf("") }

    // Filter corrections based on search query (searches both original and corrected)
    val filteredCorrections = remember(corrections, searchQuery) {
        if (searchQuery.isBlank()) {
            corrections
        } else {
            val query = searchQuery.lowercase()
            corrections.filter { (original, corrected) ->
                original.lowercase().contains(query) ||
                corrected.lowercase().contains(query)
            }
        }
    }

    // Handle the system back button

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_content_description)
                            )
                        }
                        Text(
                            text = stringResource(R.string.auto_correct_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.auto_correct_add_correction)
                        )
                    }
                }
            }
        }
        ) { paddingValues ->
            AnimatedContent(
                targetState = Unit,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "auto_correct_edit_animation"
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
            // Header with description
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.auto_correct_edit_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Search field
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.auto_correct_search_placeholder)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.auto_correct_search_description)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.auto_correct_clear_search)
                                )
                            }
                        }
                    }
                )
            }

            // List of corrections
            if (filteredCorrections.isEmpty()) {
                // Message shown when there are no corrections or no search results
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            stringResource(R.string.auto_correct_no_corrections_found)
                        } else {
                            stringResource(R.string.auto_correct_no_corrections)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Show corrections in insertion order (newest first)
                filteredCorrections.forEach { (original, corrected) ->
                    CorrectionItem(
                        original = original,
                        corrected = corrected,
                        onEdit = {
                            editingKey = original
                            showAddDialog = true
                        },
                        onDelete = {
                            val newCorrections = corrections.toMutableMap()
                            newCorrections.remove(original)
                            corrections = newCorrections.toLinkedHashMap()
                            saveCorrections(context, corrections)
                        }
                    )
                }
                }
            }
        }
    }

    // Dialog for adding/editing a correction
    if (showAddDialog) {
        AddCorrectionDialog(
            originalKey = editingKey,
            originalValue = editingKey?.let { corrections[it] },
            onDismiss = {
                showAddDialog = false
                editingKey = null
            },
            onSave = { original, corrected, addReplacementToDictionary ->
                val newCorrections = LinkedHashMap<String, String>()
                val key = original.lowercase()

                // If editing, remove the old key first
                if (editingKey != null && editingKey != key) {
                    newCorrections.remove(editingKey)
                }

                // Add the new/edited correction at the beginning (newest first)
                newCorrections[key] = corrected

                // Add all other corrections (excluding the one being edited if it's the same key)
                corrections.forEach { (k, v) ->
                    if (k != key && k != editingKey) {
                        newCorrections[k] = v
                    }
                }

                corrections = newCorrections
                saveCorrections(context, corrections)
                if (addReplacementToDictionary) {
                    addReplacementToUserDictionaryIfNeeded(context, userDictionaryStore, corrected)
                }
                showAddDialog = false
                editingKey = null
            }
        )
    }
}

@Composable
private fun CorrectionItem(
    original: String,
    corrected: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Original column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = original,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            // Arrow
            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Corrected column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = corrected,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.auto_correct_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddCorrectionDialog(
    originalKey: String?,
    originalValue: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var originalText by remember { mutableStateOf(originalKey ?: "") }
    var correctedText by remember { mutableStateOf(originalValue ?: "") }
    var addReplacementToDictionary by remember { mutableStateOf(true) }

    // Update fields when originalKey changes
    LaunchedEffect(originalKey) {
        originalText = originalKey ?: ""
        correctedText = originalValue ?: ""
        addReplacementToDictionary = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (originalKey != null) {
                    stringResource(R.string.auto_correct_edit_correction)
                } else {
                    stringResource(R.string.auto_correct_add_correction)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text(stringResource(R.string.auto_correct_original)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = correctedText,
                    onValueChange = { correctedText = it },
                    label = { Text(stringResource(R.string.auto_correct_corrected)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = addReplacementToDictionary,
                        onCheckedChange = { addReplacementToDictionary = it }
                    )
                    Text(
                        text = stringResource(R.string.auto_correct_add_replacement_to_dictionary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (originalText.isNotBlank() && correctedText.isNotBlank()) {
                        onSave(originalText.trim(), correctedText.trim(), addReplacementToDictionary)
                    }
                },
                enabled = originalText.isNotBlank() && correctedText.isNotBlank()
            ) {
                Text(stringResource(R.string.auto_correct_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auto_correct_cancel))
            }
        }
    )
}

/**
 * Loads the list to edit: the user's saved list, or the bundled defaults if none is saved yet.
 */
private fun loadEditableCorrections(context: Context): Map<String, String> {
    val corrections = mutableMapOf<String, String>()

    // First load custom corrections (if any)
    val customCorrections = SettingsManager.getCustomAutoCorrections(context)
    if (customCorrections.isNotEmpty()) {
        corrections.putAll(customCorrections)
    }

    // Then load default corrections from assets (only if there are no custom ones)
    if (corrections.isEmpty()) {
        try {
            val fileName = "common/autocorrect/auto_corrections_en.json"
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.getString(key)
                corrections[key] = value
            }
        } catch (e: Exception) {
            // File not found or parsing error
        }
    }

    return corrections
}

/**
 * Saves the list and reloads the replacements the keyboard uses.
 */
private fun saveCorrections(context: Context, corrections: Map<String, String>) {
    SettingsManager.saveCustomAutoCorrections(context, corrections)
    AutoCorrector.loadCorrections(context.assets, context)
}

internal fun addReplacementToUserDictionaryIfNeeded(
    context: Context,
    userDictionaryStore: UserDictionaryStore,
    replacement: String
): Boolean {
    val word = replacement.trim()
    if (word.isEmpty() || word.none { it.isLetterOrDigit() }) {
        return false
    }

    userDictionaryStore.loadUserEntries(context)
    val alreadyExists = userDictionaryStore.getSnapshot().any { entry ->
        entry.word.equals(word, ignoreCase = true)
    }
    if (alreadyExists) {
        return false
    }

    userDictionaryStore.addWord(context, word)
    val intent = Intent(AppBroadcastActions.USER_DICTIONARY_UPDATED).apply {
        setPackage(context.packageName)
    }
    context.sendBroadcast(intent)
    return true
}

/**
 * Converts a Map to LinkedHashMap to maintain insertion order.
 */
private fun <K, V> Map<K, V>.toLinkedHashMap(): LinkedHashMap<K, V> {
    return LinkedHashMap(this)
}
