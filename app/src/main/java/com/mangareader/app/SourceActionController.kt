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
    private var openRequestId = 0L

    fun openSource(
        source: Source,
        query: String = "",
        mode: BrowseMode = BrowseMode.POPULAR
    ) {
        val requestId = ++openRequestId
        appState.error = null
        appState.loading = true
        scope.launch {
            try {
                val error = browseState.loadFirstPage(context, source, query, mode)
                if (requestId == openRequestId) appState.error = error
            } finally {
                if (requestId == openRequestId) appState.loading = false
            }
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
