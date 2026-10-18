package com.mangareader.app

/** Limit a stalled extension so other sources can finish and the search can complete. */
internal const val GLOBAL_SEARCH_SOURCE_TIMEOUT_MS = 15_000L

/**
 * Small, in-memory cache for repeat queries while browsing back from a result.
 * Stores successful nonempty results only: network failures must remain retryable.
 * Entries expire quickly so new catalogue additions are not hidden for long.
 */
internal class GlobalSearchResultCache(
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 90_000L,
    private val maxEntries: Int = 60,
) {
    private data class Entry(val series: List<Series>, val createdAt: Long)
    private val entries = LinkedHashMap<Pair<String, String>, Entry>(16, 0.75f, true)

    fun get(sourceId: String, query: String): List<Series>? {
        val key = sourceId to query.trim().lowercase()
        val entry = entries[key] ?: return null
        if (now() - entry.createdAt >= ttlMs || now() < entry.createdAt) {
            entries.remove(key)
            return null
        }
        return entry.series
    }

    fun put(sourceId: String, query: String, series: List<Series>) {
        if (series.isEmpty()) return
        val key = sourceId to query.trim().lowercase()
        entries[key] = Entry(series.toList(), now())
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }
}
