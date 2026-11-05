package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueSeriesIdentityTest {
    private fun item(source: String, series: String, chapter: String) = DownloadItem(
        sourceId = source, seriesId = series, chapterId = chapter,
        chapterName = chapter, seriesTitle = "Same title",
    )

    @Test fun stopOneSourceDoesNotSelectAnotherSourceWithSameSeriesId() {
        val queue = listOf(
            item("source-a", "shared", "a1"),
            item("source-b", "shared", "b1"),
            item("source-a", "shared", "a2"),
            item("source-a", "other", "a3"),
        )
        assertEquals(
            setOf("a1", "a2"),
            queuedChapterIdsForSeries(queue, "source-a", "shared"),
        )
        assertEquals(
            setOf("b1"),
            queuedChapterIdsForSeries(queue, "source-b", "shared"),
        )
    }

    @Test fun blankOrMissingIdentityCannotMatchUnrelatedJobs() {
        val queue = listOf(item("source", "series", "chapter"))
        assertTrue(queuedChapterIdsForSeries(queue, "", "series").isEmpty())
        assertTrue(queuedChapterIdsForSeries(queue, "source", "").isEmpty())
        assertTrue(queuedChapterIdsForSeries(queue, "another", "series").isEmpty())
    }

    @Test fun invalidAndDuplicateChapterIdsAreExcluded() {
        val queue = listOf(
            item("source", "series", ""),
            item("source", "series", "1"),
            item("source", "series", "1"),
        )
        assertEquals(
            setOf("1"),
            queuedChapterIdsForSeries(queue, "source", "series"),
        )
    }
}
