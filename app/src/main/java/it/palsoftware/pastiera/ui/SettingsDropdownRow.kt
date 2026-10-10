package it.palsoftware.pastiera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.settingRow

/**
 * The one dropdown style used in settings: a list row with the icon, the title, the current
 * choice as the gray line under it (then an optional explanation), and a dropdown caret at
 * the end. The whole row opens the menu; nothing is saved until a choice is picked.
 *
 * [linkId] makes the row addressable (search, share, highlight) like other settings rows.
 * A [subRow] starts one step in and leaves its icon slot empty, under a parent row's title.
 */
@Composable
fun <T> SettingsDropdownRow(
    title: String,
    value: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    description: String? = null,
    linkId: String? = null,
    subRow: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsDropdownRowLayout(
        title = title,
        value = value,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
        icon = icon,
        iconPainter = iconPainter,
        description = description,
        linkId = linkId,
        subRow = subRow,
        trailing = trailing
    ) {
        options.forEach { option ->
            SettingsDropdownMenuItem(
                text = optionLabel(option),
                selected = option == selected,
                onClick = {
                    expanded = false
                    onSelect(option)
                }
            )
        }
    }
}

/**
 * [SettingsDropdownRow] with a caller-built menu, for choices whose items need more than a
 * label (an icon, a second line) or whose picking does more than save one value.
 * Fill [menuContent] with [SettingsDropdownMenuItem]s and close the menu in their onClick.
 */
@Composable
fun SettingsDropdownRowLayout(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    description: String? = null,
    linkId: String? = null,
    subRow: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .settingRow(linkId) { onExpandedChange(true) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(
                    start = if (subRow) SettingsSubRowStartInset else SettingsRowStartInset,
                    end = if (trailing != null) 4.dp else 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconPainter != null && !subRow) {
                SettingsRowIcon(iconPainter)
            } else {
                SettingsRowIcon(if (subRow) null else icon)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            trailing?.invoke(this)
        }
        // Opens under the row, lined up with the title column.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            offset = DpOffset(
                x = if (subRow) SettingsSubRowTextInset else SettingsRowTextInset,
                y = 0.dp
            )
        ) {
            menuContent()
        }
    }
}

/**
 * A menu item of a settings dropdown. The current choice carries a check mark in the
 * leading slot (kept empty, so labels line up, on the others); [icon] goes before the label.
 */
@Composable
fun SettingsDropdownMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                icon?.invoke()
                Column {
                    Text(text)
                    if (supportingText != null) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        onClick = onClick,
        leadingIcon = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        }
    )
}
