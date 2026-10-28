package com.mangareader.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import android.content.Context
import org.json.JSONObject

/** Latest observed browse request status, not a background connectivity probe. */
internal data class SourceBrowseHealth(
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val lastError: String? = null,
    val successes: Int = 0,
    val failures: Int = 0,
)

internal class SourceBrowseState {
    var healthBySource by mutableStateOf<Map<String, SourceBrowseHealth>>(emptyMap())
        private set

    fun healthFor(sourceId: String): SourceBrowseHealth? = healthBySource[sourceId]

    fun restoreHealth(context: Context, sourceId: String) {
        if (healthBySource.containsKey(sourceId)) return
        val raw = context.getSharedPreferences("source_browse_health", Context.MODE_PRIVATE)
            .getString(sourceId, null) ?: return
        try {
            val saved = JSONObject(raw)
            healthBySource = healthBySource + (sourceId to SourceBrowseHealth(
                lastSuccessAt = saved.optLong("success").takeIf { it > 0L },
                lastFailureAt = saved.optLong("failure").takeIf { it > 0L },
                lastError = saved.optString("error").takeIf { it.isNotBlank() },
                successes = saved.optInt("successes").coerceAtLeast(0),
                failures = saved.optInt("failures").coerceAtLeast(0),
            ))
        } catch (_: org.json.JSONException) {
            // Malformed previous state should not prevent browsing.
        }
    }

    private fun persistHealth(context: Context, sourceId: String) {
        val value = healthBySource[sourceId] ?: return
        val encoded = JSONObject()
            .put("success", value.lastSuccessAt ?: 0L)
            .put("failure", value.lastFailureAt ?: 0L)
            .put("error", value.lastError ?: "")
            .put("successes", value.successes)
            .put("failures", value.failures)
        context.getSharedPreferences("source_browse_health", Context.MODE_PRIVATE)
            .edit().putString(sourceId, encoded.toString()).apply()
    }

    fun recordSuccess(context: Context, sourceId: String, timestamp: Long = System.currentTimeMillis()) {
        val previous = healthBySource[sourceId] ?: SourceBrowseHealth()
        healthBySource = healthBySource + (sourceId to previous.copy(
            lastSuccessAt = timestamp,
            lastError = null,
            successes = (previous.successes + 1).coerceAtLeast(0),
        ))
        persistHealth(context, sourceId)
    }

    fun recordFailure(context: Context, sourceId: String, message: String, timestamp: Long = System.currentTimeMillis()) {
        val previous = healthBySource[sourceId] ?: SourceBrowseHealth()
        healthBySource = healthBySource + (sourceId to previous.copy(
            lastFailureAt = timestamp,
            lastError = message,
            failures = (previous.failures + 1).coerceAtLeast(0),
        ))
        persistHealth(context, sourceId)
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
    restoreHealth(context, source.id)
    SourcePrefs.setLastUsed(context, source.id)
    val generation = listingGeneration

    return try {
        val result = loadSourcePage(source, query, mode, 1)
        if (generation != listingGeneration) return null
        series = result.series
        hasNext = result.hasNext
        recordSuccess(context, source.id)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        if (generation != listingGeneration) return null
        series = emptyList()
        sourceFailureMessage(error, "Could not scan this source").also { recordFailure(context, source.id, it) }
    }
}

internal suspend fun SourceBrowseState.loadNextPage(context: Context): String? {
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
        recordSuccess(context, activeSource.id)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        if (generation != listingGeneration) return null
        hasNext = false
        sourceFailureMessage(error, "Could not load more").also { recordFailure(context, activeSource.id, it) }
    } finally {
        if (generation == listingGeneration) loadingMore = false
    }
}
