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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// PullToRefreshBox lives in a SUB-PACKAGE of material3, which the wildcard
// elsewhere does not reach and which the 0.98 CI failure was about.
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val pausedIds = DownloadQueue.pausedIds
    var confirmCancelAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Text(
                "Download queue",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                // Three states, matching the notification. Keyed on `paused`
                // alone this was a dead loop: with every chapter held the
                // button offered Pause, which set the queue-wide pause, whose
                // Resume returned to all-held. It toggled a mechanism that was
                // not the one holding the queue.
                val resumeAll = !paused && items.all { it.chapterId in pausedIds }
                IconButton(onClick = {
                    DownloadService.start(
                        context,
                        when {
                            paused -> DownloadService.ACTION_RESUME
                            resumeAll -> DownloadService.ACTION_RESUME_ALL
                            else -> DownloadService.ACTION_PAUSE
                        }
                    )
                }) {
                    Icon(
                        if (paused || resumeAll) Icons.Default.PlayArrow
                        else Icons.Default.Pause,
                        contentDescription = when {
                            paused -> "Resume all downloads"
                            resumeAll -> "Release every hold"
                            else -> "Pause all downloads"
                        }
                    )
                }
                // Confirmed, unlike before: this discards the whole queue and
                // there is no undo. Every other destructive action in this app
                // asks first — see 0.44's rule about delete buttons.
                IconButton(onClick = { confirmCancelAll = true }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Cancel all downloads"
                    )
                }
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
                                // Selectable so the failing URL can be copied
                                // out — it's the whole point of capturing it.
                                SelectionContainer {
                                    Text(
                                        entry.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
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
                val itemPaused = item.chapterId in pausedIds
                val progress = DownloadQueue.progress[item.chapterId]

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
                                // Indeterminate only while there is no ratio to
                                // be had — the page list hasn't landed, or it
                                // landed empty. A real 0 of N now draws an empty
                                // determinate bar, because "the source answered
                                // and nothing is arriving" is a different thing
                                // to show than "the source hasn't answered".
                                val percent = progress?.percent
                                if (percent == null) {
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
                                // Four active states where there used to be
                                // two labels. "Starting" now means only the
                                // gap before the service picks the chapter up;
                                // "Fetching pages" is the page list request
                                // outstanding; a ratio means it came back and
                                // says how much has landed. They have different
                                // causes, so they read differently.
                                // "On hold" is this chapter's own pause and
                                // "Paused" is the queue-wide one. Two mechanisms
                                // can hold a row now, and one word for both would
                                // be the mistake this screen just finished
                                // undoing.
                                when {
                                    itemPaused -> "On hold"
                                    !isActive && paused -> "Paused"
                                    !isActive -> "Queued"
                                    progress == null -> "Starting"
                                    progress.total == null -> "Fetching pages"
                                    progress.total == 0 -> "No pages"
                                    else -> "${progress.ready} of ${progress.total}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = {
                                DownloadService.start(
                                    context,
                                    if (itemPaused) DownloadService.ACTION_RESUME_ITEM
                                    else DownloadService.ACTION_PAUSE_ITEM,
                                    item.chapterId
                                )
                            }) {
                                Icon(
                                    if (itemPaused) Icons.Default.PlayArrow
                                    else Icons.Default.Pause,
                                    contentDescription = if (itemPaused) {
                                        "Resume this chapter"
                                    } else {
                                        "Pause this chapter"
                                    }
                                )
                            }
                            IconButton(onClick = {
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
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove from queue"
                                )
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmCancelAll) {
        AlertDialog(
            onDismissRequest = { confirmCancelAll = false },
            title = { Text("Cancel all downloads?") },
            text = {
                Text(
                    "Everything in the queue is dropped, including the chapter " +
                        "being downloaded now. Pages already saved stay on disk, " +
                        "so queuing a chapter again resumes rather than restarts."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadService.start(context, DownloadService.ACTION_CANCEL_ALL)
                    confirmCancelAll = false
                }) { Text("Cancel all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelAll = false }) { Text("Keep") }
            }
        )
    }
}
