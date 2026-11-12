package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettledProgressTest {
    @Test fun pagedReadingPersistsSettledItemInsteadOfTransientCurrentPage() {
        assertEquals(
            2,
            readerPageToPersist(ReaderMode.PAGED_LTR, 9, 3, 1, 12),
        )
        assertEquals(
            2,
            readerPageToPersist(ReaderMode.PAGED_RTL, 11, 3, 1, 12),
        )
    }

    @Test fun transitionRowsClampToFirstAndLastReadablePages() {
        assertEquals(0, readerPageToPersist(ReaderMode.PAGED_LTR, 5, 0, 1, 5))
        assertEquals(4, readerPageToPersist(ReaderMode.PAGED_RTL, 0, 6, 1, 5))
    }

    @Test fun stripKeepsVisibleReadingPosition() {
        assertEquals(6, readerPageToPersist(ReaderMode.LONG_STRIP, 6, 1, 1, 10))
        assertEquals(9, readerPageToPersist(ReaderMode.LONG_STRIP, 15, 0, 1, 10))
    }

    @Test fun emptyChapterNeverPersistsNegativePage() {
        assertEquals(0, readerPageToPersist(ReaderMode.PAGED_LTR, -1, 0, 1, 0))
        assertEquals(0, readerPageToPersist(ReaderMode.LONG_STRIP, -1, 0, 1, 0))
    }
}
