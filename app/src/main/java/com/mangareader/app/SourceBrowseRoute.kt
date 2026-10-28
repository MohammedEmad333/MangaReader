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
    Column(modifier = Modifier.fillMaxSize()) {
        if (health != null) {
            val lastAttempt = maxOf(health.lastSuccessAt ?: 0L, health.lastFailureAt ?: 0L)
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
                            TextButton(onClick = onClearHealth, enabled = !loading && !loadingMore) {
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
