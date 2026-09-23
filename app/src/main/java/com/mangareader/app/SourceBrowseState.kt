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
