package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryHeader(
    historyCount: Int,
    loading: Boolean,
    error: String?,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearView: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (historyCount == 0) "Recently read and watched items"
                    else "$historyCount recent ${if (historyCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (historyCount > 0) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search history") },
                    placeholder = { Text("Series, chapter or episode") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = MaterialTheme.shapes.large,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    if (historyViewIsActive(mediaFilter, searchQuery)) {
                        TextButton(onClick = onClearView) { Text("Reset") }
                    }
                }
            }
        }
    }

    if (loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    ErrorBanner(error)
}

@Composable
internal fun HistoryEntryRow(
    entry: HistoryEntry,
    dim: Boolean,
    downloaded: Boolean,
    badgeLocal: Boolean,
    unread: Int?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        ListItem(
            leadingContent = {
                CoverImage(
                    cover = coverModel(entry.coverPath),
                    title = entry.title,
                    modifier = Modifier
                        .width(68.dp)
                        .aspectRatio(0.7f)
                        .alpha(if (dim) 0.4f else 1f),
                )
            },
            headlineContent = {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (dim) 0.4f else 1f),
                )
            },
            supportingContent = {
                val supportingText =
                    if (entry.mediaType == "anime") {
                        val position = VideoPlaybackProgress.position(context, entry.chapterKey)
                        val duration = VideoPlaybackProgress.duration(context, entry.chapterKey)
                        val completed = VideoPlaybackProgress.isCompleted(context, entry.chapterKey)
                        buildString {
                            if (entry.detail.isNotBlank()) append(entry.detail)
                            when {
                                completed && position > 0L -> {
                                    if (isNotEmpty()) append(" • ")
                                    append("Watched • Rewatch ")
                                    append(formatMediaTime(position))
                                    if (duration > 0L) append(" / ${formatMediaTime(duration)}")
                                }
                                completed -> {
                                    if (isNotEmpty()) append(" • ")
                                    append("Watched")
                                }
                                position > 0L -> {
                                    if (isNotEmpty()) append(" • ")
                                    append(formatMediaTime(position))
                                    if (duration > 0L) append(" / ${formatMediaTime(duration)}")
                                }
                                else -> {
                                    if (isNotEmpty()) append(" • ")
                                    append("Started")
                                }
                            }
                        }
                    } else if (entry.total > 0) {
                        "Page ${entry.page + 1} of ${entry.total}"
                    } else {
                        "Page ${entry.page + 1}"
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = supportingText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EntryBadges(downloaded = downloaded, local = badgeLocal, unread = unread)
                }
            },
            modifier = Modifier.clickable(onClick = onOpen),
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove from history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
internal fun HistoryDialogs(
    pendingRemove: HistoryEntry?,
    clearAllOpen: Boolean,
    onDismissRemove: () -> Unit,
    onConfirmRemove: (HistoryEntry) -> Unit,
    onDismissClearAll: () -> Unit,
    onConfirmClearAll: () -> Unit,
) {
    if (pendingRemove != null) {
        AlertDialog(
            onDismissRequest = onDismissRemove,
            title = { Text("Remove from history?") },
            text = {
                Text(
                    "“${pendingRemove.title}” leaves the history list. " +
                        if (pendingRemove.mediaType == "anime") {
                            "Playback progress is kept."
                        } else {
                            "The chapter, your read mark and your place in it are untouched."
                        },
                )
            },
            confirmButton = {
                Button(onClick = { onConfirmRemove(pendingRemove) }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemove) { Text("Cancel") }
            },
        )
    }

    if (clearAllOpen) {
        AlertDialog(
            onDismissRequest = onDismissClearAll,
            title = { Text("Clear all history?") },
            text = {
                Text(
                    "Every entry is removed. Reading and playback progress are kept, " +
                        "so only the recent-history list is cleared.",
                )
            },
            confirmButton = {
                Button(onClick = onConfirmClearAll) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearAll) { Text("Cancel") }
            },
        )
    }
}

internal fun historyViewIsActive(mediaFilter: String, searchQuery: String): Boolean =
    normalizeHistoryMediaFilter(mediaFilter) != "All" || searchQuery.isNotBlank()
