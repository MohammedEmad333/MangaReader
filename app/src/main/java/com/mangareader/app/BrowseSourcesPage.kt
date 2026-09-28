package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowseSourcesPage(
    query: String,
    onQueryChange: (String) -> Unit,
    pinnedOnly: Boolean,
    onPinnedOnlyChange: (Boolean) -> Unit,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    onClearView: () -> Unit,
    visibleCount: Int,
    totalCount: Int,
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
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text("Search sources") },
            placeholder = { Text("Source name or language") },
            trailingIcon = {
                if (query.isNotBlank()) {
                    TextButton(onClick = { onQueryChange("") }) {
                        Text("Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = pinnedOnly,
                onClick = { onPinnedOnlyChange(!pinnedOnly) },
                label = { Text("Pinned only") },
            )
            listOf("All", "Manga", "Anime").forEach { label ->
                FilterChip(
                    selected = mediaFilter == label,
                    onClick = { onMediaFilterChange(label) },
                    label = { Text(label) },
                )
            }
            if (sourcesViewIsActive(query, pinnedOnly, mediaFilter)) {
                TextButton(onClick = onClearView) {
                    Text("Clear view")
                }
            }
            Text(
                sourceVisibilitySummaryLabel(visibleCount, totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    val sourcesListState = rememberRestoredListState(
        scroll,
        "sources",
        ordering,
    )
    val sortedGroups = remember(groups) {
        groups.map { (lang, rows) ->
            lang to rows.sortedBy { it.name.lowercase() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        LazyColumn(
            state = sourcesListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (lastUsedRow != null) {
                item(key = "sources:last-header", contentType = "header") { SectionHeader("Last used") }
                item(
                    key = "sources:last:${lastUsedRow.id}",
                    contentType = "source",
                ) {
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
                item(key = "sources:pinned-header", contentType = "header") { SectionHeader("Pinned") }
                items(
                    pinnedRows,
                    key = { "sources:pinned:${it.id}" },
                    contentType = { "source" },
                ) { row ->
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

            sortedGroups.forEach { (lang, rowsInGroup) ->
                item(
                    key = "sources:group-header:$lang",
                    contentType = "header",
                ) { SectionHeader(lang) }
                items(
                    rowsInGroup,
                    key = { "sources:group:$lang:${it.id}" },
                    contentType = { "source" },
                ) { row ->
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
                item(
                    key = "sources:empty",
                    contentType = "empty",
                ) {
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

            item(
                key = "sources:add",
                contentType = "action",
            ) {
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
}


internal fun sourceVisibilitySummaryLabel(visibleCount: Int, totalCount: Int): String =
    visibleCount.coerceAtLeast(0).toString() + " of " +
        totalCount.coerceAtLeast(0).toString() + " sources"


internal fun sourcesViewIsActive(
    query: String,
    pinnedOnly: Boolean,
    mediaFilter: String,
): Boolean =
    query.isNotBlank() ||
        pinnedOnly ||
        normalizeSourcesMediaFilter(mediaFilter) != "All"
