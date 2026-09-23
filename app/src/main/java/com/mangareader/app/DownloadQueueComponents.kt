package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadQueueHeader(
    items: List<DownloadItem>,
    paused: Boolean,
    pausedIds: Set<String>,
    onBack: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Text(
            "Download queue",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))

        if (items.isNotEmpty()) {
            val resumeAll =
                !paused && items.all { it.chapterId in pausedIds }

            IconButton(
                onClick = {
                    DownloadService.start(
                        context,
                        when {
                            paused -> DownloadService.ACTION_RESUME
                            resumeAll -> DownloadService.ACTION_RESUME_ALL
                            else -> DownloadService.ACTION_PAUSE
                        },
                    )
                },
            ) {
                Icon(
                    if (paused || resumeAll) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.Pause
                    },
                    contentDescription = when {
                        paused -> "Resume all downloads"
                        resumeAll -> "Release every hold"
                        else -> "Pause all downloads"
                    },
                )
            }

            IconButton(onClick = onCancelAll) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Cancel all downloads",
                )
            }
        }
    }
}

internal fun LazyListScope.downloadFailedSection(
    context: Context,
    failed: List<FailedDownload>,
    queued: List<DownloadItem>,
    queuePaused: Boolean,
) {
    if (failed.isEmpty()) return

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Failed (${failed.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Row {
                TextButton(
                    onClick = {
                        if (DownloadQueue.retryAll(context) > 0) {
                            if (queuePaused) {
                                DownloadQueue.setPaused(context, false)
                            }
                            DownloadService.start(context)
                        }
                    },
                ) {
                    Text("Retry all")
                }
                TextButton(
                    onClick = { DownloadQueue.clearFailed(context) },
                ) {
                    Text("Clear")
                }
            }
        }
    }

    items(
        items = failed,
        key = { "failed:" + it.item.chapterId },
    ) { entry ->
        DownloadFailedRow(
            entry = entry,
            queuePaused = queuePaused,
            context = context,
        )
    }

    if (queued.isNotEmpty()) {
        item {
            Text(
                "Queued (${queued.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 12.dp,
                    bottom = 4.dp,
                ),
            )
        }
    }
}

@Composable
private fun DownloadFailedRow(
    entry: FailedDownload,
    queuePaused: Boolean,
    context: Context,
) {
    ListItem(
        headlineContent = {
            Text(
                entry.item.seriesTitle.ifBlank { entry.item.chapterName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    entry.item.chapterName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        entry.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (
                            DownloadQueue.retry(
                                context,
                                setOf(entry.item.chapterId),
                            ) > 0
                        ) {
                            if (queuePaused) {
                                DownloadQueue.setPaused(context, false)
                            }
                            DownloadService.start(context)
                        }
                    },
                ) {
                    Text("Retry")
                }
                TextButton(
                    onClick = {
                        DownloadQueue.dismissFailed(
                            context,
                            entry.item.chapterId,
                        )
                    },
                ) {
                    Text("Dismiss")
                }
            }
        },
    )
    HorizontalDivider()
}

internal fun LazyListScope.downloadQueuedSection(
    context: Context,
    items: List<DownloadItem>,
    activeId: String?,
    queuePaused: Boolean,
    pausedIds: Set<String>,
) {
    items(
        items = items,
        key = { it.chapterId },
    ) { item ->
        val isActive = item.chapterId == activeId
        val itemPaused = item.chapterId in pausedIds
        val progress = DownloadQueue.progress[item.chapterId]

        DownloadQueuedRow(
            context = context,
            item = item,
            isActive = isActive,
            itemPaused = itemPaused,
            queuePaused = queuePaused,
            progress = progress,
        )
    }
}

@Composable
private fun DownloadQueuedRow(
    context: Context,
    item: DownloadItem,
    isActive: Boolean,
    itemPaused: Boolean,
    queuePaused: Boolean,
    progress: DownloadQueue.DownloadProgress?,
) {
    ListItem(
        headlineContent = {
            Text(
                item.seriesTitle.ifBlank { item.chapterName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    item.chapterName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isActive) {
                    val bar = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                    val percent = progress?.percent

                    if (percent == null) {
                        LinearProgressIndicator(modifier = bar)
                    } else {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = bar,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    downloadQueueStatus(
                        isActive = isActive,
                        itemPaused = itemPaused,
                        queuePaused = queuePaused,
                        progress = progress,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                IconButton(
                    onClick = {
                        DownloadService.start(
                            context,
                            if (itemPaused) {
                                DownloadService.ACTION_RESUME_ITEM
                            } else {
                                DownloadService.ACTION_PAUSE_ITEM
                            },
                            item.chapterId,
                        )
                    },
                ) {
                    Icon(
                        if (itemPaused) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Pause
                        },
                        contentDescription = if (itemPaused) {
                            "Resume this chapter"
                        } else {
                            "Pause this chapter"
                        },
                    )
                }

                IconButton(
                    onClick = {
                        DownloadQueue.remove(context, item.chapterId)
                        if (isActive) {
                            DownloadService.start(
                                context,
                                DownloadService.ACTION_SKIP,
                                item.chapterId,
                            )
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove from queue",
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

private fun downloadQueueStatus(
    isActive: Boolean,
    itemPaused: Boolean,
    queuePaused: Boolean,
    progress: DownloadQueue.DownloadProgress?,
): String = when {
    itemPaused -> "On hold"
    !isActive && queuePaused -> "Paused"
    !isActive -> "Queued"
    progress == null -> "Starting"
    progress.total == null -> "Fetching pages"
    progress.total == 0 -> "No pages"
    else -> "${progress.ready} of ${progress.total}"
}

@Composable
internal fun DownloadCancelAllDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel all downloads?") },
        text = {
            Text(
                "Everything in the queue is dropped, including the chapter " +
                    "being downloaded now. Pages already saved stay on disk, " +
                    "so queuing a chapter again resumes rather than restarts.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Cancel all")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep")
            }
        },
    )
}
