package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

internal fun LazyListScope.downloadFailedSection(
    context: Context,
    failed: List<FailedDownload>,
    queued: List<DownloadItem>,
    queuePaused: Boolean,
    scope: CoroutineScope,
) {
    if (failed.isEmpty()) return

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Failed downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "${failed.size} ${if (failed.size == 1) "item needs" else "items need"} attention",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                TextButton(
                    onClick = {
                        val appContext = context.applicationContext
                        scope.launch {
                            val retried = withContext(Dispatchers.IO) {
                                val changed = DownloadQueue.retryAll(appContext)
                                if (changed > 0 && queuePaused) {
                                    DownloadQueue.setPaused(appContext, false)
                                }
                                changed
                            }
                            if (retried > 0) DownloadService.start(context)
                        }
                    },
                ) { Text("Retry all") }
                TextButton(
                    onClick = {
                        val appContext = context.applicationContext
                        scope.launch(Dispatchers.IO) {
                            DownloadQueue.clearFailed(appContext)
                        }
                    },
                ) { Text("Clear") }
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
            scope = scope,
        )
    }

    if (queued.isNotEmpty()) {
        item {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Queued downloads",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${queued.size} ${if (queued.size == 1) "item" else "items"} waiting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DownloadFailedRow(
    entry: FailedDownload,
    queuePaused: Boolean,
    context: Context,
    scope: CoroutineScope,
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
            headlineContent = {
                Text(
                    entry.item.seriesTitle.ifBlank { entry.item.chapterName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            val appContext = context.applicationContext
                            scope.launch {
                                val retried = withContext(Dispatchers.IO) {
                                    val changed = DownloadQueue.retry(
                                        appContext,
                                        setOf(entry.item.chapterId),
                                    )
                                    if (changed > 0 && queuePaused) {
                                        DownloadQueue.setPaused(appContext, false)
                                    }
                                    changed
                                }
                                if (retried > 0) DownloadService.start(context)
                            }
                        },
                    ) { Text("Retry") }
                    TextButton(
                        onClick = {
                            val appContext = context.applicationContext
                            scope.launch(Dispatchers.IO) {
                                DownloadQueue.dismissFailed(appContext, entry.item.chapterId)
                            }
                        },
                    ) { Text("Dismiss") }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
