package it.palsoftware.pastiera.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Settings rows share one column layout so titles line up with [SettingsSectionLabel]:
 * 16 dp start padding, a 24 dp icon slot, a 12 dp gap, then the title (52 dp).
 * A sub-row starts one step in ([SettingsSubRowStartInset]) and leaves its icon slot empty.
 */
val SettingsRowStartInset = 16.dp

/** Start padding of a sub-row: its (empty) icon slot sits under the parent row's title. */
val SettingsSubRowStartInset = 52.dp

/** Start of the title column of a top-level row (16 + 24 + 12 dp), for text without a row. */
val SettingsRowTextInset = 52.dp

/** Start of the title column of a sub-row (52 + 24 + 12 dp), for text without a row. */
val SettingsSubRowTextInset = 88.dp

/** The 24 dp icon column every settings row reserves. A null icon keeps the slot empty. */
@Composable
fun SettingsRowIcon(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    if (icon == null) {
        Spacer(modifier = modifier.size(24.dp))
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(24.dp)
        )
    }
}

/** [SettingsRowIcon] for drawable icons. */
@Composable
fun SettingsRowIcon(
    painter: Painter,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(24.dp)
    )
}
