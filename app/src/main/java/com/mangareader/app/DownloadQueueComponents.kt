package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadQueueHeader(
    items: List<DownloadItem>,
    paused: Boolean,
    pausedIds: Set<String>,
    onBack: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val context = LocalContext.current
    val heldCount = items.count { it.chapterId in pausedIds }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Download queue",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    when {
                        items.isEmpty() -> "No downloads waiting"
                        paused -> "${items.size} waiting · queue paused"
                        heldCount > 0 -> "${items.size} waiting · $heldCount on hold"
                        else -> "${items.size} ${if (items.size == 1) "item" else "items"} waiting"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (items.isNotEmpty()) {
                val resumeAll = !paused && items.all { it.chapterId in pausedIds }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                DownloadService.start(
                                    context,
                                    when {
                                        paused -> DownloadService.ACTION_RESUME
                                        resumeAll -> DownloadService.ACTION_RESUME_ALL
                                        else -> DownloadService.ACTION_PAUSE
                                    },
                                )
                            },
                        ) {
                            Icon(
                                if (paused || resumeAll) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = when {
                                    paused -> "Resume all downloads"
                                    resumeAll -> "Release every hold"
                                    else -> "Pause all downloads"
                                },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        IconButton(onClick = onCancelAll) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Cancel all downloads",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
