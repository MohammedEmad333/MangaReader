package com.mangareader.app

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Completed anime packages/files downloaded by Yomu itself.
 *
 * This lives under Downloads -> Anime instead of pretending HLS packages are
 * manga-style DownloadedSeries entries. Both direct files and local HLS
 * playlists open through the same VideoPlayerActivity.
 */
@Composable
internal fun AnimeOfflineDownloadsPanel(query: String) {
    val context = LocalContext.current
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

    if (allItems.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Offline anime",
                style = MaterialTheme.typography.titleSmall,
            )
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
                        val kind = if (item.path.endsWith(".m3u8", ignoreCase = true)) "HLS offline" else "Video file"
                        val quality = item.quality.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
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
