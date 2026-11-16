package com.mangareader.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** Diagnostics are opt-in. Listing sources never contacts their websites. */
@Composable
internal fun SourceHealthCenterDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var entries by remember { mutableStateOf<List<SourceHealthEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Source?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            val available = withContext(Dispatchers.IO) {
                SourceManager.listAllSources(context)
            }
            val failures = DownloadQueue.failed.map {
                it.item.sourceId to it.reason
            }
            sources = available
            entries = buildSourceHealthEntries(
                installed = available.map { it.id to it.name },
                failed = failures,
            )
        } catch (_: Exception) {
            error = "Could not load source diagnostics. Your sources were not changed."
        } finally {
            loading = false
        }
    }

    if (selected == null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Source Health Center") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Shows installed sources and recorded download failures. " +
                        "A source without errors is not necessarily online. " +
                        "Connection probes are manual and may contact source websites.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                if (!loading && error == null) {
                    Text(
                        "${entries.size} sources · " +
                            "${entries.count { !it.installed }} unavailable · " +
                            "${entries.sumOf { it.failedChapters }} failed chapters",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (entries.isEmpty()) {
                        Text("No configured sources or recorded download failures.")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                            items(entries, key = { it.id }) { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${if (entry.installed) "Installed" else "Not installed"} · " +
                                            "${entry.failedChapters} failed chapters",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(entry.hint, style = MaterialTheme.typography.bodySmall)
                                    val source = sources.firstOrNull { it.id == entry.id }
                                    if (source != null) {
                                        Row {
                                            TextButton(onClick = { selected = source }) {
                                                Text("Probe connection")
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { refresh++ }, enabled = !loading) {
                Text("Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )

    // Reuse the established per-source browser/HTTP diagnostic rather than
    // shipping a second HTTP client or running silent background requests.
    selected?.let { source ->
        NetworkProbeDialog(source = source, onDismiss = { selected = null })
    }
}
