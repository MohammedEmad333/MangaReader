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
    private var navigationGeneration = 0L

    /** Distinguishes requests even when the same series is opened twice. */
    fun snapshotGeneration(): Long = navigationGeneration
    fun isCurrent(seriesId: String, generation: Long): Boolean =
        generation == navigationGeneration && active?.id == seriesId

    fun begin(
        series: Series,
        origin: SeriesOrigin
    ) {
        navigationGeneration++
        active = series
        chapters = emptyList()
        fetched = false
        this.origin = origin
    }

    fun clear() {
        navigationGeneration++
        active = null
        chapters = emptyList()
        fetched = false
    }
}


internal suspend fun SeriesNavigationState.reloadSeriesContent(
    context: android.content.Context,
    source: Source,
    series: Series
): String? {
    val generation = snapshotGeneration()
    return try {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            loadSeriesContentAndCache(context, source, series)
        }
        if (!isCurrent(series.id, generation)) return null
        active = loaded.series
        chapters = loaded.chapters
        fetched = true
        null
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (!isCurrent(series.id, generation)) null
        else sourceFailureMessage(error, "Could not list chapters")
    }
}
