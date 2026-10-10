package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageBoundsTest {
    @Test fun emptyChapterStartsAtZero() {
        assertEquals(0, safeReaderPageIndex(3, 0))
    }

    @Test fun pagePositionStaysWithinAvailablePages() {
        assertEquals(0, safeReaderPageIndex(-1, 4))
        assertEquals(2, safeReaderPageIndex(2, 4))
        assertEquals(3, safeReaderPageIndex(99, 4))
    }

    @Test fun singlePageChapterStartsAtZero() {
        assertEquals(0, safeReaderPageIndex(7, 1))
    }
}
