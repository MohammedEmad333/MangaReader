package com.mangareader.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageRetryTest {
    private val kept = File("cached-page-0000")
    private val recovered = File("retried-page-0001")

    @Test fun onlyFailedPagesInActiveChapterCanRetry() {
        val pages = listOf(kept, null)
        assertTrue(canRetryReaderPage(pages, false, 3, 3, 1))
        assertFalse(canRetryReaderPage(pages, true, 3, 3, 1))
        assertFalse(canRetryReaderPage(pages, false, 3, 3, 0))
        assertFalse(canRetryReaderPage(pages, false, 3, 2, 1))
        assertFalse(canRetryReaderPage(pages, false, null, 3, 1))
        assertFalse(canRetryReaderPage(pages, false, 3, 3, -1))
        assertFalse(canRetryReaderPage(pages, false, 3, 3, 2))
    }

    @Test fun retainsCachedPagesAcrossRetryCallbacks() {
        val original = listOf(kept, null, null)
        assertEquals(original, mergeRetriedReaderPages(original, listOf(null, null, null)))
        assertEquals(
            listOf(kept, recovered, null),
            mergeRetriedReaderPages(original, listOf(null, recovered, null))
        )
    }

    @Test fun rejectsUnexpectedChapterPageCountChanges() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeRetriedReaderPages(listOf(kept, null), listOf(recovered))
        }
    }
}
