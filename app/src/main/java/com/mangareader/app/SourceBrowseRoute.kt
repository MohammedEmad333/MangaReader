package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun SourceBrowseRoute(
    source: Source,
    sourceId: String?,
    series: List<Series>?,
    loading: Boolean,
    error: String?,
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
    LibraryScreen(
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
