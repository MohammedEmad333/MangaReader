package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressValidationTest {
    @Test fun rejectsEmptyChapter() {
        assertFalse(isValidReaderPageIndex(0, 0))
    }

    @Test fun rejectsNegativeAndOutOfRangePages() {
        assertFalse(isValidReaderPageIndex(-1, 3))
        assertFalse(isValidReaderPageIndex(3, 3))
        assertFalse(isValidReaderPageIndex(100, 3))
    }

    @Test fun acceptsOnlyLoadedPageIndices() {
        assertTrue(isValidReaderPageIndex(0, 1))
        assertTrue(isValidReaderPageIndex(0, 3))
        assertTrue(isValidReaderPageIndex(2, 3))
    }
}
