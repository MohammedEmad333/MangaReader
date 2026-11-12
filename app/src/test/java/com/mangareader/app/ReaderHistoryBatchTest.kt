package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderHistoryBatchTest {
    private fun entry(source: String, series: String, chapter: String, page: Int) =
        HistoryEntry(
            chapterKey = "$source|$chapter",
            title = chapter,
            sourceId = source,
            seriesId = series,
            coverPath = "",
            page = page,
            total = 20,
            updatedAt = page.toLong(),
        )

    @Test fun repeatedPagesOfSameSeriesKeepOnlyNewestWrite() {
        val batch = ReaderHistoryBatch()
        batch.add(entry("source", "series", "chapter", 1))
        batch.add(entry("source", "series", "chapter", 2))
        batch.add(entry("source", "series", "nextChapter", 3))
        assertEquals(1, batch.size)
        assertEquals("source|nextChapter", batch.entries().single().chapterKey)
        assertEquals(3, batch.entries().single().page)
    }

    @Test fun finalUpdateOrderMatchesSequentialHistoryWrites() {
        val a1 = entry("src", "a", "one", 1)
        val b1 = entry("src", "b", "one", 2)
        val a2 = entry("src", "a", "two", 3)
        val c1 = entry("src", "c", "one", 4)
        val b2 = entry("src", "b", "two", 5)
        val batch = ReaderHistoryBatch()
        listOf(a1, b1, a2, c1, b2).forEach(batch::add)
        assertEquals(listOf(a2, c1, b2), batch.entries())
        // History.touchAll inserts every entry at the FRONT of the list.
        assertEquals(listOf(b2, c1, a2), batch.entries().reversed())
    }

    @Test fun sameSeriesIdsFromDifferentSourcesAreIndependent() {
        val batch = ReaderHistoryBatch()
        batch.add(entry("source-a", "shared", "chapter", 2))
        batch.add(entry("source-b", "shared", "chapter", 3))
        assertEquals(2, batch.size)
    }

    @Test fun standaloneFileEntriesAreIndependentAndCoalesceByChapter() {
        val first = entry("", "", "first", 1)
        val second = entry("", "", "second", 2)
        val batch = ReaderHistoryBatch()
        batch.add(first)
        batch.add(second)
        batch.add(first.copy(page = 5))
        assertEquals(listOf(second, first.copy(page = 5)), batch.entries())
    }
}
