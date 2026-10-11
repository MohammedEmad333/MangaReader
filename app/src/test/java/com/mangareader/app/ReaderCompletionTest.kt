package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCompletionTest {
    @Test fun emptyChapterIsNotComplete() {
        assertFalse(isReaderChapterCompleted(0, 0))
        assertFalse(isReaderChapterCompleted(-1, 0))
    }

    @Test fun incompletePageDoesNotMarkChapterRead() {
        assertFalse(isReaderChapterCompleted(0, 3))
        assertFalse(isReaderChapterCompleted(1, 3))
    }

    @Test fun lastPageCompletesChapter() {
        assertTrue(isReaderChapterCompleted(2, 3))
        assertTrue(isReaderChapterCompleted(0, 1))
    }
}
