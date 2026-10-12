package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSessionRestoreTest {
    private val entry = HistoryEntry(
        chapterKey = "source|chapter", title = "Test", sourceId = "source",
        seriesId = "series", coverPath = "", page = 5, total = 20,
        updatedAt = 1L,
    )

    @Test fun restoresOnlyMatchingHistory() {
        assertEquals(entry, findRestorableReaderHistory(
            ReaderSessionCheckpoint("source", "series", "chapter", 5),
            listOf(entry)
        ))
    }

    @Test fun missingOrWrongChapterIsNotRestored() {
        assertNull(findRestorableReaderHistory(
            ReaderSessionCheckpoint("source", "series", "different", 5), listOf(entry)
        ))
        assertNull(findRestorableReaderHistory(
            ReaderSessionCheckpoint("source", "series", "chapter", 5), emptyList()
        ))
    }

    @Test fun differentSourceOrSeriesCannotResume() {
        assertNull(findRestorableReaderHistory(
            ReaderSessionCheckpoint("other", "series", "chapter", 5), listOf(entry)
        ))
        assertNull(findRestorableReaderHistory(
            ReaderSessionCheckpoint("source", "other", "chapter", 5), listOf(entry)
        ))
    }
}
