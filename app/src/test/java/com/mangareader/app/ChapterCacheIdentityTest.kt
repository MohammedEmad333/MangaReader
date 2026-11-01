package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterCacheIdentityTest {
    private fun saved(source: String, series: String) = LibraryEntry(
        seriesId = series,
        sourceId = source,
        title = "Same title",
        cover = "",
        addedAt = 1L,
    )

    @Test fun sameSeriesIdFromDifferentSourcesGetsSeparateCacheFile() {
        assertNotEquals(
            chapterCacheScopeKey("source-a", "same-id"),
            chapterCacheScopeKey("source-b", "same-id"),
        )
        assertEquals(32, chapterCacheScopeKey("source-a", "same-id").length)
    }

    @Test fun lengthPrefixesAvoidAmbiguousDelimiterConcatenation() {
        assertNotEquals(
            chapterCacheScopeKey("a|b", "c"),
            chapterCacheScopeKey("a", "b|c"),
        )
        assertEquals(
            chapterCacheScopeKey("a|b", "c"),
            chapterCacheScopeKey("a|b", "c"),
        )
    }

    @Test fun legacyCacheIsAllowedOnlyWithOneUnambiguousSavedOwner() {
        val unique = listOf(saved("source-a", "same-id"))
        assertTrue(canReadLegacyChapterCache(unique, "source-a", "same-id"))
        assertFalse(canReadLegacyChapterCache(unique, "source-b", "same-id"))
        assertFalse(canReadLegacyChapterCache(unique, "source-a", "another-id"))
    }

    @Test fun legacyCacheIsNotSharedAcrossMultipleSources() {
        val ambiguous = listOf(
            saved("source-a", "same-id"),
            saved("source-b", "same-id"),
        )
        assertFalse(canReadLegacyChapterCache(ambiguous, "source-a", "same-id"))
        assertFalse(canReadLegacyChapterCache(ambiguous, "source-b", "same-id"))
        assertFalse(canReadLegacyChapterCache(listOf(
            saved("source-a", "same-id"),
            saved("source-a", "same-id"),
        ), "source-a", "same-id"))
    }

    @Test fun emptyIdentityNeverUsesLegacyCache() {
        assertFalse(canReadLegacyChapterCache(listOf(saved("", "id")), "", "id"))
        assertFalse(canReadLegacyChapterCache(listOf(saved("src", "")), "src", ""))
        assertFalse(canReadLegacyChapterCache(emptyList(), "src", "id"))
    }
}
