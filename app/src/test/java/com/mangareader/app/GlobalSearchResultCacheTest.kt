package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalSearchResultCacheTest {
    private var time = 1_000L
    private val cache = GlobalSearchResultCache(now = { time }, ttlMs = 100L, maxEntries = 2)

    private fun sample(id: String) = Series(id = id, title = id, cover = null)

    @Test fun repeatsQueriesWithoutRepeatingSourceRequests() {
        val results = listOf(sample("one"))
        cache.put("source", "  Naruto ", results)
        assertEquals(results, cache.get("source", "naruto"))
        assertNull(cache.get("different-source", "naruto"))
    }

    @Test fun expiresResultsAndAllowsRefresh() {
        cache.put("source", "a", listOf(sample("one")))
        time += 100L
        assertNull(cache.get("source", "a"))
    }

    @Test fun emptyFailuresAreNeverCached() {
        cache.put("source", "a", emptyList())
        assertNull(cache.get("source", "a"))
    }

    @Test fun evictsLeastRecentlyUsedEntry() {
        cache.put("source", "a", listOf(sample("one")))
        cache.put("source", "b", listOf(sample("two")))
        cache.get("source", "a")
        cache.put("source", "c", listOf(sample("three")))
        assertNull(cache.get("source", "b"))
        assertEquals("one", cache.get("source", "a")?.single()?.id)
    }
}
