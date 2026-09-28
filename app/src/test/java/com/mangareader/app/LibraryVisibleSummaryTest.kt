package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryVisibleSummaryTest {

    @Test
    fun libraryVisibleSummaryLabel_formatsAndClampsCounts() {
        assertEquals("Showing 12 of 40", libraryVisibleSummaryLabel(12, 40))
        assertEquals("Showing 0 of 0", libraryVisibleSummaryLabel(-1, -5))
    }
}
