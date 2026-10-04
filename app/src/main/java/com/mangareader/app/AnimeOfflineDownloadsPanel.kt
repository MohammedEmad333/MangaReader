package com.mangareader.app

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
internal fun AnimeOfflineDownloadsPanel(query: String) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context.applicationContext) }
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
                        ActiveAnimeDownload(
                            id = info.id,
                            title = info.progress.getString(AnimeHlsDownloadWorker.KEY_TITLE)
                                .orEmpty()
                                .ifBlank { "Queued anime download" },
                            quality = info.progress.getString(AnimeHlsDownloadWorker.KEY_QUALITY).orEmpty(),
                            stage = info.progress.getString(AnimeHlsDownloadWorker.KEY_STAGE)
                                .orEmpty()
                                .ifBlank {
                                    when (info.state) {
                                        WorkInfo.State.ENQUEUED -> "Queued"
                                        WorkInfo.State.BLOCKED -> "Waiting"
                                        else -> "Starting"
                                    }
                                },
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

    val activeVisible = remember(activeDownloads, needle) {
        activeDownloads.filter { item ->
            needle.isBlank() ||
                item.title.contains(needle, ignoreCase = true) ||
                item.quality.contains(needle, ignoreCase = true)
        }
    }

    if (allItems.isEmpty() && activeDownloads.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        if (activeDownloads.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Downloading anime", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${activeVisible.size}/${activeDownloads.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            activeVisible.forEach { item ->
                ListItem(
                    headlineContent = {
                        Text(
                            item.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val quality = item.quality.takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty()
                            Text("${item.stage} · ${item.percent}%$quality")
                            LinearProgressIndicator(
                                progress = { item.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { workManager.cancelWorkById(item.id) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel anime download",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }

        if (allItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Offline anime", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${items.size}/${allItems.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        },
                        headlineContent = {
                            Text(
                                item.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            val kind = if (item.path.endsWith(".m3u8", ignoreCase = true)) {
                                "HLS offline"
                            } else {
                                "Video file"
                            }
                            val quality = item.quality.takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty()
                            Text("$kind$quality")
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = item }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete offline episode",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            if (file.exists()) {
                                context.startActivity(
                                    VideoPlayerActivity.intent(
                                        context = context,
                                        url = Uri.fromFile(file).toString(),
                                        resumeKey = "offline:${item.path}",
                                    ),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
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
