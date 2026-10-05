package com.mangareader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadsHeader(
    seriesCount: Int,
    downloadCount: Int,
    totalSize: Long,
    queued: Int,
    failedCount: Int,
    onOpenQueue: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (seriesCount > 0) {
                            "$seriesCount series · $downloadCount downloads · ${formatBytes(totalSize)}"
                        } else {
                            "Your offline manga and anime"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onOpenQueue) {
                    Text(
                        when {
                            failedCount > 0 && queued > 0 -> "$queued queued · $failedCount failed"
                            failedCount > 0 -> "$failedCount failed"
                            queued > 0 -> "$queued queued"
                            else -> "Queue"
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DownloadsTools(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: DownloadsSortMode,
    onSortModeChange: (DownloadsSortMode) -> Unit,
    descending: Boolean,
    onDescendingChange: (Boolean) -> Unit,
    mediaFilter: DownloadsMediaFilter,
    onMediaFilterChange: (DownloadsMediaFilter) -> Unit,
    onClear: () -> Unit,
    shownCount: Int,
    totalCount: Int,
    shownDownloadCount: Int,
    shownSize: Long,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search downloads") },
                placeholder = { Text("Series or chapter name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadsMediaFilter.entries.forEach { option ->
                    FilterChip(
                        selected = mediaFilter == option,
                        onClick = { onMediaFilterChange(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadsSortMode.entries.forEach { option ->
                    FilterChip(
                        selected = sortMode == option,
                        onClick = { onSortModeChange(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
                TextButton(onClick = { onDescendingChange(!descending) }) {
                    Text(if (descending) "Descending ↓" else "Ascending ↑")
                }
                if (downloadsViewIsActive(query, sortMode, descending, mediaFilter)) {
                    TextButton(onClick = onClear) { Text("Reset") }
                }
            }
            if (query.isNotBlank() || mediaFilter != DownloadsMediaFilter.ALL) {
                Text(
                    "Showing $shownCount of $totalCount · $shownDownloadCount downloads · ${formatBytes(shownSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mediaFilter == DownloadsMediaFilter.ANIME) {
                AnimeOfflineDownloadsPanel(query = query)
            }
        }
    }
}

@Composable
internal fun DownloadsSeriesRow(
    entry: DownloadedSeries,
    dim: Boolean,
    badgeLocal: Boolean,
    unread: Int?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
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
                    cover = entry.cover.ifBlank { null },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isAnime = entry.sourceId.isAnimeExtensionSourceId()
                    val unit = when {
                        isAnime && entry.chapters.size == 1 -> "episode"
                        isAnime -> "episodes"
                        entry.chapters.size == 1 -> "chapter"
                        else -> "chapters"
                    }
                    Text(
                        "${entry.chapters.size} $unit · ${formatBytes(entry.sizeBytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EntryBadges(downloaded = false, local = badgeLocal, unread = unread)
                }
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete downloads",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

@Composable
internal fun DownloadsDeleteDialog(
    entry: DownloadedSeries?,
    onDismiss: () -> Unit,
    onConfirm: (DownloadedSeries) -> Unit,
) {
    if (entry == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete downloads?") },
        text = {
            Text(
                "${entry.chapters.size} downloaded " +
                    (if (entry.chapters.size == 1) "chapter" else "chapters") +
                    " of \"${entry.title}\" will be removed from this device. " +
                    "Reading progress is kept.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(entry) }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun downloadsViewIsActive(
    query: String,
    sortMode: DownloadsSortMode,
    descending: Boolean,
    mediaFilter: DownloadsMediaFilter,
): Boolean =
    query.isNotBlank() ||
        sortMode != DownloadsSortMode.SIZE ||
        !descending ||
        mediaFilter != DownloadsMediaFilter.ALL
