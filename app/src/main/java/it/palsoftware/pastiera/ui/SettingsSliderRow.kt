package it.palsoftware.pastiera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A settings row whose control is a slider, laid out the same way on every screen: the icon
 * sits on the title line, the current value is the line under the title (like a summary),
 * an optional explanation follows, and the slider fills the title column below.
 * Pass the setting link / highlight modifiers through [modifier].
 */
@Composable
fun SettingsSliderRow(
    icon: ImageVector?,
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    subRow: Boolean = false,
    enabled: Boolean = true,
    iconPainter: Painter? = null,
    slider: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (subRow) SettingsSubRowStartInset else SettingsRowStartInset,
                end = 16.dp,
                top = 12.dp,
                bottom = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val iconTint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        if (iconPainter != null) {
            SettingsRowIcon(iconPainter, tint = iconTint)
        } else {
            SettingsRowIcon(if (subRow) null else icon, tint = iconTint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            slider()
        }
    }
}
