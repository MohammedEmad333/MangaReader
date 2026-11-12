package com.mangareader.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCompletionTest {
    @Test fun validationRunsOnlyWhenFinalPageIsReachedAndLoadingFinished() {
        assertFalse(shouldValidateReaderCompletion(-1, 0, false))
        assertFalse(shouldValidateReaderCompletion(0, 0, false))
        assertFalse(shouldValidateReaderCompletion(0, 2, false))
        assertFalse(shouldValidateReaderCompletion(1, 2, true))
        assertFalse(shouldValidateReaderCompletion(2, 2, false))
        assertTrue(shouldValidateReaderCompletion(1, 2, false))
        assertTrue(shouldValidateReaderCompletion(0, 1, false))
    }

    @Test fun emptyChapterIsNotComplete() {
        assertFalse(isReaderChapterCompleted(0, 0))
        assertFalse(isReaderChapterCompleted(-1, 0))
    }

    @Test fun incompletePageDoesNotMarkChapterRead() {
        assertFalse(isReaderChapterCompleted(0, 3))
        assertFalse(isReaderChapterCompleted(1, 3))
    }

    @Test fun partiallyLoadedChapterIsNotComplete() {
        assertFalse(isReaderChapterCompleted(0, 1, stillLoading = true))
        assertFalse(isReaderChapterCompleted(2, 3, stillLoading = true))
    }

    @Test fun outOfRangePageDoesNotCompleteChapter() {
        assertFalse(isReaderChapterCompleted(3, 3))
        assertFalse(isReaderChapterCompleted(-1, 1))
    }

    @Test fun finalPositionDoesNotCompleteMissingOrEmptyPages() {
        val usable = File.createTempFile("reader-complete-", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val empty = File.createTempFile("reader-empty-", ".png").apply { deleteOnExit() }
        val missing = File.createTempFile("reader-vanished-", ".png").apply { delete() }

        assertFalse(isReaderChapterFullyLoadedAtEnd(2, listOf(usable, null, usable)))
        assertFalse(isReaderChapterFullyLoadedAtEnd(2, listOf(usable, empty, usable)))
        assertFalse(isReaderChapterFullyLoadedAtEnd(2, listOf(usable, missing, usable)))
        assertFalse(isReaderChapterFullyLoadedAtEnd(0, emptyList()))
        assertFalse(isReaderChapterFullyLoadedAtEnd(2, listOf(usable, usable, usable), true))
        assertFalse(isReaderChapterFullyLoadedAtEnd(1, listOf(usable, usable, usable)))
        assertTrue(isReaderChapterFullyLoadedAtEnd(2, listOf(usable, usable, usable)))
    }

    @Test fun htmlErrorPageCannotCountAsCompletedReading() {
        val usable = File.createTempFile("reader-complete-", ".img").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val errorPage = File.createTempFile("reader-error-", ".html").apply {
            writeText("<html>Access denied</html>")
            deleteOnExit()
        }
        assertFalse(isReaderChapterFullyLoadedAtEnd(1, listOf(errorPage, usable)))
    }

    @Test fun lastPageCompletesChapter() {
        assertTrue(isReaderChapterCompleted(2, 3))
        assertTrue(isReaderChapterCompleted(0, 1))
    }
}
