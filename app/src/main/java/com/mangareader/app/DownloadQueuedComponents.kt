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
internal fun DownloadQueuedRow(
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

internal fun downloadQueueStatus(
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
