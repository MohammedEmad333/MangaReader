package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class SeriesNavigationState {
    var active by mutableStateOf<Series?>(null)
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var fetched by mutableStateOf(false)
    var origin by mutableStateOf(SeriesOrigin.BROWSE)
    var tagReturn by mutableStateOf<Series?>(null)

    fun begin(
        series: Series,
        origin: SeriesOrigin
    ) {
        active = series
        chapters = emptyList()
        fetched = false
        this.origin = origin
    }

    fun clear() {
        active = null
        chapters = emptyList()
    }
}
