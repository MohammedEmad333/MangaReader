package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Filter sheet backed by the extension's live filter objects. Reset replaces the
 * adapter filter list and bumps [revision] so row-local Compose state re-reads it.
 */
@Composable
internal fun SourceFilterDialog(
    source: Source,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val adapter = source as? TachiyomiSourceAdapter
    var revision by remember(source.id) { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Filters")
                Text(
                    "Refine what this source returns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (adapter == null) {
                        Text(
                            "This source has no filters.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val filters = adapter.filterList
                        if (filters.isEmpty()) {
                            Text(
                                "This source has no filters.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            filters.forEach { filter ->
                                FilterEntry(filter, 0, revision)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        adapter?.resetFilters()
                        revision++
                    },
                ) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Individual filter rendering lives in [FilterEntry]. The revision is used only
 * as a remember key for Reset, not as a per-tap invalidation signal.
 */
