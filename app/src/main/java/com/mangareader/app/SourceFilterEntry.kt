package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.Filter

/**
 * One extension-defined filter row. Each mutable filter mirrors its state into
 * row-local Compose state so a tap only recomposes the control that changed.
 */
@Composable
internal fun FilterEntry(filter: Filter<*>, depth: Int, revision: Int) {
    val indent = (depth * 12).dp

    when (filter) {
        is Filter.Header -> Text(
            text = filter.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = indent, top = 14.dp, bottom = 6.dp),
        )

        is Filter.Separator -> Spacer(Modifier.height(8.dp))

        is Filter.CheckBox -> {
            var checked by remember(filter, revision) { mutableStateOf(filter.state) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, top = 2.dp, bottom = 2.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            filter.state = !filter.state
                            checked = filter.state
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            filter.state = it
                            checked = it
                        },
                    )
                    Text(filter.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is Filter.TriState -> {
            var state by remember(filter, revision) { mutableStateOf(filter.state) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, top = 2.dp, bottom = 2.dp),
                shape = MaterialTheme.shapes.medium,
                color = when (state) {
                    Filter.TriState.STATE_INCLUDE -> MaterialTheme.colorScheme.secondaryContainer
                    Filter.TriState.STATE_EXCLUDE -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            filter.state = (filter.state + 1) % 3
                            state = filter.state
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (state) {
                            Filter.TriState.STATE_INCLUDE -> "✓"
                            Filter.TriState.STATE_EXCLUDE -> "✗"
                            else -> "–"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = when (state) {
                            Filter.TriState.STATE_INCLUDE -> MaterialTheme.colorScheme.onSecondaryContainer
                            Filter.TriState.STATE_EXCLUDE -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(filter.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is Filter.Text -> {
            var text by remember(filter, revision) { mutableStateOf(filter.state) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    filter.state = it
                },
                label = { Text(filter.name) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, top = 4.dp, bottom = 4.dp),
            )
        }

        is Filter.Select<*> -> {
            var selected by remember(filter, revision) { mutableStateOf(filter.state) }
            Text(
                text = filter.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = indent, top = 10.dp, bottom = 6.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = indent),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filter.values.forEachIndexed { index, value ->
                    FilterChip(
                        selected = selected == index,
                        onClick = {
                            filter.state = index
                            selected = index
                        },
                        label = { Text(value.toString()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        }

        is Filter.Sort -> {
            var selection by remember(filter, revision) { mutableStateOf(filter.state) }
            Text(
                text = filter.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = indent, top = 10.dp, bottom = 6.dp),
            )
            filter.values.forEachIndexed { index, value ->
                val active = selection?.index == index
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indent, top = 2.dp, bottom = 2.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (active) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelection = if (active) {
                                    Filter.Sort.Selection(index, !(selection?.ascending ?: false))
                                } else {
                                    Filter.Sort.Selection(index, false)
                                }
                                filter.state = newSelection
                                selection = newSelection
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (active) {
                                if (selection?.ascending == true) "↑" else "↓"
                            } else {
                                "•"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        is Filter.Group<*> -> {
            Text(
                text = filter.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = indent, top = 14.dp, bottom = 6.dp),
            )
            (filter.state as? List<*>)
                ?.filterIsInstance<Filter<*>>()
                ?.forEach { child -> FilterEntry(child, depth + 1, revision) }
        }

        else -> Unit
    }
}
