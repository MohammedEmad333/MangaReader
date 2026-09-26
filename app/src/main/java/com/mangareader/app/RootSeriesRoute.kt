package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootSeriesRoute(
    root: RootRouteContext
) {
    val appState = root.app
    val browseState = root.browse
    val seriesState = root.series
    val actions = root.actions
    val series = seriesState.active ?: return
    val seriesSite = browseState.source?.siteUrl()
    val solveFromSeries: (() -> Unit)? = if (seriesSite == null) null else fun() {
        appState.challengeUrl = seriesSite
    }

    SeriesRoute(
        series = series,
        chapters = seriesState.chapters,
        chaptersFetched = seriesState.fetched,
        source = browseState.source,
        sourceId = browseState.sourceId,
        loading = appState.loading,
        error = appState.error,
        readTick = appState.readTick,
        scroll = root.ui.seriesScroll,
        localDownloadTick = appState.downloadTick,
        onFindVideos = { actions.findVideos(it) },
        onDownload = { source, chapter -> actions.downloadChapter(source, chapter) },
        onDownloadAll = { source, chapters -> actions.downloadAll(source, chapters) },
        onCancelDownloads = { actions.cancelSeriesDownloads(it) },
        onDownloadStateChanged = { appState.downloadTick++ },
        onOpenChapter = { actions.openChapter(it) },
        onRefresh = { actions.refreshChapters() },
        onReadStateChanged = { appState.readTick++ },
        onLibraryChanged = { appState.libraryTick++ },
        onSearchTag = actions::searchSeriesTag,
        onLibrarySearchTag = { tag ->
            actions.backFromSeries()
            root.ui.onLibrarySearchChange(tag)
            root.ui.onLibrarySearchOpenChange(true)
            appState.currentTab = 0
        },
        onGlobalSearchTag = actions::searchGlobalTag,

        onMigrate = actions::startMigration,

        onSolveChallenge = solveFromSeries,
        onBack = actions::backFromSeries
    )
}
