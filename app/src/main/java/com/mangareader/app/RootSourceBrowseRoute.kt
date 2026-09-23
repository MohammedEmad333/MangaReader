package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootSourceBrowseRoute(
    root: RootRouteContext
) {
    val appState = root.app
    val browseState = root.browse
    val actions = root.actions
    val source = browseState.source ?: return
    val site = source.siteUrl()
    val startChallenge: (() -> Unit)? = if (site == null) null else fun() {
        appState.challengeUrl = site
    }

    SourceBrowseRoute(
        source = source,
        sourceId = browseState.sourceId,
        series = browseState.series,
        loading = appState.loading,
        error = appState.error,
        filtersOpen = actions::showFilters,
        diagnose = actions::showProbe,
        mode = browseState.mode,
        onModeChange = { mode -> actions.openSource(source, "", mode) },
        query = browseState.query,
        hasNext = browseState.hasNext,
        loadingMore = browseState.loadingMore,
        onSearch = { query -> actions.openSource(source, query, browseState.mode) },
        onLoadMore = { actions.loadMoreSeries() },
        onRescan = { actions.openSource(source, browseState.query, browseState.mode) },
        onOpen = { actions.openSeries(it) },
        onBack = actions::backFromSourceBrowse,

        libraryTick = appState.libraryTick,
        scroll = root.ui.browseScroll,
        onSolveChallenge = startChallenge
    )
}
