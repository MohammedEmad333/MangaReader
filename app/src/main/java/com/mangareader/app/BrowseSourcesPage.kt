package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowseSourcesPage(
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    lastUsedRow: BrowseRow?,
    pinnedRows: List<BrowseRow>,
    groups: List<Pair<String, List<BrowseRow>>>,
    visibleRowsEmpty: Boolean,
    pinnedIds: Set<String>,
    onTogglePin: (BrowseRow) -> Unit,
    onOpen: (BrowseRow) -> Unit,
    onEdit: (SourceConfig) -> Unit,
    onDelete: (SourceConfig) -> Unit,
    onOpenSettings: (Source) -> Unit,
    onAdd: () -> Unit,
    scroll: ScrollMemory,
    ordering: Any?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("All", "Manga", "Anime").forEach { label ->
            FilterChip(
                selected = mediaFilter == label,
                onClick = { onMediaFilterChange(label) },
                label = { Text(label) },
            )
        }
    }

    val sourcesListState = rememberRestoredListState(
        scroll,
        "sources",
        ordering,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = sourcesListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (lastUsedRow != null) {
                item { SectionHeader("Last used") }
                item {
                    BrowseSourceRow(
                        row = lastUsedRow,
                        pinned = lastUsedRow.id in pinnedIds,
                        onOpen = { onOpen(lastUsedRow) },
                        onTogglePin = { onTogglePin(lastUsedRow) },
                        onEditConfig = onEdit,
                        onDeleteConfig = onDelete,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            if (pinnedRows.isNotEmpty()) {
                item { SectionHeader("Pinned") }
                items(pinnedRows) { row ->
                    BrowseSourceRow(
                        row = row,
                        pinned = true,
                        onOpen = { onOpen(row) },
                        onTogglePin = { onTogglePin(row) },
                        onEditConfig = onEdit,
                        onDeleteConfig = onDelete,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            groups.forEach { (lang, rowsInGroup) ->
                item { SectionHeader(lang) }
                items(rowsInGroup.sortedBy { it.name.lowercase() }) { row ->
                    BrowseSourceRow(
                        row = row,
                        pinned = false,
                        onOpen = { onOpen(row) },
                        onTogglePin = { onTogglePin(row) },
                        onEditConfig = onEdit,
                        onDeleteConfig = onDelete,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            if (visibleRowsEmpty) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No sources yet. Add a local folder, or install " +
                                "extensions from the Extensions tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add a local source")
                    }
                }
            }
        }

        ListScrollHandle(
            state = sourcesListState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
