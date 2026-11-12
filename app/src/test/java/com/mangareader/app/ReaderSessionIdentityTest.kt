package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionIdentityTest {
    private val chapter = ReaderSessionIdentity("source", "series", "chapter")

    @Test fun retryRequiresExactlyMatchingSourceSeriesAndChapter() {
        assertTrue(canRetryReaderSession(chapter, chapter, 3, 3, loading = false))
        assertFalse(canRetryReaderSession(null, chapter, 3, 3, loading = false))
        assertFalse(canRetryReaderSession(chapter, chapter.copy(sourceId = "other"), 3, 3, false))
        assertFalse(canRetryReaderSession(chapter, chapter.copy(seriesId = "other"), 3, 3, false))
        assertFalse(canRetryReaderSession(chapter, chapter.copy(chapterId = "other"), 3, 3, false))
        assertFalse(canRetryReaderSession(chapter, chapter, 3, 2, false))
        assertFalse(canRetryReaderSession(chapter, chapter, 3, 3, true))
    }

    @Test fun blankIdentitiesCannotBeRetried() {
        assertFalse(canRetryReaderSession(chapter, chapter.copy(sourceId = ""), 3, 3, false))
        assertFalse(canRetryReaderSession(chapter, chapter.copy(seriesId = ""), 3, 3, false))
        assertFalse(canRetryReaderSession(chapter, chapter.copy(chapterId = ""), 3, 3, false))
    }

    @Test fun routeIdentityDistinguishesReusedChapterIdsAcrossSeries() {
        val first = readerRouteIdentity("source", "series-a", "shared-chapter")
        val second = readerRouteIdentity("source", "series-b", "shared-chapter")
        val third = readerRouteIdentity("other-source", "series-a", "shared-chapter")
        assertFalse(first == second)
        assertFalse(first == third)
        assertEquals(first, readerRouteIdentity("source", "series-a", "shared-chapter"))
    }
}
