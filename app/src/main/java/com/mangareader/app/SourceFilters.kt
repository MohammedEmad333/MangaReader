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
 * **That last sentence was false from the start and it is why the sheet felt
 * slow.** Every row was handed an `onChange` callback wired to `revision++`, so
 * one checkbox tap bumped the counter, invalidated *every* row's
 * `remember(filter, revision)`, and recomposed the whole list — dozens of rows
 * for a genre group, per tap. The callback did nothing else: Reset bumps
 * [revision] itself and Apply reads `filter.state` directly, so nothing between
 * taps needed notifying. It is removed, and a tap now recomposes only its row,
 * which is what the paragraph above always claimed.
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
                            FilterEntry(filter, 0, revision)
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
