package com.mangareader.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageRetryTest {
    private val kept = File.createTempFile("cached-page-", ".png").apply { writeBytes(byteArrayOf(1)); deleteOnExit() }
    private val recovered = File.createTempFile("retried-page-", ".png").apply { writeBytes(byteArrayOf(2)); deleteOnExit() }

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

    @Test fun missingAndEmptyCacheFilesAreEligibleForRetry() {
        val vanished = File.createTempFile("vanished-", ".png")
        vanished.delete()
        val empty = File.createTempFile("empty-", ".png").apply { deleteOnExit() }
        assertTrue(canRetryReaderPage(listOf(vanished, empty), false, 1, 1, 0))
        assertTrue(canRetryReaderPage(listOf(vanished, empty), false, 1, 1, 1))
        assertFalse(canRetryReaderPage(listOf(kept), false, 1, 1, 0))
    }

    @Test fun failedRetryDoesNotRestoreDeletedCachedPage() {
        val vanished = File.createTempFile("vanished-", ".png")
        vanished.delete()
        assertEquals(listOf(null, kept), mergeRetriedReaderPages(
            listOf(vanished, kept), listOf(null, null)
        ))
    }
}
