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

    @Test fun duplicateMatchingHistoryRowsCannotRestoreArbitrarily() {
        assertNull(findRestorableReaderHistory(
            ReaderSessionCheckpoint("source", "series", "chapter", 5),
            listOf(entry, entry.copy(page = 9, updatedAt = 2L)),
        ))
    }

    @Test fun animeHistoryIsNotRestoredAsMangaSession() {
        assertNull(findRestorableReaderHistory(
            ReaderSessionCheckpoint("source", "series", "chapter", 5),
            listOf(entry.copy(mediaType = "anime")),
        ))
    }

    @Test fun historyChapterNavigationRequiresUniqueIdentity() {
        val chapters = listOf(
            Chapter("a", "First"),
            Chapter("b", "Second"),
        )
        assertEquals(1, findHistoryChapterIndex(chapters, "source", "source|b"))
        assertNull(findHistoryChapterIndex(chapters, "other", "source|b"))
        assertNull(findHistoryChapterIndex(chapters, "", "|b"))
        assertNull(findHistoryChapterIndex(chapters, "source", "source|"))
        assertNull(findHistoryChapterIndex(
            chapters + Chapter("b", "Duplicate"), "source", "source|b"
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
