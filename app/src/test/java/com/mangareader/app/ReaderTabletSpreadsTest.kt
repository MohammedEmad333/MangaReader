package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTabletSpreadsTest {
    @Test fun tabletModeRequiresOptInAndLandscape() {
        assertFalse(readerTwoPageTabletEnabled(false, ReaderMode.PAGED_RTL, 1200, 800))
        assertTrue(readerTwoPageTabletEnabled(true, ReaderMode.PAGED_RTL, 1200, 800))
        assertTrue(readerTwoPageTabletEnabled(true, ReaderMode.PAGED_LTR, 840, 600))
        assertFalse(readerTwoPageTabletEnabled(true, ReaderMode.LONG_STRIP, 1200, 800))
        assertFalse(readerTwoPageTabletEnabled(true, ReaderMode.PAGED_LTR, 600, 900))
        assertFalse(readerTwoPageTabletEnabled(true, ReaderMode.PAGED_LTR, 840, 900))
        assertFalse(readerTwoPageTabletEnabled(true, ReaderMode.PAGED_LTR, 840, 0))
    }

    @Test fun countAndPairingForOddAndEvenChapters() {
        assertEquals(0, readerSpreadCount(0))
        assertEquals(1, readerSpreadCount(1))
        assertEquals(2, readerSpreadCount(4))
        assertEquals(3, readerSpreadCount(5))
        assertEquals(0, readerSpreadFirstPage(0, 5))
        assertEquals(1, readerSpreadLastPage(0, 5))
        assertEquals(2, readerSpreadFirstPage(1, 5))
        assertEquals(3, readerSpreadLastPage(1, 5))
        assertEquals(4, readerSpreadFirstPage(2, 5))
        assertEquals(4, readerSpreadLastPage(2, 5))
    }

    @Test fun resumeAndSeekMapToSpreadWithoutInventingPageIds() {
        for (page in 0..17) {
            val slot = readerSpreadForPage(page)
            assertTrue(readerSpreadFirstPage(slot, 18) <= page)
            assertTrue(readerSpreadLastPage(slot, 18) >= page)
        }
        assertEquals(0, readerSpreadForPage(-1))
        assertEquals(0, readerSpreadLastPage(-1, 0))
        assertEquals(0, readerSpreadFirstPage(999, 0))
        assertEquals(6, readerSpreadLastPage(999, 7))
    }
}
