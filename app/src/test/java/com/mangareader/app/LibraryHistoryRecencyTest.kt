package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHistoryRecencyTest {
    private fun row(source: String, series: String, updated: Long): HistoryEntry =
        HistoryEntry(
            chapterKey = "chapter-$updated",
            title = "Chapter",
            sourceId = source,
            seriesId = series,
            coverPath = "",
            page = 1,
            total = 10,
            updatedAt = updated,
        )

    @Test fun usesMaximumTimestampEvenForOldImportedHistoryOrder() {
        val actual = libraryLastReadIndex(
            listOf(
                row("source-a", "series-1", 80),
                row("source-a", "series-1", 120),
                row("source-a", "series-1", 35),
            ),
        )

        assertEquals(mapOf(libraryEntryKey("source-a", "series-1") to 120L), actual)
    }

    @Test fun keepsSameSeriesIdFromDifferentSourcesSeparate() {
        val actual = libraryLastReadIndex(
            listOf(
                row("source-a", "shared", 300),
                row("source-b", "shared", 100),
            ),
        )

        assertEquals(2, actual.size)
        assertEquals(300L, actual[libraryEntryKey("source-a", "shared")])
        assertEquals(100L, actual[libraryEntryKey("source-b", "shared")])
    }

    @Test fun ignoresStandaloneEntriesWithMissingSourceOrSeries() {
        val actual = libraryLastReadIndex(
            listOf(
                row("", "series-1", 900),
                row("source-a", "", 800),
                row("source-a", "series-1", 700),
            ),
        )

        assertEquals(mapOf(libraryEntryKey("source-a", "series-1") to 700L), actual)
    }

    @Test fun emptyHistoryProducesEmptyIndex() {
        assertTrue(libraryLastReadIndex(emptyList()).isEmpty())
    }

    @Test fun handlesTimestampsAtLongMinimumWithoutLosingEntry() {
        val key = libraryEntryKey("source-a", "series-1")
        assertEquals(
            mapOf(key to Long.MIN_VALUE),
            libraryLastReadIndex(listOf(row("source-a", "series-1", Long.MIN_VALUE))),
        )
    }
}
