package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LazyListScope.downloadQueuedSection(
    context: Context,
    items: List<DownloadItem>,
    activeId: String?,
    queuePaused: Boolean,
    pausedIds: Set<String>,
    scope: CoroutineScope,
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
            scope = scope,
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
    scope: CoroutineScope,
) {
    val status = downloadQueueStatus(
        isActive = isActive,
        itemPaused = itemPaused,
        queuePaused = queuePaused,
        progress = progress,
    )
    val statusContainer = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        itemPaused || queuePaused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusContent = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        itemPaused || queuePaused -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isActive) 2.dp else 1.dp,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    item.seriesTitle.ifBlank { item.chapterName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        item.chapterName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = statusContainer,
                        contentColor = statusContent,
                    ) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    if (isActive) {
                        val bar = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
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
                            if (itemPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (itemPaused) {
                                "Resume this chapter"
                            } else {
                                "Pause this chapter"
                            },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(
                        onClick = {
                            val appContext = context.applicationContext
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    DownloadQueue.remove(appContext, item.chapterId)
                                }
                                if (isActive) {
                                    DownloadService.start(
                                        context,
                                        DownloadService.ACTION_SKIP,
                                        item.chapterId,
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove from queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
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
