package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** Latest observed browse request status, not a background connectivity probe. */
internal data class SourceBrowseHealth(
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val lastError: String? = null,
)

internal class SourceBrowseState {
    var healthBySource by mutableStateOf<Map<String, SourceBrowseHealth>>(emptyMap())
        private set

    fun healthFor(sourceId: String): SourceBrowseHealth? = healthBySource[sourceId]

    fun recordSuccess(sourceId: String, timestamp: Long = System.currentTimeMillis()) {
        val previous = healthBySource[sourceId] ?: SourceBrowseHealth()
        healthBySource = healthBySource + (sourceId to previous.copy(
            lastSuccessAt = timestamp,
            lastError = null,
        ))
    }

    fun recordFailure(sourceId: String, message: String, timestamp: Long = System.currentTimeMillis()) {
        val previous = healthBySource[sourceId] ?: SourceBrowseHealth()
        healthBySource = healthBySource + (sourceId to previous.copy(
            lastFailureAt = timestamp,
            lastError = message,
        ))
    }

    var sourceId by mutableStateOf<String?>(null)
    var source by mutableStateOf<Source?>(null)
    var series by mutableStateOf<List<Series>?>(null)
    var page by mutableIntStateOf(1)
    var hasNext by mutableStateOf(false)
    var query by mutableStateOf("")
    var mode by mutableStateOf(BrowseMode.POPULAR)
    var loadingMore by mutableStateOf(false)
    internal var listingGeneration = 0L
        private set

    fun resetListing(
        source: Source,
        query: String,
        mode: BrowseMode
    ) {
        listingGeneration++
        loadingMore = false
        this.sourceId = source.id
        this.source = source
        this.series = null
        this.page = 1
        this.hasNext = false
        this.query = query
        this.mode = mode
    }

    fun clearSource() {
        listingGeneration++
        loadingMore = false
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
    val generation = listingGeneration

    return try {
        val result = loadSourcePage(source, query, mode, 1)
        if (generation != listingGeneration) return null
        series = result.series
        hasNext = result.hasNext
        recordSuccess(source.id)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        if (generation != listingGeneration) return null
        series = emptyList()
        sourceFailureMessage(error, "Could not scan this source").also { recordFailure(source.id, it) }
    }
}

internal suspend fun SourceBrowseState.loadNextPage(): String? {
    val activeSource = source ?: return null
    if (loadingMore || !hasNext) return null

    loadingMore = true
    val next = page + 1
    val generation = listingGeneration

    return try {
        val result = loadSourcePage(
            activeSource,
            query,
            mode,
            next
        )
        if (generation != listingGeneration) return null
        series = (series ?: emptyList()) + result.series
        page = next
        hasNext = result.hasNext
        recordSuccess(activeSource.id)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        if (generation != listingGeneration) return null
        hasNext = false
        sourceFailureMessage(error, "Could not load more").also { recordFailure(activeSource.id, it) }
    } finally {
        if (generation == listingGeneration) loadingMore = false
    }
}
