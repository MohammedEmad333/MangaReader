package com.mangareader.app

import android.content.Context

internal class RootNavigationActionController(
    private val context: Context,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val globalSearch: GlobalSearchState,
    private val migrationState: MigrationNavigationState,
    private val openSource: (Source, String, BrowseMode) -> Unit,
    private val runGlobalSearch: (String) -> Unit
) {
    fun openGlobalSearch() {
        globalSearch.mediaIsAnime = null
        globalSearch.open = true
        if (
            globalSearch.query.isNotBlank() &&
            globalSearch.results.isEmpty() &&
            !globalSearch.running
        ) {
            runGlobalSearch(globalSearch.query)
        }
    }

    fun backFromSourceBrowse() {
        browseState.series = null
        appState.error = null

        val cameFromTag = seriesState.tagReturn
        if (cameFromTag != null) {
            seriesState.tagReturn = null
            seriesState.active = cameFromTag
        } else {
            browseState.source = null
            browseState.sourceId = null
        }
    }

    fun backFromSeries() {
        seriesState.clear()
        appState.error = null
        if (seriesState.origin != SeriesOrigin.BROWSE) {
            browseState.clearSource()
        }
    }

    fun searchSeriesTag(tag: String) {
        val source = browseState.source ?: return
        seriesState.tagReturn = seriesState.active
        seriesState.active = null
        appState.error = null

        if (source.applyGenreFilter(tag)) {
            openSource(source, "", BrowseMode.FILTER)
        } else {
            openSource(source, tag, browseState.mode)
        }
    }

    fun searchGlobalTag(tag: String) {
        globalSearch.mediaIsAnime = null
        seriesState.tagReturn = seriesState.active
        seriesState.active = null
        appState.error = null
        globalSearch.open = true
        runGlobalSearch(tag)
    }

    fun startMigration() {
        val active = seriesState.active ?: return
        val sourceId = browseState.sourceId ?: return

        migrationState.from = MigrateFrom(
            active.id,
            sourceId,
            active.title
        )
        globalSearch.mediaIsAnime =
            browseState.source?.isAnime ?: sourceId.startsWith("aniyomi:")
        seriesState.tagReturn = active
        seriesState.active = null
        appState.error = null
        globalSearch.open = true
        runGlobalSearch(active.title)
    }

    fun backFromGlobalSearch() {
        globalSearch.cancel()
        globalSearch.open = false
        migrationState.from = null
        globalSearch.mediaIsAnime = null
        browseState.series = null

        val cameFromTag = seriesState.tagReturn
        if (cameFromTag != null) {
            seriesState.tagReturn = null
            seriesState.active = cameFromTag
        } else {
            browseState.source = null
            browseState.sourceId = null
        }
    }

    fun setSearchHasResultsOnly(value: Boolean) {
        globalSearch.hasResultsOnly = value
        SourcePrefs.setGlobalSearchHasResultsOnly(context, value)
    }

    fun removeRecentSearch(query: String) {
        globalSearch.recents =
            SourcePrefs.removeRecentSearch(context, query)
    }

    fun clearRecentSearches() {
        SourcePrefs.clearRecentSearches(context)
        globalSearch.recents = emptyList()
    }

    fun selectMigrationTarget(
        source: Source,
        series: Series
    ) {
        val expectedMedia = globalSearch.mediaIsAnime
        if (expectedMedia != null && source.isAnime != expectedMedia) return
        migrationState.target = source to series
    }

    fun dismissMigrationTarget() {
        migrationState.target = null
    }
}
