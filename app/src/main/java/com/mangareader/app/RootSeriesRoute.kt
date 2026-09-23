package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootSeriesRoute(
    root: RootRouteContext
) {
    val appState = root.app
    val browseState = root.browse
    val seriesState = root.series
    val globalSearch = root.search
    val migrationState = root.migration
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
        onSearchTag = { tag ->
            browseState.source?.let { source ->
                seriesState.tagReturn = seriesState.active
                seriesState.active = null
                appState.error = null
                if (source.applyGenreFilter(tag)) {
                    actions.openSource(source, "", BrowseMode.FILTER)
                } else {
                    actions.openSource(source, tag, browseState.mode)
                }
            }
        },
        onGlobalSearchTag = { tag ->
            seriesState.tagReturn = seriesState.active
            seriesState.active = null
            appState.error = null
            globalSearch.open = true
            actions.runGlobalSearch(tag)
        },
        onMigrate = {
            val active = seriesState.active
            val sourceId = browseState.sourceId
            if (active != null && sourceId != null) {
                migrationState.from = MigrateFrom(active.id, sourceId, active.title)
                seriesState.tagReturn = active
                seriesState.active = null
                appState.error = null
                globalSearch.open = true
                actions.runGlobalSearch(active.title)
            }
        },
        onSolveChallenge = solveFromSeries,
        onBack = {
            seriesState.clear()
            appState.error = null
            if (seriesState.origin != SeriesOrigin.BROWSE) {
                browseState.clearSource()
            }
        }
    )
}
