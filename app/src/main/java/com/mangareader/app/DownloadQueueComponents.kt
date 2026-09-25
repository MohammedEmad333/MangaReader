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

@Composable
internal fun DownloadQueueHeader(
    items: List<DownloadItem>,
    paused: Boolean,
    pausedIds: Set<String>,
    onBack: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Text(
            "Download queue",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))

        if (items.isNotEmpty()) {
            val resumeAll =
                !paused && items.all { it.chapterId in pausedIds }

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
                    if (paused || resumeAll) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.Pause
                    },
                    contentDescription = when {
                        paused -> "Resume all downloads"
                        resumeAll -> "Release every hold"
                        else -> "Pause all downloads"
                    },
                )
            }

            IconButton(onClick = onCancelAll) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Cancel all downloads",
                )
            }
        }
    }
}
