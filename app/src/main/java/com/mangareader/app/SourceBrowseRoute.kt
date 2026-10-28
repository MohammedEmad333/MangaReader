package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SourceBrowseRoute(
    source: Source,
    sourceId: String?,
    series: List<Series>?,
    loading: Boolean,
    error: String?,
    health: SourceBrowseHealth?,
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
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    text = if (health.lastError == null) {
                        "Last browse: successful"
                    } else {
                        "Last browse failed: ${health.lastError}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (health.lastError == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
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
