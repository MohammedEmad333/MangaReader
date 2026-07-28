package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Downloads tab: series with chapters saved to permanent storage.
 *
 * Built from [DownloadIndex] rather than from the library, because the two are
 * different sets — a chapter can be downloaded without the series ever being
 * saved, and a saved series usually has nothing downloaded at all.
 */
@Composable
internal fun DownloadsTab(
    downloadTick: Int,
    onOpen: (DownloadedSeries) -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current

    // Deletes made here don't go through the service, so they wouldn't move
    // DownloadQueue.tick; a local counter covers that without pushing a callback
    // back up to YomuApp for something no other screen cares about.
    var localTick by remember { mutableIntStateOf(0) }
    val revision = downloadTick + localTick

    // Keyed on the revision so a chapter finishing, or a delete from anywhere
    // else, re-reads rather than showing a stale list.
    val series = remember(revision) { DownloadIndex.list(context) }
    val totalSize = remember(series) { series.sumOf { it.sizeBytes } }
    var confirmDelete by remember { mutableStateOf<DownloadedSeries?>(null) }

    val queued = DownloadQueue.items.size
    val failedCount = DownloadQueue.failed.size

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Downloads", style = MaterialTheme.typography.titleLarge)
                if (series.isNotEmpty()) {
                    Text(
                        "${series.size} series \u00b7 ${formatBytes(totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onOpenQueue) {
                Text(
                    when {
                        failedCount > 0 && queued > 0 -> "Queue ($queued, $failedCount failed)"
                        failedCount > 0 -> "Queue ($failedCount failed)"
                        queued > 0 -> "Queue ($queued)"
                        else -> "Queue"
                    }
                )
            }
        }
        HorizontalDivider()

        if (series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing downloaded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(series, key = { it.seriesId }) { entry ->
                    ListItem(
                        leadingContent = {
                            CoverImage(
                                cover = entry.cover.ifBlank { null },
                                title = entry.title,
                                modifier = Modifier
                                    .width(44.dp)
                                    .aspectRatio(0.7f)
                            )
                        },
                        headlineContent = {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                "${entry.chapters.size} " +
                                    (if (entry.chapters.size == 1) "chapter" else "chapters") +
                                    " \u00b7 ${formatBytes(entry.sizeBytes)}"
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { confirmDelete = entry }) { Text("Delete") }
                        },
                        modifier = Modifier.clickable { onOpen(entry) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete downloads?") },
            text = {
                Text(
                    "${entry.chapters.size} downloaded " +
                        (if (entry.chapters.size == 1) "chapter" else "chapters") +
                        " of \"${entry.title}\" will be removed from this device. " +
                        "Reading progress is kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadIndex.deleteSeries(context, entry)
                    localTick++
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * The download queue, plus anything that failed out of it.
 *
 * Reads [DownloadQueue] directly rather than taking its state as parameters. The
 * queue is process-wide and the service writes to it from a background thread, so
 * there is nothing for `YomuApp` to usefully hoist — threading it through as
 * arguments would only add a hop between the writer and the reader. Actions go
 * straight back to the service.
 */
@Composable
internal fun DownloadQueueScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val items = DownloadQueue.items
    val failed = DownloadQueue.failed
    val activeId = DownloadQueue.activeId
    val paused = DownloadQueue.paused

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "Download queue",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                TextButton(onClick = {
                    DownloadService.start(
                        context,
                        if (paused) DownloadService.ACTION_RESUME
                        else DownloadService.ACTION_PAUSE
                    )
                }) { Text(if (paused) "Resume" else "Pause") }
                TextButton(onClick = {
                    DownloadService.start(context, DownloadService.ACTION_CANCEL_ALL)
                }) { Text("Cancel all") }
            }
        }
        HorizontalDivider()

        if (items.isEmpty() && failed.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing in the queue.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (failed.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Failed (${failed.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row {
                            TextButton(onClick = {
                                if (DownloadQueue.retryAll(context) > 0) {
                                    if (paused) DownloadQueue.setPaused(context, false)
                                    DownloadService.start(context)
                                }
                            }) { Text("Retry all") }
                            TextButton(onClick = { DownloadQueue.clearFailed(context) }) {
                                Text("Clear")
                            }
                        }
                    }
                }

                items(failed, key = { "failed:" + it.item.chapterId }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(
                                entry.item.seriesTitle.ifBlank { entry.item.chapterName },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    entry.item.chapterName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    entry.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    // Pages that did land are still on disk, so
                                    // this resumes rather than starting over.
                                    if (DownloadQueue.retry(
                                            context,
                                            setOf(entry.item.chapterId)
                                        ) > 0
                                    ) {
                                        if (paused) DownloadQueue.setPaused(context, false)
                                        DownloadService.start(context)
                                    }
                                }) { Text("Retry") }
                                TextButton(onClick = {
                                    DownloadQueue.dismissFailed(context, entry.item.chapterId)
                                }) { Text("Dismiss") }
                            }
                        }
                    )
                    HorizontalDivider()
                }

                if (items.isNotEmpty()) {
                    item {
                        Text(
                            "Queued (${items.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            items(items, key = { it.chapterId }) { item ->
                val isActive = item.chapterId == activeId
                val percent = DownloadQueue.progress[item.chapterId]

                ListItem(
                    headlineContent = {
                        Text(
                            item.seriesTitle.ifBlank { item.chapterName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                item.chapterName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isActive) {
                                val bar = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                // No percent yet means the page list request
                                // hasn't come back, so there's no ratio to show —
                                // an indeterminate bar is the honest one until the
                                // page count is known.
                                if (percent == null || percent == 0) {
                                    LinearProgressIndicator(modifier = bar)
                                } else {
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = bar
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    isActive && percent != null && percent > 0 -> "$percent%"
                                    isActive -> "Starting"
                                    paused -> "Paused"
                                    else -> "Queued"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = {
                                DownloadQueue.remove(context, item.chapterId)
                                // Removing the one being fetched has to reach the
                                // service too, or it carries on downloading a
                                // chapter no longer listed.
                                if (isActive) {
                                    DownloadService.start(
                                        context,
                                        DownloadService.ACTION_SKIP,
                                        item.chapterId
                                    )
                                }
                            }) { Text("Remove") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
