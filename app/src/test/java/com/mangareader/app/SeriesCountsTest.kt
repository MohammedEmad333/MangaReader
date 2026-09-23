package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesCountsTest {

    @Test
    fun unread_neverDropsBelowZero() {
        assertEquals(0, SeriesCounts(total = 2, read = 5, latestChapterAt = 0, updatedAt = 0).unread)
    }

    @Test
    fun started_requiresPartialProgress() {
        assertFalse(SeriesCounts(10, 0, 0, 0).started)
        assertTrue(SeriesCounts(10, 4, 0, 0).started)
        assertFalse(SeriesCounts(10, 10, 0, 0).started)
    }

    @Test
    fun completed_requiresKnownNonEmptyTotal() {
        assertFalse(SeriesCounts(0, 0, 0, 0).completed)
        assertTrue(SeriesCounts(10, 10, 0, 0).completed)
        assertTrue(SeriesCounts(10, 11, 0, 0).completed)
    }
}
