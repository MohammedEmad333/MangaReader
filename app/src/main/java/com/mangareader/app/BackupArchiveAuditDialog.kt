package com.mangareader.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The scan is opt-in; opening settings doesn't read old backups. */
@Composable
internal fun BackupArchiveAuditDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var generation by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(true) }
    var audit by remember { mutableStateOf<BackupArchiveAudit?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(generation) {
        working = true
        error = false
        audit = null
        val result = withContext(Dispatchers.IO) {
            runCatching { auditAutomaticBackupArchives(Backup.backupsDir(context)) }
        }
        result.fold(
            onSuccess = { audit = it },
            onFailure = { error = true },
        )
        working = false
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Check saved backups") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Read-only check of published Yomu backups. No files are changed " +
                        "or deleted. A verified file is readable now, but restore will " +
                        "check it again before replacing your settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error) {
                    Text("Could not inspect the backup folder.", color = MaterialTheme.colorScheme.error)
                }
                audit?.let { result ->
                    if (!result.folderAvailable) {
                        Text("Backup folder is not available. Check the storage location.")
                    } else {
                        Text(
                            "${result.verified} verified · ${result.invalid} invalid · " +
                                "${result.unreadable} unreadable",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val newest = result.newestVerified
                        if (newest == null) {
                            Text("No verified backup was found in the scanned files.")
                        } else {
                            Text(
                                "Newest verified: ${newest.fileName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (result.ignoredEntries > 0) {
                            Text(
                                "${result.ignoredEntries} older files were not scanned (100-file limit).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (result.entries.isEmpty()) {
                            Text("No published automatic backups were found.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(result.entries, key = { it.fileName }) { entry ->
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        Text(entry.fileName, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            when (entry.status) {
                                                BackupArchiveStatus.VERIFIED ->
                                                    "Verified · Yomu ${entry.appVersion.orEmpty()} · ${entry.byteCount ?: 0} bytes"
                                                BackupArchiveStatus.INVALID -> "Invalid backup. Leave this file for manual review."
                                                BackupArchiveStatus.UNREADABLE -> "File cannot be read."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { generation++ }, enabled = !working) { Text("Check again") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
