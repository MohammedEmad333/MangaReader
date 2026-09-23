package com.mangareader.app

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.io.File

@Composable
internal fun DataSettingsDialogs(
    askAccess: Boolean,
    onDismissAccess: () -> Unit,
    onGrantAccess: () -> Unit,
    importOpen: Boolean,
    onDismissImport: () -> Unit,
    confirmReorganise: Boolean,
    onDismissReorganise: () -> Unit,
    onConfirmReorganise: () -> Unit,
    pendingMove: Pair<File, File>?,
    onDismissMove: () -> Unit,
    onConfirmMove: (Pair<File, File>) -> Unit,
    message: String?,
    onDismissMessage: () -> Unit,
    pendingRestore: Uri?,
    onDismissRestore: () -> Unit,
    onConfirmRestore: (Uri) -> Unit,
    confirmDownloads: Boolean,
    onDismissDownloads: () -> Unit,
    onConfirmDownloads: () -> Unit,
    confirmChapterLists: Boolean,
    onDismissChapterLists: () -> Unit,
    onConfirmChapterLists: () -> Unit,
) {
    if (askAccess) {
        AlertDialog(
            onDismissRequest = onDismissAccess,
            title = { Text("Allow access to storage?") },
            text = {
                Text(
                    "To keep downloads in a folder you choose, Yomu needs " +
                        "permission to manage files. Android grants this on its own " +
                        "settings screen rather than in a dialog, so this opens that " +
                        "screen — come back here afterwards and pick the folder.",
                )
            },
            confirmButton = {
                Button(onClick = onGrantAccess) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAccess) {
                    Text("Cancel")
                }
            },
        )
    }

    if (importOpen) {
        TachiyomiImportDialog(onDismiss = onDismissImport)
    }

    if (confirmReorganise) {
        AlertDialog(
            onDismissRequest = onDismissReorganise,
            title = { Text("Reorganise downloads?") },
            text = {
                Text(
                    "Chapters downloaded before this layout sit in a folder named " +
                        "after a hash, which is unreadable but works. This files them " +
                        "under source and series instead.\n\nA chapter can only be " +
                        "placed if the app still knows what it was — anything it " +
                        "can’t identify is left where it is and keeps working.",
                )
            },
            confirmButton = {
                Button(onClick = onConfirmReorganise) {
                    Text("Reorganise")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReorganise) {
                    Text("Cancel")
                }
            },
        )
    }

    if (pendingMove != null) {
        AlertDialog(
            onDismissRequest = onDismissMove,
            title = { Text("Move existing downloads?") },
            text = {
                Text(
                    "Downloads and backups already written are still in the old " +
                        "folder. Moving them keeps them readable; leaving them means " +
                        "they stay on disk taking up space but stop appearing in " +
                        "Downloads. On a large library this takes a while, and moving " +
                        "to an SD card is a copy rather than a rename, so give it time.",
                )
            },
            confirmButton = {
                Button(onClick = { onConfirmMove(pendingMove) }) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissMove) {
                    Text("Leave them")
                }
            },
        )
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = onDismissMessage,
            title = { Text("Backup") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = onDismissMessage) {
                    Text("Done")
                }
            },
        )
    }

    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Everything currently in the app is replaced: library, " +
                        "categories, history, read marks and source settings. This " +
                        "can't be undone, and it isn't a merge — anything added " +
                        "since the backup was made is lost. Downloaded chapters stay " +
                        "on disk either way.",
                )
            },
            confirmButton = {
                Button(onClick = { onConfirmRestore(pendingRestore) }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestore) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmDownloads) {
        AlertDialog(
            onDismissRequest = onDismissDownloads,
            title = { Text("Delete all downloads?") },
            text = {
                Text("Every downloaded chapter goes. This can't be undone.")
            },
            confirmButton = {
                Button(onClick = onConfirmDownloads) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDownloads) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmChapterLists) {
        AlertDialog(
            onDismissRequest = onDismissChapterLists,
            title = { Text("Clear chapter lists?") },
            text = {
                Text(
                    "A downloaded chapter stays on disk, but the series it belongs to " +
                        "won't open offline again until it's been opened once with a " +
                        "connection.",
                )
            },
            confirmButton = {
                Button(onClick = onConfirmChapterLists) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissChapterLists) {
                    Text("Cancel")
                }
            },
        )
    }
}
