package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SourceActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState
) {
    fun openSource(
        source: Source,
        query: String = "",
        mode: BrowseMode = BrowseMode.POPULAR
    ) {
        appState.error = null
        scope.launch {
            appState.loading = true
            appState.error = browseState.loadFirstPage(
                context,
                source,
                query,
                mode
            )
            appState.loading = false
        }
    }

    fun loadMoreSeries() {
        scope.launch {
            browseState.loadNextPage()?.let {
                appState.error = it
            }
        }
    }

    fun openSourceConfig(config: SourceConfig) {
        val source = resolveSourceConfig(context, config)
        if (source == null) {
            appState.error = "\"${config.label}\" isn't configured yet"
            return
        }
        openSource(source)
    }
}
