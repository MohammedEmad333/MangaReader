package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class SearchActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val globalSearch: GlobalSearchState,
    private val sourceActions: SourceActionController,
    private val openSeries: (Series) -> Unit
) {
    fun run(query: String) {
        globalSearch.search(
            context,
            scope,
            appState.configs,
            appState.extensionSources,
            query
        )
    }

    fun cancel() {
        globalSearch.cancel()
    }

    fun setMediaFilter(value: String) {
        globalSearch.setMediaFilter(
            context,
            scope,
            appState.configs,
            appState.extensionSources,
            value
        )
    }

    fun setPinnedOnly(value: Boolean) {
        globalSearch.setPinnedOnly(
            context,
            scope,
            appState.configs,
            appState.extensionSources,
            value
        )
    }

    fun openResult(source: Source, series: Series) {
        browseState.source = source
        browseState.sourceId = source.id
        browseState.series = null
        browseState.page = 1
        browseState.hasNext = false
        browseState.query = globalSearch.query

        openSeries(series)
        seriesState.origin = SeriesOrigin.GLOBAL_SEARCH
    }

    fun openSource(source: Source) {
        cancel()
        globalSearch.open = false
        sourceActions.openSource(source, globalSearch.query)
    }
}
