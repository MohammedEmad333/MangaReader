package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTopControls(
    selecting: Boolean,
    selectedCount: Int,
    visibleIds: List<String>,
    search: String,
    searchOpen: Boolean,
    mediaFilter: String,
    groups: List<LibraryGroupView>,
    currentPage: Int,
    showTabs: Boolean,
    showCount: Boolean,
    filterActive: Boolean,
    visibleCount: Int,
    totalCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onAssignCategories: () -> Unit,
    onRemoveSelection: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchOpenChange: (Boolean) -> Unit,
    onOpenOptions: () -> Unit,
    onMediaFilterChange: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    var bulkMenuOpen by remember { mutableStateOf(false) }
    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    if (selecting) {
        TopAppBar(
            title = {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
            },
            actions = {
                IconButton(onClick = { onSelectAll(visibleIds) }) {
                    Icon(Icons.Default.Check, contentDescription = "Select all")
                }
                IconButton(onClick = onAssignCategories) {
                    Icon(Icons.Default.Edit, contentDescription = "Change categories")
                }
                IconButton(onClick = onRemoveSelection) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove from library")
                }
                Box {
                    IconButton(onClick = { bulkMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = bulkMenuOpen,
                        onDismissRequest = { bulkMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mark as read") },
                            onClick = {
                                bulkMenuOpen = false
                                onMarkRead()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as unread") },
                            onClick = {
                                bulkMenuOpen = false
                                onMarkUnread()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = {
                                bulkMenuOpen = false
                                onDownload()
                            },
                        )
                    }
                }
            },
            colors = topBarColors,
        )
    } else {
        TopAppBar(
            title = {
                if (searchOpen) {
                    TextField(
                        value = search,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search your library") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Column {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = libraryVisibleSummaryLabel(visibleCount, totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (searchOpen) onSearchChange("")
                        onSearchOpenChange(!searchOpen)
                    },
                ) {
                    Icon(
                        if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchOpen) "Close search" else "Search",
                    )
                }
                IconButton(onClick = onOpenOptions) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter, sort and display options",
                        tint = if (filterActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
            },
            colors = topBarColors,
        )
    }

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
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }

    if (groups.size > 1 && showTabs) {
        ScrollableTabRow(
            selectedTabIndex = currentPage.coerceIn(0, groups.size - 1),
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            groups.forEachIndexed { index, group ->
                Tab(
                    selected = index == currentPage,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            if (showCount) "${group.label} (${group.items.size})" else group.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

internal fun libraryVisibleSummaryLabel(visibleCount: Int, totalCount: Int): String =
    "Showing " + visibleCount.coerceAtLeast(0) + " of " + totalCount.coerceAtLeast(0)
