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
internal fun DownloadFailedRow(
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
