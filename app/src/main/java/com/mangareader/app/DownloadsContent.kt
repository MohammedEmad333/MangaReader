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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadsHeader(
    seriesCount: Int,
    totalSize: Long,
    queued: Int,
    failedCount: Int,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Downloads", style = MaterialTheme.typography.titleLarge)
            if (seriesCount > 0) {
                Text(
                    "${seriesCount} series · ${formatBytes(totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onOpenQueue) {
            Text(
                when {
                    failedCount > 0 && queued > 0 -> "Queue (${queued}, ${failedCount} failed)"
                    failedCount > 0 -> "Queue (${failedCount} failed)"
                    queued > 0 -> "Queue (${queued})"
                    else -> "Queue"
                },
            )
        }
    }
    HorizontalDivider()
}

@Composable
internal fun DownloadsSeriesRow(
    entry: DownloadedSeries,
    dim: Boolean,
    badgeLocal: Boolean,
    unread: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = {
            CoverImage(
                cover = entry.cover.ifBlank { null },
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
                Text("${entry.chapters.size} ${unit} · ${formatBytes(entry.sizeBytes)}")
                EntryBadges(
                    downloaded = false,
                    local = badgeLocal,
                    unread = unread,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete downloads",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
    HorizontalDivider()
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
            TextButton(onClick = { onConfirm(entry) }) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
