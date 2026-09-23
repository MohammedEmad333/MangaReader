package com.mangareader.app

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Pick a file, look at what's in it, then decide.
 *
 * The preview is the safety mechanism, the same one `Backup.restore` uses when
 * it validates a whole payload before touching anything. If the parser were
 * reading the wrong fields, the counts and the sample titles on this screen
 * would be visibly wrong — and nothing has been written yet.
 */
@Composable
internal fun TachiyomiImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<File>?>(null) }
    var chosen by remember { mutableStateOf<File?>(null) }
    var parsed by remember { mutableStateOf<TachiyomiBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        files = withContext(Dispatchers.IO) { findBackupFiles() }
    }

    val summary = parsed
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Import Tachiyomi backup") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val message = error
                val finished = done
                when {
                    finished != null -> Text(finished)

                    message != null -> Text(
                        message,
                        color = MaterialTheme.colorScheme.error
                    )

                    busy -> {
                        Text("Working\u2026")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "A large library takes a moment. Don't leave this screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    summary != null -> {
                        Text("Found in ${chosen?.name.orEmpty()}:")
                        Spacer(Modifier.height(8.dp))
                        Text("\u2022 ${summary.inLibrary} series in the library")
                        if (summary.historyOnly > 0) {
                            Text(
                                "\u2022 ${summary.historyOnly} read but not in the " +
                                    "library \u2014 skipped"
                            )
                        }
                        Text("\u2022 ${summary.chapters} chapters")
                        Text("\u2022 ${summary.readChapters} marked read")
                        Text("\u2022 ${summary.savedPages} with a saved page")
                        Text("\u2022 ${summary.historyEntries} history entries")
                        Text("\u2022 ${summary.categories.size} categories")
                        Text("\u2022 ${summary.sourceNames.size} sources")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "First few titles \u2014 if these look wrong, cancel:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        summary.series.take(4).forEach {
                            Text(
                                "\u2022 ${it.title}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Nothing is removed \u2014 this merges into what's already here. " +
                                "Downloads aren't in a Tachiyomi backup and won't appear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    files == null -> {
                        Text("Looking for backup files\u2026")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    files.isNullOrEmpty() -> Text(
                        "No .tachibk files found on storage. Tachiyomi writes them to " +
                            "Manga/Tachiyomi/autobackup by default \u2014 copy one there or " +
                            "to Download and try again."
                    )

                    else -> {
                        Text(
                            "Pick a backup:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        files.orEmpty().forEach { f ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chosen = f
                                        busy = true
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching { readTachiyomiBackup(f) }
                                            }
                                            busy = false
                                            result.onSuccess { parsed = it }
                                            result.onFailure {
                                                error = it.message ?: "Could not read that file."
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${f.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (summary != null && done == null && !busy) {
                Button(onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { applyTachiyomiBackup(context, summary) }
                        }
                        busy = false
                        result.onSuccess { done = it }
                        result.onFailure { error = it.message ?: "Import failed." }
                    }
                }) { Text("Import") }
            } else {
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (summary != null && done == null && !busy) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
