package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryIdentityTest {
    private fun entry(source: String, series: String, chapter: String) = HistoryEntry(
        chapterKey = "$source|$chapter",
        title = "Same name",
        sourceId = source,
        seriesId = series,
        coverPath = "",
        page = 0,
        total = 10,
        updatedAt = 1L,
    )

    @Test fun historyRowKeysSeparateSameSeriesFromDifferentSources() {
        val first = entry("source-a", "123", "chapter")
        val second = entry("source-b", "123", "chapter")
        assertFalse(historyRowKey(first) == historyRowKey(second))
        assertEquals(historyRowKey(first), historyRowKey(first.copy(page = 9)))
        assertFalse(historyRowKey(first).contains("Same name"))
    }

    @Test fun historyRowKeysCannotCollideAcrossDelimiterBoundaries() {
        val first = entry("a:b", "c", "chapter")
        val second = entry("a", "b:c", "chapter")
        assertFalse(historyRowKey(first) == historyRowKey(second))
    }

    @Test fun standaloneHistoryRowKeysFollowChapterIdentity() {
        val first = entry("", "", "first")
        val second = entry("", "", "second")
        assertFalse(historyRowKey(first) == historyRowKey(second))
        assertEquals(historyRowKey(first), historyRowKey(first.copy(page = 4)))
    }

    @Test fun sameSeriesIdFromDifferentSourcesStaysDistinct() {
        val a = entry("source-a", "123", "first")
        val b = entry("source-b", "123", "first")
        assertFalse(historyEntryIdentity(a) == historyEntryIdentity(b))
        assertFalse(shouldReplaceHistoryEntry(a, b))
        assertEquals(listOf(a, b), listOf(a, b).distinctBy(::historyEntryIdentity))
    }

    @Test fun laterChapterOfSameSourceAndSeriesReplacesEarlierChapter() {
        val earlier = entry("source", "series", "first")
        val later = entry("source", "series", "second")
        assertEquals(historyEntryIdentity(earlier), historyEntryIdentity(later))
        assertTrue(shouldReplaceHistoryEntry(earlier, later))
    }

    @Test fun identicalChapterKeyReplacesOldProgress() {
        val previous = entry("source", "series", "chapter")
        val updated = previous.copy(page = 5, updatedAt = 20L)
        assertTrue(shouldReplaceHistoryEntry(previous, updated))
    }

    @Test fun standaloneFilesRemainIndependent() {
        val a = entry("", "", "first")
        val b = entry("", "", "second")
        assertFalse(historyEntryIdentity(a) == historyEntryIdentity(b))
        assertFalse(shouldReplaceHistoryEntry(a, b))
    }

    @Test fun incompleteIdentityCannotOverwriteOtherSeries() {
        val existing = entry("source", "series", "chapter")
        val incoming = entry("", "series", "different")
        assertFalse(shouldReplaceHistoryEntry(existing, incoming))
    }
}
