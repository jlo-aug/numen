package it.palsoftware.pastiera

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class TutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                TutorialScreen(
                    onComplete = {
                        SettingsManager.setTutorialCompleted(this@TutorialActivity)
                        startActivity(Intent(this@TutorialActivity, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun numenTutorialPages(isEnabled: Boolean, isSelected: Boolean): List<TutorialPageType> = buildList {
    add(
        TutorialPageType.MeetNumen(
            title = stringResource(R.string.numen_tutorial_welcome_title),
            description = stringResource(R.string.numen_tutorial_welcome_description)
        )
    )
    if (!isEnabled) {
        add(
            TutorialPageType.EnablePastiera(
                title = stringResource(R.string.tutorial_page_enable_title),
                description = stringResource(R.string.numen_tutorial_enable_description)
            )
        )
    }
    if (!isSelected) {
        add(
            TutorialPageType.SelectPastiera(
                title = stringResource(R.string.tutorial_page_select_title),
                description = stringResource(R.string.numen_tutorial_select_description)
            )
        )
    }
    add(
        TutorialPageType.Standard(
            title = stringResource(R.string.numen_tutorial_ready_title),
            description = "",
            icon = Icons.Filled.CheckCircle,
            iconTint = MaterialTheme.colorScheme.primary
        )
    )
}

sealed class TutorialPageType {
    /** Numen's first page: the mascot walking above the name. */
    data class MeetNumen(val title: String, val description: String) : TutorialPageType()

    data class EnablePastiera(val title: String, val description: String) : TutorialPageType()

    data class SelectPastiera(val title: String, val description: String) : TutorialPageType()

    /** The last page: you're ready. */
    data class Standard(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    // Check IME status
    var isPastieraEnabled by remember { mutableStateOf(false) }
    var isPastieraSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkImeStatus(context) { enabled, selected ->
            isPastieraEnabled = enabled
            isPastieraSelected = selected
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            checkImeStatus(context) { enabled, selected ->
                isPastieraEnabled = enabled
                isPastieraSelected = selected
            }
        }
    }

    // Numen's welcome is short: meet your numen, turn it on, choose it, done.
    val pages = numenTutorialPages(isPastieraEnabled, isPastieraSelected)
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .padding(horizontal = 12.dp)
            ) {
            }
            
            // Pager content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (val pageType = pages[page]) {
                        is TutorialPageType.MeetNumen -> {
                            TutorialMeetNumenPageContent(page = pageType, modifier = Modifier.fillMaxSize())
                        }
                        is TutorialPageType.EnablePastiera -> {
                            TutorialEnablePastieraPageContent(
                                page = pageType,
                                isPastieraEnabled = isPastieraEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.SelectPastiera -> {
                            TutorialSelectPastieraPageContent(
                                page = pageType,
                                isPastieraEnabled = isPastieraEnabled,
                                isPastieraSelected = isPastieraSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.Standard -> {
                            TutorialNumenReadyPageContent(page = pageType, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                if (pagerState.currentPage < pages.lastIndex) {
                    FilledTonalButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 12.dp)
                            .height(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_skip),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

            }
            
            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage - 1,
                                        animationSpec = tween(300)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.tutorial_previous),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.tutorial_previous), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, _ ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    }
                                )
                                .padding(if (isSelected) 0.dp else 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary)
                                )
                            }
                        }
                        if (index < pages.size - 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (pagerState.currentPage < pages.size - 1) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + 1,
                                        animationSpec = tween(300)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.tutorial_next), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.tutorial_next),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onComplete,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.numen_tutorial_done), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private val TutorialIconSurfaceSize = 82.dp
private val TutorialIconSize = 38.dp

@Composable
private fun TutorialPageHeader(
    title: String,
    centered: Boolean,
    iconContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        iconContent()
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.wrapContentWidth()
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun TutorialIconSurface(
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = Modifier.size(TutorialIconSurfaceSize),
        shape = CircleShape,
        color = tint.copy(alpha = 0.1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(TutorialIconSize),
                tint = tint
            )
        }
    }
}

internal fun shouldShowQuickLauncherMappingConflict(context: Context): Boolean {
    val spaceShortcut = SettingsManager.getLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
    return spaceShortcut != null && !SettingsManager.isQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
}

@Composable
private fun TutorialMeetNumenPageContent(page: TutorialPageType.MeetNumen, modifier: Modifier = Modifier) {
    // Centred; scrolls only if the text outgrows the screen.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            it.palsoftware.pastiera.ui.NumenWordmark(name = Branding.appName, pixel = 4.dp)
            it.palsoftware.pastiera.ui.NumenWalking(pixel = 6.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            // A comfortable reading width, not edge to edge.
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 470.dp)
            )
        }
    }
}

/** Numen's last page: the mascot and three tips, each with its key. */
@Composable
private fun TutorialNumenReadyPageContent(page: TutorialPageType.Standard, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            it.palsoftware.pastiera.ui.NumenSprite(
                face = it.palsoftware.pastiera.ui.NumenFace.Happy,
                legsApart = false,
                facingLeft = false,
                glow = 0.7f,
                pixel = 5.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TutorialKeyTip(key = "SYM", text = stringResource(R.string.numen_tutorial_tip_sym))
                TutorialKeyTip(key = "⌫", text = stringResource(R.string.numen_tutorial_tip_undo))
                TutorialKeyTip(key = "SYM + A", text = stringResource(R.string.numen_tutorial_tip_shortcuts))
                TutorialKeyTip(icon = Icons.Filled.Settings, text = stringResource(R.string.numen_tutorial_tip_settings))
            }
        }
    }
}

/** One tip with a keycap-style chip for its key or icon. */
@Composable
private fun TutorialKeyTip(
    text: String,
    key: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 52.dp).height(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        text = key.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TutorialEnablePastieraPageContent(
    page: TutorialPageType.EnablePastiera,
    isPastieraEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    TutorialActionPageLayout(
        title = page.title,
        description = page.description,
        icon = Icons.Filled.Settings,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
        statusContent = {
            if (isPastieraEnabled) {
                TutorialStatusRow(
                    text = stringResource(R.string.tutorial_enabled_message),
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actionContent = {
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_enable_button), style = MaterialTheme.typography.bodyMedium)
        }
        }
    )
}

@Composable
fun TutorialSelectPastieraPageContent(
    page: TutorialPageType.SelectPastiera,
    isPastieraEnabled: Boolean,
    isPastieraSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    TutorialActionPageLayout(
        title = page.title,
        description = page.description,
        icon = Icons.Filled.Keyboard,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
        statusContent = {
            when {
                !isPastieraEnabled -> TutorialStatusRow(
                    text = stringResource(R.string.tutorial_enable_first_message),
                    icon = Icons.Filled.Warning,
                    tint = MaterialTheme.colorScheme.error
                )
                isPastieraSelected -> TutorialStatusRow(
                    text = stringResource(R.string.tutorial_selected_message),
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actionContent = {
        Button(
            onClick = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isPastieraEnabled,
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_select_button), style = MaterialTheme.typography.bodyMedium)
        }
        }
    )
}

@Composable
private fun TutorialActionPageLayout(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    statusContent: @Composable ColumnScope.() -> Unit = {},
    actionContent: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TutorialPageHeader(
            title = title,
            centered = true,
            iconContent = { TutorialIconSurface(icon = icon, tint = tint) }
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        statusContent()
        Spacer(modifier = Modifier.weight(1f))
        actionContent()
    }
}

@Composable
private fun TutorialStatusRow(
    text: String,
    icon: ImageVector,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Checks if Pastiera IME is enabled and selected.
 */
private fun checkImeStatus(
    context: Context,
    callback: (enabled: Boolean, selected: Boolean) -> Unit
) {
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pastieraPackageName = ImeIdentity.packageName
        val pastieraImeId = ImeIdentity.imeId
        
        val enabledInputMethods = imm.enabledInputMethodList
        val isEnabled = enabledInputMethods.any { inputMethodInfo ->
            inputMethodInfo.packageName == pastieraPackageName ||
            ImeIdentity.matchesImeId(inputMethodInfo.id)
        }
        
        var isSelected = false
        if (isEnabled) {
            try {
                val defaultInputMethod = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD
                ) ?: ""
                isSelected = ImeIdentity.matchesImeId(defaultInputMethod)
            } catch (e: SecurityException) {
                try {
                    val currentSubtype = imm.currentInputMethodSubtype
                    if (currentSubtype != null) {
                        val allInputMethods = imm.inputMethodList
                        val pastieraInputMethod = allInputMethods.find { 
                            it.packageName == pastieraPackageName || ImeIdentity.matchesImeId(it.id)
                        }
                        if (pastieraInputMethod != null && enabledInputMethods.size == 1) {
                            isSelected = true
                        } else {
                            isSelected = false
                        }
                    } else {
                        isSelected = false
                    }
                } catch (e2: Exception) {
                    isSelected = false
                }
            } catch (e: Exception) {
                isSelected = false
            }
        }
        
        callback(isEnabled, isSelected)
    } catch (e: Exception) {
        android.util.Log.e("TutorialActivity", "Error checking IME status", e)
        callback(false, false)
    }
}

