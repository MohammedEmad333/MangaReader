package com.mangareader.app

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Import Tachiyomi backup")
                Text(
                    "Preview first, then merge into Yomu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val message = error
                val finished = done
                when {
                    finished != null -> Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            finished,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        )
                    }

                    message != null -> Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        )
                    }

                    busy -> {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Working…", style = MaterialTheme.typography.titleSmall)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "A large library can take a moment. Keep this screen open while Yomu validates the backup.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    summary != null -> {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    chosen?.name.orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text("${summary.inLibrary} series in the library")
                                if (summary.historyOnly > 0) {
                                    Text("${summary.historyOnly} read-only series will be skipped")
                                }
                                Text("${summary.chapters} chapters · ${summary.readChapters} marked read")
                                Text("${summary.savedPages} saved page positions · ${summary.historyEntries} history entries")
                                Text("${summary.categories.size} categories · ${summary.sourceNames.size} sources")
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("First titles", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "If these look wrong, cancel before importing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                summary.series.take(4).forEach {
                                    Text("• ${it.title}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Text(
                            "Nothing is removed — the backup merges into your current library. Downloads are not part of a Tachiyomi backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    files == null -> {
                        Text("Looking for backup files…", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    files.isNullOrEmpty() -> Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            "No .tachibk files found. Tachiyomi normally writes them to Manga/Tachiyomi/autobackup — copy one there or to Download and try again.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }

                    else -> {
                        Text(
                            "Choose a backup",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        files.orEmpty().forEach { f ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = if (chosen == f) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            chosen = f
                                            error = null
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
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(f.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${f.length() / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (summary != null && done == null && !busy) {
                Button(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { applyTachiyomiBackup(context, summary) }
                            }
                            busy = false
                            result.onSuccess { done = it }
                            result.onFailure { error = it.message ?: "Import failed." }
                        }
                    },
                ) {
                    Text("Import")
                }
            } else {
                TextButton(enabled = !busy, onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (summary != null && done == null && !busy) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
