package com.mangareader.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private data class ActiveAnimeDownload(
    val id: UUID,
    val title: String,
    val quality: String,
    val stage: String,
    val percent: Int,
    val state: WorkInfo.State,
)

private data class ActiveDirectAnimeDownload(
    val item: PendingDirectAnimeDownload,
    val stage: String,
    val percent: Int,
    val failed: Boolean,
)

private data class DirectDownloadsSnapshot(
    val active: List<ActiveDirectAnimeDownload>,
    val completed: Int,
)

@Composable
internal fun AnimeOfflineDownloadsPanel(query: String) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context.applicationContext) }
    val systemDownloadManager = remember {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    var revision by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<AnimeOfflineItem?>(null) }

    val allItems = remember(revision) { AnimeOfflineIndex.list(context) }
    val needle = query.trim()
    val items = remember(allItems, needle) {
        allItems.filter { item ->
            needle.isBlank() ||
                item.title.contains(needle, ignoreCase = true) ||
                item.quality.contains(needle, ignoreCase = true)
        }
    }

    val activeDownloads by produceState(initialValue = emptyList<ActiveAnimeDownload>(), workManager) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching { workManager.getWorkInfosByTag(AnimeHlsDownloadWorker.TAG).get() }
                    .getOrDefault(emptyList())
                    .filter { info ->
                        info.state == WorkInfo.State.ENQUEUED ||
                            info.state == WorkInfo.State.RUNNING ||
                            info.state == WorkInfo.State.BLOCKED
                    }
                    .map { info ->
                        val baseStage = info.progress.getString(AnimeHlsDownloadWorker.KEY_STAGE)
                            .orEmpty()
                            .ifBlank {
                                when (info.state) {
                                    WorkInfo.State.ENQUEUED -> "Queued"
                                    WorkInfo.State.BLOCKED -> "Waiting"
                                    else -> "Starting"
                                }
                            }
                        val stage = if (
                            info.runAttemptCount > 0 &&
                            (baseStage == "Queued" || baseStage == "Starting" || baseStage == "Preparing")
                        ) {
                            "Retrying · $baseStage"
                        } else {
                            baseStage
                        }
                        ActiveAnimeDownload(
                            id = info.id,
                            title = info.progress.getString(AnimeHlsDownloadWorker.KEY_TITLE)
                                .orEmpty()
                                .ifBlank { "Queued anime download" },
                            quality = info.progress.getString(AnimeHlsDownloadWorker.KEY_QUALITY).orEmpty(),
                            stage = stage,
                            percent = info.progress.getInt(AnimeHlsDownloadWorker.KEY_PERCENT, 0)
                                .coerceIn(0, 100),
                            state = info.state,
                        )
                    }
                    .sortedBy { it.title.lowercase() }
            }
            delay(750)
        }
    }

    val directDownloads by produceState(
        initialValue = emptyList<ActiveDirectAnimeDownload>(),
        systemDownloadManager,
    ) {
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                pollDirectDownloads(context, systemDownloadManager)
            }
            if (snapshot.completed > 0) revision += snapshot.completed
            value = snapshot.active
            delay(750)
        }
    }

    val activeVisible = remember(activeDownloads, needle) {
        activeDownloads.filter { item ->
            needle.isBlank() ||
                item.title.contains(needle, ignoreCase = true) ||
                item.quality.contains(needle, ignoreCase = true)
        }
    }
    val directVisible = remember(directDownloads, needle) {
        directDownloads.filter { download ->
            val item = download.item
            needle.isBlank() ||
                item.title.contains(needle, ignoreCase = true) ||
                item.quality.contains(needle, ignoreCase = true)
        }
    }

    if (allItems.isEmpty() && activeDownloads.isEmpty() && directDownloads.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val totalActive = activeDownloads.size + directDownloads.size
        val totalActiveVisible = activeVisible.size + directVisible.size
        if (totalActive > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Downloading anime", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Active transfers and retry state",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "$totalActiveVisible/$totalActive",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    activeVisible.forEach { item ->
                        DownloadProgressCard(
                            title = item.title,
                            quality = item.quality,
                            stage = item.stage,
                            percent = item.percent,
                            onCancel = { workManager.cancelWorkById(item.id) },
                        )
                    }

                    directVisible.forEach { download ->
                        val item = download.item
                        DownloadProgressCard(
                            title = item.title,
                            quality = item.quality,
                            stage = download.stage,
                            percent = download.percent,
                            onCancel = {
                                systemDownloadManager.remove(item.id)
                                AnimeDirectDownloadIndex.remove(context, item.id)
                                File(item.path).delete()
                            },
                            onRetry = if (download.failed) {
                                {
                                    AnimeDirectDownloadReconciler.retryFailed(context, item)
                                    revision++
                                }
                            } else {
                                null
                            },
                            onDelete = if (download.failed) {
                                {
                                    systemDownloadManager.remove(item.id)
                                    AnimeDirectDownloadIndex.remove(context, item.id)
                                    File(item.path).delete()
                                    revision++
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (allItems.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Offline anime", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Ready to play without a connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${items.size}/${allItems.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (items.isEmpty()) {
                        Text(
                            "No offline episodes match this search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        items.forEach { item ->
                            val file = File(item.path)
                            val kind = if (item.path.endsWith(".m3u8", ignoreCase = true)) {
                                "HLS offline"
                            } else {
                                "Video file"
                            }
                            val quality = item.quality.takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty()

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (file.exists()) {
                                                context.startActivity(
                                                    VideoPlayerActivity.intent(
                                                        context = context,
                                                        url = Uri.fromFile(file).toString(),
                                                        resumeKey = "offline:${item.path}",
                                                    ),
                                                )
                                            }
                                        }
                                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "$kind$quality",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = item }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete offline episode",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val item = pendingDelete
    if (item != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete offline episode?") },
            text = { Text("\"${item.title}\" will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AnimeOfflineIndex.deleteFiles(context, item)
                        pendingDelete = null
                        revision++
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DownloadProgressCard(
    title: String,
    quality: String,
    stage: String,
    percent: Int,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val qualityLabel = quality.takeIf { it.isNotBlank() }
                    ?.let { " · $it" }
                    .orEmpty()
                Text(
                    "$stage · $percent%$qualityLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (onRetry != null && onDelete != null) {
                Row {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry anime download",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete failed anime download",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel anime download",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun pollDirectDownloads(
    context: Context,
    manager: DownloadManager,
): DirectDownloadsSnapshot {
    val active = mutableListOf<ActiveDirectAnimeDownload>()
    var completed = 0

    AnimeDirectDownloadIndex.list(context).forEach { item ->
        val cursor = manager.query(DownloadManager.Query().setFilterById(item.id))
        cursor.use {
            if (!it.moveToFirst()) {
                AnimeDirectDownloadIndex.remove(context, item.id)
                File(item.path).delete()
                return@forEach
            }

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                0
            }

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = File(item.path)
                    if (file.exists() && file.length() > 0L) {
                        AnimeOfflineIndex.record(
                            context,
                            AnimeOfflineItem(
                                title = item.title,
                                path = item.path,
                                sourceUrl = item.sourceUrl,
                                quality = item.quality,
                                downloadedAt = System.currentTimeMillis(),
                            ),
                        )
                        AnimeDirectDownloadIndex.remove(context, item.id)
                        completed++
                    } else {
                        active += ActiveDirectAnimeDownload(
                            item = item,
                            stage = "Finishing",
                            percent = 100,
                            failed = false,
                        )
                    }
                }
                DownloadManager.STATUS_FAILED -> active += ActiveDirectAnimeDownload(
                    item = item,
                    stage = "Failed · tap retry or delete",
                    percent = percent,
                    failed = true,
                )
                DownloadManager.STATUS_PAUSED -> active += ActiveDirectAnimeDownload(
                    item = item,
                    stage = if (item.retryCount > 0) "Paused while retrying" else "Paused",
                    percent = percent,
                    failed = false,
                )
                DownloadManager.STATUS_RUNNING -> active += ActiveDirectAnimeDownload(
                    item = item,
                    stage = if (item.retryCount > 0) "Retrying file" else "Downloading file",
                    percent = percent,
                    failed = false,
                )
                else -> active += ActiveDirectAnimeDownload(
                    item = item,
                    stage = if (item.retryCount > 0) "Retry queued" else "Queued",
                    percent = percent,
                    failed = false,
                )
            }
        }
    }

    return DirectDownloadsSnapshot(
        active = active.sortedBy { it.item.title.lowercase() },
        completed = completed,
    )
}
