package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LibraryEmpty(
    allEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (allEmpty) "Your library is empty." else "Nothing here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (allEmpty) {
                "Open a series from Browse and tap “Add to library”."
            } else {
                "Nothing matches the current filters."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryGrid(
    shown: List<LibraryEntry>,
    allEmpty: Boolean,
    scrollKey: String,
    scroll: ScrollMemory,
    ordering: Any?,
    display: LibraryDisplay,
    perRow: Int,
    readIds: Set<String>,
    downloadedIds: Set<String>,
    badgeLocal: Boolean,
    unreadCounts: Map<String, Int>,
    selected: Set<String>,
    selecting: Boolean,
    onOpen: (LibraryEntry) -> Unit,
    onToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shown.isEmpty()) {
        LibraryEmpty(allEmpty = allEmpty, modifier = modifier)
        return
    }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) refreshing = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        if (display == LibraryDisplay.LIST) {
            LazyColumn(
                state = rememberRestoredListState(
                    scroll,
                    "$scrollKey#list",
                    ordering,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            ) {
                items(shown, key = { it.seriesId }) { entry ->
                    val isSelected = entry.seriesId in selected
                    LibraryListEntryRow(
                        entry = entry,
                        isSelected = isSelected,
                        dim = entry.seriesId in readIds && !isSelected,
                        downloaded = entry.seriesId in downloadedIds,
                        badgeLocal = badgeLocal && entry.sourceId.isLocalSourceId(),
                        unread = unreadCounts[entry.seriesId],
                        selecting = selecting,
                        onOpen = { onOpen(entry) },
                        onToggle = { onToggle(entry.seriesId) },
                    )
                }
            }
        } else {
            val gridState = rememberRestoredGridState(
                scroll,
                "$scrollKey#grid",
                ordering,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = if (perRow > 0) {
                        GridCells.Fixed(perRow)
                    } else {
                        GridCells.Adaptive(minSize = 110.dp)
                    },
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.seriesId }) { entry ->
                        val isSelected = entry.seriesId in selected
                        LibraryGridEntryCard(
                            entry = entry,
                            display = display,
                            isSelected = isSelected,
                            dim = entry.seriesId in readIds && !isSelected,
                            downloaded = entry.seriesId in downloadedIds,
                            badgeLocal = badgeLocal && entry.sourceId.isLocalSourceId(),
                            unread = unreadCounts[entry.seriesId],
                            selecting = selecting,
                            onOpen = { onOpen(entry) },
                            onToggle = { onToggle(entry.seriesId) },
                        )
                    }
                }

                GridScrollHandle(
                    state = gridState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}
