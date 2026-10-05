package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadQueueScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items = DownloadQueue.items
    val failed = DownloadQueue.failed
    val activeId = DownloadQueue.activeId
    val paused = DownloadQueue.paused
    val pausedIds = DownloadQueue.pausedIds
    var confirmCancelAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        DownloadQueueHeader(
            items = items,
            paused = paused,
            pausedIds = pausedIds,
            onBack = onBack,
            onCancelAll = { confirmCancelAll = true },
        )

        if (items.isEmpty() && failed.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Nothing in the queue",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "New chapter downloads will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                downloadFailedSection(
                    context = context,
                    failed = failed,
                    queued = items,
                    queuePaused = paused,
                    scope = scope,
                )
                downloadQueuedSection(
                    context = context,
                    items = items,
                    activeId = activeId,
                    queuePaused = paused,
                    pausedIds = pausedIds,
                    scope = scope,
                )
            }
        }
    }

    if (confirmCancelAll) {
        DownloadCancelAllDialog(
            onConfirm = {
                DownloadService.start(
                    context,
                    DownloadService.ACTION_CANCEL_ALL,
                )
                confirmCancelAll = false
            },
            onDismiss = { confirmCancelAll = false },
        )
    }
}
