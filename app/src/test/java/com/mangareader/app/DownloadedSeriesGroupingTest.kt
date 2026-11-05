package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedSeriesGroupingTest {
    private fun record(source: String, series: String, chapter: String, cover: String = "") =
        DownloadIndexRecord(
            chapterId = chapter, chapterName = chapter,
            sourceId = source, seriesId = series,
            title = "Same title", cover = cover,
        )

    @Test fun equalSeriesIdsFromTwoSourcesNeverMixChapters() {
        val grouped = groupDownloadedRecords(
            listOf(
                record("source-a", "shared", "a1", "cover-a"),
                record("source-b", "shared", "b1", "cover-b"),
                record("source-a", "shared", "a2"),
            ),
            sizeOf = { if (it.startsWith("a")) 10 else 5 },
        )
        assertEquals(2, grouped.size)
        assertEquals(listOf("a1", "a2"), grouped[0].chapters.map { it.chapterId })
        assertEquals(listOf("b1"), grouped[1].chapters.map { it.chapterId })
        assertEquals("source-a", grouped[0].sourceId)
        assertEquals("source-b", grouped[1].sourceId)
        assertEquals(20L, grouped[0].sizeBytes)
        assertEquals(5L, grouped[1].sizeBytes)
        assertEquals("cover-a", grouped[0].cover)
        assertEquals("cover-b", grouped[1].cover)
    }

    @Test fun emptyGroupsAreSafe() {
        assertTrue(groupDownloadedRecords(emptyList()) { error("No size reads") }.isEmpty())
    }

    @Test fun offlineCoversUseSourceQualifiedNames() {
        assertNotEquals(
            downloadCoverCacheKey("source-a", "same"),
            downloadCoverCacheKey("source-b", "same"),
        )
        assertNotEquals(
            downloadCoverCacheKey("a|b", "c"),
            downloadCoverCacheKey("a", "b|c"),
        )
    }
}
