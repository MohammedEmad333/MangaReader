package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class SourceBrowseState {
    var sourceId by mutableStateOf<String?>(null)
    var source by mutableStateOf<Source?>(null)
    var series by mutableStateOf<List<Series>?>(null)
    var page by mutableIntStateOf(1)
    var hasNext by mutableStateOf(false)
    var query by mutableStateOf("")
    var mode by mutableStateOf(BrowseMode.POPULAR)
    var loadingMore by mutableStateOf(false)

    fun resetListing(
        source: Source,
        query: String,
        mode: BrowseMode
    ) {
        this.sourceId = source.id
        this.source = source
        this.series = null
        this.page = 1
        this.hasNext = false
        this.query = query
        this.mode = mode
    }

    fun clearSource() {
        source = null
        sourceId = null
        series = null
    }
}


internal suspend fun SourceBrowseState.loadFirstPage(
    context: android.content.Context,
    source: Source,
    query: String,
    mode: BrowseMode
): String? {
    resetListing(source, query, mode)
    SourcePrefs.setLastUsed(context, source.id)

    return try {
        val result = loadSourcePage(source, query, mode, 1)
        series = result.series
        hasNext = result.hasNext
        null
    } catch (error: Throwable) {
        series = emptyList()
        sourceFailureMessage(error, "Could not scan this source")
    }
}

internal suspend fun SourceBrowseState.loadNextPage(): String? {
    val activeSource = source ?: return null
    if (loadingMore || !hasNext) return null

    loadingMore = true
    val next = page + 1

    return try {
        val result = loadSourcePage(
            activeSource,
            query,
            mode,
            next
        )
        series = (series ?: emptyList()) + result.series
        page = next
        hasNext = result.hasNext
        null
    } catch (error: Throwable) {
        hasNext = false
        sourceFailureMessage(error, "Could not load more")
    } finally {
        loadingMore = false
    }
}
