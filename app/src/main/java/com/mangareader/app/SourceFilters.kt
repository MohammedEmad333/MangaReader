package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.Filter

/**
 * The filter sheet for a source, built from its own `getFilterList()`.
 *
 * This is the browse-side twin of [SourceSettingsDialog]: both take a tree of
 * objects defined by extension code and render it without knowing anything about
 * the particular source. It reaches the filters the same way that file reaches
 * preferences — by casting to [TachiyomiSourceAdapter] — rather than by widening
 * this app's own `Source` interface with a Tachiyomi type.
 *
 * **The filters are mutated in place, not copied.** `Filter` stores its value in
 * a `var state`, and [TachiyomiSourceAdapter.filterList] deliberately hands back
 * one live instance so that what this edits is what the next search reads.
 *
 * The cost of that is that Compose can't see the changes: a plain object's field
 * isn't snapshot state, so nothing recomposes when a checkbox is ticked. Each
 * row solves this locally — it mirrors `filter.state` into its own `remember`ed
 * Compose state and updates both together, so a tap only recomposes that row.
 *
 * [revision] exists only for Reset: it's a counter bumped when the filter list
 * is replaced wholesale, and every row's local state is keyed on it too, so a
 * Reset forces every row to re-read the fresh `filter.state` instead of holding
 * onto stale local state from before. Ordinary taps never touch [revision], so
 * they only recompose the one row that changed.
 *
 * Unknown filter types are skipped rather than drawn as an empty row. Extensions
 * subclass these freely and a source built against a newer library may carry
 * something this doesn't handle; a gap is better than a control that does
 * nothing.
 */
@Composable
internal fun SourceFilterDialog(
    source: Source,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val adapter = source as? TachiyomiSourceAdapter
    var revision by remember(source.id) { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (adapter == null) {
                    Text("This source has no filters.")
                } else {
                    val filters = adapter.filterList
                    if (filters.isEmpty()) {
                        Text("This source has no filters.")
                    } else {
                        filters.forEach { filter ->
                            FilterEntry(filter, 0, revision) { revision++ }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onApply) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    adapter?.resetFilters()
                    revision++
                }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}


/**
 * One filter, and its children if it has any.
 *
 * [depth] only drives indentation. Groups nest one level in practice, but the
 * recursion costs nothing and a source that nests further renders correctly
 * instead of flattening.
 *
 * [revision] is only read as a `remember` key, to let Reset invalidate this
 * row's local state. It's not read directly in the composable body, so a tap
 * on a *different* row (which doesn't change revision) never touches this one.
 */
@Composable
private fun FilterEntry(filter: Filter<*>, depth: Int, revision: Int, onChange: () -> Unit) {
    val indent = (depth * 12).dp

    when (filter) {
        is Filter.Header -> Text(
            filter.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = indent, top = 12.dp, bottom = 4.dp)
        )

        is Filter.Separator -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        is Filter.CheckBox -> {
            var checked by remember(filter, revision) { mutableStateOf(filter.state) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        filter.state = !filter.state
                        checked = filter.state
                        onChange()
                    }
                    .padding(start = indent, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        filter.state = it
                        checked = it
                        onChange()
                    }
                )
                Text(filter.name, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Three states, one tap each: ignored, include, exclude. The glyph
        // carries the meaning because there is no tri-state checkbox in
        // material3 and a checkbox with a third value would read as broken.
        is Filter.TriState -> {
            var state by remember(filter, revision) { mutableStateOf(filter.state) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        filter.state = (filter.state + 1) % 3
                        state = filter.state
                        onChange()
                    }
                    .padding(start = indent, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (state) {
                        Filter.TriState.STATE_INCLUDE -> "\u2713"
                        Filter.TriState.STATE_EXCLUDE -> "\u2717"
                        else -> "\u2013"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state) {
                        Filter.TriState.STATE_INCLUDE -> MaterialTheme.colorScheme.primary
                        Filter.TriState.STATE_EXCLUDE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(filter.name, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is Filter.Text -> {
            var text by remember(filter, revision) { mutableStateOf(filter.state) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    filter.state = it
                    onChange()
                },
                label = { Text(filter.name) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, top = 4.dp, bottom = 4.dp)
            )
        }

        is Filter.Select<*> -> {
            var selected by remember(filter, revision) { mutableStateOf(filter.state) }
            Text(
                filter.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = indent, top = 8.dp, bottom = 4.dp)
            )
            // Scrolls rather than wraps: FlowRow is still experimental on this
            // Compose version, same as the genre chips and the reader's sheet.
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = indent),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filter.values.forEachIndexed { index, value ->
                    FilterChip(
                        selected = selected == index,
                        onClick = {
                            filter.state = index
                            selected = index
                            onChange()
                        },
                        label = { Text(value.toString()) }
                    )
                }
            }
        }

        is Filter.Sort -> {
            var selection by remember(filter, revision) { mutableStateOf(filter.state) }
            Text(
                filter.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = indent, top = 8.dp, bottom = 4.dp)
            )
            filter.values.forEachIndexed { index, value ->
                val active = selection?.index == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Tapping the active row flips direction; tapping a
                            // different one selects it descending, which is what
                            // "sort by this" almost always means.
                            val newSelection = if (active) {
                                Filter.Sort.Selection(index, !(selection?.ascending ?: false))
                            } else {
                                Filter.Sort.Selection(index, false)
                            }
                            filter.state = newSelection
                            selection = newSelection
                            onChange()
                        }
                        .padding(start = indent, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (active) {
                            if (selection?.ascending == true) "\u2191" else "\u2193"
                        } else "\u2003",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is Filter.Group<*> -> {
            Text(
                filter.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = indent, top = 12.dp, bottom = 4.dp)
            )
            // A group's state is its children. They're declared as List<V>, so
            // the type has to be recovered before they can be drawn.
            (filter.state as? List<*>)
                ?.filterIsInstance<Filter<*>>()
                ?.forEach { child -> FilterEntry(child, depth + 1, revision, onChange) }
        }

        else -> Unit
    }
}
