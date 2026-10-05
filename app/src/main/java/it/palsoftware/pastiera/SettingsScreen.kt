package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import android.widget.Toast
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.update.checkForUpdateNotices
import it.palsoftware.pastiera.update.showReleaseNotice
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks
import kotlinx.coroutines.delay

/**
 * Sealed class per rappresentare lo stato della navigazione nelle settings.
 */
enum class SettingsDestination {
    Main,
    KeyboardsDevices,
    TextInput,
    Accessibility,
    AutoCorrection,
    Customization,
    NavMode,
    Advanced,
    About,
    CustomInputStyles,
    AppLanguage,
    DeviceSymLayerEditor,
    Modifiers
}

/** The destination payload of one SettingsActivity, also used by deep links. */
internal data class SettingsPage(
    val destination: SettingsDestination,
    val customizationDestination: String? = null,
    val keyboardThemeTarget: String? = null,
    val keyboardThemeTab: String? = null,
    val navModeKeyCode: Int? = null,
    val keyboardsDevicesDestination: KeyboardsDevicesDestination = KeyboardsDevicesDestination.Main
)

internal fun SettingRoute.toSettingsPage() = SettingsPage(
    destination = destination,
    customizationDestination = customizationDestination,
    keyboardThemeTarget = keyboardThemeTarget?.name,
    keyboardThemeTab = keyboardThemeTab?.name,
    keyboardsDevicesDestination = keyboardsDevicesDestination
)

/**
 * App settings screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    initialDestination: String? = null,
    initialCustomizationDestination: String? = null,
    initialKeyboardThemeTarget: String? = null,
    settingLinkRequest: SettingLinkRequest? = null
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var checkingForUpdates by remember { mutableStateOf(false) }
    val currentEntry = remember { context.settingsActivity().intent.settingsPage() }
    val currentDestination = currentEntry.destination
    var highlightSettingId by rememberSaveable { mutableStateOf<String?>(null) }
    var linkSheetEntry by remember { mutableStateOf<SettingEntry?>(null) }

    fun navigateTo(destination: SettingsDestination) {
        openSettingsPage(context, SettingsPage(destination))
    }
    fun navigateBack() { context.settingsActivity().finish() }
    fun openCustomization(destination: String?, keyboardThemeTarget: String? = null, keyboardThemeTab: String? = null) {
        openSettingsPage(context, SettingsPage(SettingsDestination.Customization,
            destination, keyboardThemeTarget, keyboardThemeTab))
    }
    fun navigateToNavMode(keyCode: Int?) {
        openSettingsPage(context, SettingsPage(SettingsDestination.NavMode, navModeKeyCode = keyCode))
    }

    /**
     * Navigates to a settings entry (e.g. from search or a deep link) and asks
     * its row to flash and scroll into view. Never changes any value.
     */
    fun openSettingEntry(entry: SettingEntry) {
        linkSheetEntry = null
        val visibleEntry = SettingLinkRegistry.visibleTarget(context, entry)
        val route = visibleEntry.route
        if (route.symCustomization) {
            context.startActivity(Intent(context, SymCustomizationActivity::class.java).apply {
                putExtra(SymCustomizationActivity.EXTRA_SETTING_ID, visibleEntry.id)
            })
            return
        }
        val target = route.toSettingsPage()
        if (currentEntry != target) {
            context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                data = android.net.Uri.parse("${SettingLinkRegistry.LINK_SCHEME}://${SettingLinkRegistry.LINK_HOST}/${visibleEntry.id}")
            })
            return
        }
        highlightSettingId = visibleEntry.id
    }

    // Deep link (<scheme>://setting/<id>, see SettingLinkRegistry) arriving via intent or onNewIntent
    LaunchedEffect(settingLinkRequest?.serial) {
        val request = settingLinkRequest ?: return@LaunchedEffect
        val entry = SettingLinkRegistry.byId(request.id)
        if (entry == null) {
            Toast.makeText(context, R.string.settings_link_unavailable_toast, Toast.LENGTH_SHORT)
                .show()
        } else {
            openSettingEntry(entry)
        }
    }
    LaunchedEffect(highlightSettingId) {
        if (highlightSettingId != null) {
            // Must outlast the blink sequence in settingRow (~1.65 s) so the
            // outline fades out gently after the last blink.
            delay(1800)
            highlightSettingId = null
        }
    }

    // Automatic update check on screen open (only once, respecting dismissed releases)
    if (currentDestination == SettingsDestination.Main && shouldUseGithubUpdateChecks(context)) {
        LaunchedEffect(Unit) {
            it.palsoftware.pastiera.update.checkForUpdateNotices(
                context = context,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = true
            ) { result ->
                if (result.hasAnnouncement && result.releaseTag != null && result.displayName != null) {
                    it.palsoftware.pastiera.update.showReleaseNotice(context, result)
                }
            }
        }
    }


    CompositionLocalProvider(
        LocalSettingHighlightId provides highlightSettingId,
        LocalSettingLinkLongPress provides ({ id -> linkSheetEntry = SettingLinkRegistry.byId(id) })
    ) {
    val entry = currentEntry
        when (entry.destination) {
            SettingsDestination.Main -> {
                SettingsMainScreen(
                    modifier = modifier,
                    context = context,
                    checkingForUpdates = checkingForUpdates,
                    onCheckingForUpdatesChange = { checkingForUpdates = it },
                    onOpenSettingEntry = { target ->
                        context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                            data = android.net.Uri.parse("${SettingLinkRegistry.LINK_SCHEME}://${SettingLinkRegistry.LINK_HOST}/${target.id}")
                        })
                    },
                    onModifiersClick = { navigateTo(SettingsDestination.Modifiers) },
                    onKeyboardsDevicesClick = { navigateTo(SettingsDestination.KeyboardsDevices) },
                    onTextInputClick = { navigateTo(SettingsDestination.TextInput) },
                    onAccessibilityClick = { navigateTo(SettingsDestination.Accessibility) },
                    onAutoCorrectionClick = { navigateTo(SettingsDestination.AutoCorrection) },
                    onCustomizationClick = { openCustomization(null) },
                    onStatusBarButtonsClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS)
                    },
                    onKeyboardThemeClick = {
                        openCustomization(
                            SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
                            initialKeyboardThemeTarget
                        )
                    },
                    onQuickLauncherClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS)
                    },
                    onNavModeClick = {
                        navigateToNavMode(null)
                    },
                    onEnterBehaviorClick = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR)
                    },
                    onAdvancedClick = { navigateTo(SettingsDestination.Advanced) },
                    onAboutClick = { navigateTo(SettingsDestination.About) },
                    onBackClick = { navigateBack() },
                    onCustomInputStylesClick = { navigateTo(SettingsDestination.CustomInputStyles) },
                    onAppLanguageClick = { navigateTo(SettingsDestination.AppLanguage) }
                )
            }
            SettingsDestination.KeyboardsDevices -> {
                KeyboardsDevicesSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavModeSettingsClick = { keyCode ->
                        navigateToNavMode(keyCode)
                    },
                    onOpenKeyboardTheme = {
                        openCustomization(
                            SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
                            SettingsActivity.KEYBOARD_THEME_TARGET_SOFTWARE
                        )
                    },
                    destination = entry.keyboardsDevicesDestination,
                    onDestinationChange = { destination ->
                        if (destination == KeyboardsDevicesDestination.Main) navigateBack()
                        else openSettingsPage(context, entry.copy(keyboardsDevicesDestination = destination))
                    }
                )
            }
            SettingsDestination.TextInput -> {
                TextInputSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavModeSettingsClick = { navigateToNavMode(null) }
                )
            }
            SettingsDestination.Accessibility -> {
                AccessibilitySettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AutoCorrection -> {
                AutoCorrectionCategoryScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.Customization -> {
                CustomizationSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    initialDestination = entry.customizationDestination,
                    initialKeyboardThemeTarget = entry.keyboardThemeTarget,
                    initialKeyboardThemeTab = entry.keyboardThemeTab,
                    onOpenModifiers = { navigateTo(SettingsDestination.Modifiers) }
                )
            }
            SettingsDestination.NavMode -> {
                NavModeSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    initialKeyCode = entry.navModeKeyCode
                )
            }
            SettingsDestination.Advanced -> {
                AdvancedSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.About -> {
                AboutScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.CustomInputStyles -> {
                CustomInputStylesScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AppLanguage -> {
                AppLanguageSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.DeviceSymLayerEditor -> {
                DeviceSymLayerEditorStubScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.Modifiers -> {
                ModifierSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenSymLayers = {
                        context.startActivity(
Intent(context, SymCustomizationActivity::class.java)
                        )
                    },
                    onOpenSymShortcuts = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS)
                    },
                    onOpenNavMode = {
                        navigateToNavMode(null)
                    }
                )
            }
        }
    }

    // Share/copy sheet for the settings entry currently being long-pressed
    linkSheetEntry?.let { entry ->
        SettingLinkSheet(entry = entry, onDismiss = { linkSheetEntry = null })
    }
}



@Composable
private fun SettingsMainScreen(
    modifier: Modifier,
    context: Context,
    checkingForUpdates: Boolean,
    onCheckingForUpdatesChange: (Boolean) -> Unit,
    onModifiersClick: () -> Unit,
    onKeyboardsDevicesClick: () -> Unit,
    onTextInputClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onAutoCorrectionClick: () -> Unit,
    onCustomizationClick: () -> Unit,
    onStatusBarButtonsClick: () -> Unit,
    onKeyboardThemeClick: () -> Unit,
    onQuickLauncherClick: () -> Unit,
    onNavModeClick: () -> Unit,
    onEnterBehaviorClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBackClick: () -> Unit,
    onCustomInputStylesClick: () -> Unit,
    onAppLanguageClick: () -> Unit,
    onOpenSettingEntry: (SettingEntry) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResults = remember(searchQuery, context) {
        SettingLinkRegistry.search(context, searchQuery)
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
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    SettingsSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            if (searchQuery.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        searchResults.forEach { entry ->
                            SettingSearchResultRow(
                                entry = entry,
                                onClick = {
                                    keyboardController?.hide()
                                    searchQuery = ""
                                    onOpenSettingEntry(entry)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
            // The most-used setup in Numen: what SYM + a key opens.
            if (!BuildConfig.UPSTREAM_BRANDING) {
                SettingsCategoryRow(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.settings_key_shortcuts_title),
                    description = stringResource(R.string.settings_key_shortcuts_description),
                    onClick = { it.palsoftware.pastiera.ui.openKeyShortcuts(context) }
                )
            }

            // Typing: what the keys type.
            SettingsGroupDivider(stringResource(R.string.settings_group_typing))

            SettingsCategoryRow(
                icon = Icons.Filled.Spellcheck,
                title = stringResource(R.string.settings_category_auto_correction),
                description = stringResource(R.string.settings_row_autocorrect_description),
                linkId = SettingLinkIds.MAIN_AUTO_CORRECTION,
                onClick = onAutoCorrectionClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.settings_category_text_input),
                description = stringResource(R.string.settings_row_text_input_description),
                linkId = SettingLinkIds.MAIN_TEXT_INPUT,
                onClick = onTextInputClick
            )
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                title = stringResource(R.string.app_enter_behaviour_title),
                description = stringResource(R.string.settings_row_enter_description),
                linkId = SettingLinkIds.MAIN_APP_ENTER_BEHAVIOR,
                onClick = onEnterBehaviorClick
            )

            // Keys: what keys and shortcuts do.
            SettingsGroupDivider(stringResource(R.string.settings_group_utility))

            SettingsCategoryRow(
                iconRes = R.drawable.modifier_keys_24,
                title = stringResource(R.string.modifiers_title),
                description = stringResource(R.string.modifiers_description),
                linkId = SettingLinkIds.MAIN_MODIFIERS,
                onClick = onModifiersClick
            )
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.ManageSearch,
                title = stringResource(R.string.starter_launcher_shortcuts_title),
                description = stringResource(R.string.starter_launcher_shortcuts_description),
                linkId = SettingLinkIds.MAIN_LAUNCHER_SHORTCUTS,
                onClick = onQuickLauncherClick
            )
            SettingsCategoryRow(
                icon = ImageVector.vectorResource(R.drawable.navigation_24),
                title = stringResource(R.string.nav_mode_title),
                description = stringResource(R.string.settings_row_nav_mode_description),
                linkId = SettingLinkIds.MAIN_NAV_MODE,
                onClick = onNavModeClick
            )

            // Keyboard bar: how it looks and sounds when shown.
            SettingsGroupDivider(stringResource(R.string.settings_group_customization))

            SettingsCategoryRow(
                icon = Icons.Filled.SmartButton,
                title = stringResource(R.string.status_bar_buttons_title),
                description = stringResource(R.string.status_bar_buttons_description),
                linkId = SettingLinkIds.MAIN_STATUS_BAR_BUTTONS,
                onClick = onStatusBarButtonsClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.keyboard_theme_title),
                linkId = SettingLinkIds.MAIN_KEYBOARD_THEME,
                onClick = onKeyboardThemeClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.settings_category_customization),
                description = stringResource(R.string.settings_row_sound_variations_description),
                linkId = SettingLinkIds.MAIN_CUSTOMIZATION,
                onClick = onCustomizationClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Keyboard,
                title = stringResource(R.string.keyboards_devices_title),
                description = stringResource(R.string.settings_row_devices_description),
                linkId = SettingLinkIds.MAIN_KEYBOARDS_DEVICES,
                onClick = onKeyboardsDevicesClick
            )

            // System: everything else.
            SettingsGroupDivider(stringResource(R.string.settings_group_system))

            SettingsCategoryRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.custom_input_styles_title),
                description = stringResource(R.string.settings_row_languages_description),
                linkId = SettingLinkIds.MAIN_CUSTOM_INPUT_STYLES,
                onClick = onCustomInputStylesClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.TouchApp,
                title = stringResource(R.string.settings_category_accessibility),
                description = stringResource(R.string.settings_row_accessibility_description),
                linkId = SettingLinkIds.MAIN_ACCESSIBILITY,
                onClick = onAccessibilityClick
            )
            SettingsCategoryRow(
                icon = ImageVector.vectorResource(R.drawable.translate_24),
                title = stringResource(R.string.app_language_title),
                description = currentAppLanguageLabel(context),
                linkId = SettingLinkIds.MAIN_APP_LANGUAGE,
                onClick = onAppLanguageClick
            )
            SettingsCategoryRow(
                icon = Icons.Filled.Engineering,
                title = stringResource(R.string.settings_category_advanced),
                description = stringResource(R.string.settings_row_advanced_description),
                linkId = SettingLinkIds.MAIN_ADVANCED,
                onClick = onAdvancedClick
            )

            SettingsGroupDivider(stringResource(R.string.settings_group_pastiera))

            SettingsCategoryRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_title),
                description = stringResource(
                    R.string.settings_about_version_summary,
                    BuildConfig.VERSION_NAME
                ),
                linkId = SettingLinkIds.MAIN_ABOUT,
                onClick = onAboutClick
            )
            if (BuildConfig.UPSTREAM_BRANDING) { // Pastiera donations only.
                SettingsCategoryRow(
                    icon = Icons.Filled.VolunteerActivism,
                    title = stringResource(R.string.settings_support_open_collective),
                    description = stringResource(R.string.settings_support_open_collective_description),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://pastiera.eu/donate")
                            )
                        )
                    }
                )
            }

            if (shouldUseGithubUpdateChecks(context)) {
                SettingsCategoryRow(
                    icon = ImageVector.vectorResource(R.drawable.plektra_open_monochrome_24),
                    title = if (checkingForUpdates) {
                        stringResource(
                            if (BuildConfig.RELEASE_CHANNEL == "stable") {
                                R.string.settings_stable_update_checking
                            } else {
                                R.string.settings_update_checking
                            }
                        )
                    } else {
                        stringResource(
                            if (BuildConfig.RELEASE_CHANNEL == "stable") {
                                R.string.settings_stable_update_section_title
                            } else {
                                R.string.settings_update_section_title
                            }
                        )
                    },
                    description = stringResource(
                        if (BuildConfig.RELEASE_CHANNEL == "stable") {
                            R.string.settings_stable_update_section_description
                        } else {
                            R.string.settings_update_section_description
                        }
                    ),
                    enabled = !checkingForUpdates,
                    onClick = {
                        onCheckingForUpdatesChange(true)
                        val onResult: (it.palsoftware.pastiera.update.UpdateCheckResult) -> Unit = { result ->
                            onCheckingForUpdatesChange(false)
                            when {
                                result.hasAnnouncement -> showReleaseNotice(context, result)
                                !result.successful -> Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_update_check_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                else -> Toast.makeText(
                                    context,
                                    context.getString(
                                        if (BuildConfig.RELEASE_CHANNEL == "stable") {
                                            R.string.settings_stable_update_up_to_date
                                        } else {
                                            R.string.settings_update_up_to_date
                                        }
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        checkForUpdateNotices(
                            context = context,
                            releaseChannel = BuildConfig.RELEASE_CHANNEL,
                            ignoreDismissedReleases = false,
                            callback = onResult
                        )
                    }
                )
            }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    linkId: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (description == null) 60.dp else 72.dp)
            .settingRow(linkId?.takeIf { enabled }, onClick.takeIf { enabled })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
private fun SettingsGroupDivider(label: String) {
    it.palsoftware.pastiera.ui.SettingsSectionLabel(label)
}
