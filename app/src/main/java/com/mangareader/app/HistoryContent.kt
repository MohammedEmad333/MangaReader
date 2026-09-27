package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("History", style = MaterialTheme.typography.titleLarge)
        if (historyCount > 0) {
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        label = { Text("Search history") },
        placeholder = { Text("Series, chapter or episode") },
    )

    if (loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    ErrorBanner(error)

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
    HorizontalDivider()
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

    ListItem(
        leadingContent = {
            CoverImage(
                cover = coverModel(entry.coverPath),
                title = entry.title,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(0.7f)
                    .alpha(if (dim) 0.4f else 1f),
            )
        },
        headlineContent = {
            Text(
                entry.title,
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
                )
                EntryBadges(
                    downloaded = downloaded,
                    local = badgeLocal,
                    unread = unread,
                )
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
    )
    HorizontalDivider()
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
                Button(onClick = { onConfirmRemove(pendingRemove) }) {
                    Text("Remove")
                }
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
