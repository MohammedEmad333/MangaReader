package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

@Composable
internal fun SheetHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
internal fun SheetNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
    )
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
internal fun TriFilterRow(label: String, state: FilterState, onChange: (FilterState) -> Unit) {
    val toggle = when (state) {
        FilterState.OFF -> ToggleableState.Off
        FilterState.INCLUDE -> ToggleableState.On
        FilterState.EXCLUDE -> ToggleableState.Indeterminate
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(state.next()) }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TriStateCheckbox(state = toggle, onClick = { onChange(state.next()) })
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label)
            if (state == FilterState.EXCLUDE) {
                Text(
                    "Excluded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SortRow(
    label: String,
    selected: Boolean,
    ascending: Boolean,
    showDirection: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.width(32.dp)) {
            if (selected) {
                if (showDirection) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = if (ascending) "Ascending" else "Descending",
                        tint = MaterialTheme.colorScheme.primary,
                        // One glyph, turned over, rather than two icons: the
                        // extended icon pack isn't a dependency here.
                        modifier = Modifier.rotate(if (ascending) 180f else 0f)
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Text(label, modifier = Modifier.weight(1f))
        trailing()
    }
}
