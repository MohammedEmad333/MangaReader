package com.mangareader.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun SeriesResumeFab(
    visible: Boolean,
    chapters: List<Chapter>,
    resumeIndex: Int,
    anyProgress: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    ExtendedFloatingActionButton(
        onClick = {
            val target = chapters.getOrNull(
                if (resumeIndex >= 0) resumeIndex else 0,
            )
            if (target != null) onOpen(target.id)
        },
        icon = {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
            )
        },
        text = {
            Text(if (anyProgress) "Resume" else "Start")
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}

@Composable
internal fun SeriesDeleteDialogs(
    chapter: Chapter?,
    onDismissChapter: () -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    selectionOpen: Boolean,
    selectedChapters: List<Chapter>,
    downloadTick: Int,
    onDismissSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    val context = LocalContext.current

    if (chapter != null) {
        AlertDialog(
            onDismissRequest = onDismissChapter,
            title = { Text("Delete this download?") },
            text = {
                Text(
                    "“${chapter.name}” is removed from storage. The " +
                        "chapter stays in the list and can be saved again, and your " +
                        "read mark and place in it are untouched.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteChapter(chapter) },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissChapter) {
                    Text("Cancel")
                }
            },
        )
    }

    if (selectionOpen) {
        val onDisk = selectedChapters.count { selected ->
            remember(selected.id, downloadTick) {
                Downloads.isComplete(context, selected.id)
            }
        }

        AlertDialog(
            onDismissRequest = onDismissSelection,
            title = {
                Text(
                    if (onDisk == 1) {
                        "Delete 1 download?"
                    } else {
                        "Delete $onDisk downloads?"
                    },
                )
            },
            text = {
                Text(
                    if (onDisk == 0) {
                        "None of the selected chapters are downloaded, so there's " +
                            "nothing to remove."
                    } else {
                        "Removed from storage. The chapters stay in the list and can " +
                            "be saved again, and your read marks and places in them " +
                            "are untouched."
                    },
                )
            },
            confirmButton = {
                Button(
                    enabled = onDisk > 0,
                    onClick = onDeleteSelection,
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSelection) {
                    Text("Cancel")
                }
            },
        )
    }
}
