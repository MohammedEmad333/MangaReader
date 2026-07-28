package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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

/**
 * The download queue.
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

        DownloadQueue.lastError?.let { message ->
            ErrorBanner(message)
            TextButton(onClick = { DownloadQueue.lastError = null }) { Text("Dismiss") }
        }
        HorizontalDivider()

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing in the queue.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                    // hasn't come back, so there's no ratio to
                                    // show — an indeterminate bar is the honest
                                    // one until the page count is known.
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
                                    // Removing the one being fetched has to reach
                                    // the service too, or it carries on
                                    // downloading a chapter no longer listed.
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
}
