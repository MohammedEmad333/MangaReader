package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SourceBrowseRoute(
    source: Source,
    sourceId: String?,
    series: List<Series>?,
    loading: Boolean,
    error: String?,
    health: SourceBrowseHealth?,
    onClearHealth: () -> Unit,
    filtersOpen: () -> Unit,
    diagnose: () -> Unit,
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    query: String,
    hasNext: Boolean,
    loadingMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRescan: () -> Unit,
    onOpen: (Series) -> Unit,
    onBack: () -> Unit,
    libraryTick: Int,
    scroll: ScrollMemory,
    onSolveChallenge: (() -> Unit)?
) {
    var confirmReset by remember(source.id) { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset source statistics?") },
            text = { Text("Clear the saved success and failure history for this source only?") },
            confirmButton = {
                Button(onClick = { confirmReset = false; onClearHealth() }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (health != null) {
            val diagnostics = sourceHealthDiagnostics(health)
            val lastAttempt = diagnostics.lastAttemptAt ?: 0L
            val checkedAt = if (lastAttempt > 0L) {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(lastAttempt))
            } else {
                "unknown"
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (loading) "Checking source…" else if (health.lastError == null) {
                                "Last browse: successful"
                            } else {
                                "Last browse failed"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (health.lastError != null && !loading) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { confirmReset = true }, enabled = !loading && !loadingMore) {
                                Text("Reset stats")
                            }
                            TextButton(onClick = onRescan, enabled = !loading && !loadingMore) {
                                Text("Check again")
                            }
                        }
                    }
                    Text(
                        text = "Last checked: $checkedAt • Successes: ${health.successes} • Failures: ${health.failures}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Observed requests: ${diagnostics.totalRequests} • Success rate: ${diagnostics.successPercent?.let { "$it%" } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (health.lastError != null) {
                        Text(
                            text = health.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        LibraryScreen(
        sourceId = sourceId.orEmpty(),
        title = source.name,
        series = series,
        loading = loading,
        error = error,
        supportsSearch = source.supportsSearch,
        supportsLatest = source.supportsLatest,
        supportsFilters = source.supportsFilters,
        onOpenFilters = filtersOpen,
        onDiagnose = diagnose,
        mode = mode,
        onModeChange = onModeChange,
        query = query,
        hasNext = hasNext,
        loadingMore = loadingMore,
        onSearch = onSearch,
        onLoadMore = onLoadMore,
        onRescan = onRescan,
        onOpen = onOpen,
        onBack = onBack,
        libraryTick = libraryTick,
        isLocalSource = sourceId.orEmpty().isLocalSourceId(),
        scroll = scroll,
        onSolveChallenge = onSolveChallenge
        )
    }
}
