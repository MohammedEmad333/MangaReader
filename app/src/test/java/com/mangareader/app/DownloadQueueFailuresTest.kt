package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueueFailuresTest {
    private fun item(id: String) = DownloadItem(
        sourceId = "source", chapterId = id, chapterName = id, seriesTitle = "Series"
    )
    private fun failure(id: String) = FailedDownload(item(id), "Network error")

    @Test fun retryPreservesExistingOrderAndAddsOnlyMissingChapters() {
        val original = listOf(item("active"), item("already"), item("waiting"))
        val edited = DownloadQueueFailures.retry(
            items = original,
            failed = listOf(failure("already"), failure("failed"), failure("other")),
            chapterIds = setOf("already", "failed"),
        )
        assertEquals(listOf("active", "already", "waiting", "failed"), edited.items.map { it.chapterId })
        assertEquals(listOf("other"), edited.failed.map { it.item.chapterId })
        assertEquals(2, edited.changed)
    }

    @Test fun retryRemovesStaleFailureWithoutDuplicatingQueuedChapter() {
        val original = listOf(item("already"))
        val edited = DownloadQueueFailures.retry(original, listOf(failure("already")), setOf("already"))
        assertEquals(original, edited.items)
        assertEquals(emptyList<FailedDownload>(), edited.failed)
        assertEquals(1, edited.changed)
    }

    @Test fun duplicateFailedEntriesOnlyQueueOnce() {
        val edited = DownloadQueueFailures.retry(
            emptyList(), listOf(failure("a"), failure("a")), setOf("a"),
        )
        assertEquals(listOf("a"), edited.items.map { it.chapterId })
        assertEquals(1, edited.changed)
    }

    @Test fun blankIdentifiersAreNotRetriedOrRemoved() {
        val original = listOf(failure(""), failure("valid"))
        val noChange = DownloadQueueFailures.retry(emptyList(), original, setOf(""))
        assertEquals(original, noChange.failed)
        assertEquals(emptyList<DownloadItem>(), noChange.items)
        assertEquals(0, noChange.changed)

        val validOnly = DownloadQueueFailures.retry(emptyList(), original, setOf("", "valid"))
        assertEquals(listOf("valid"), validOnly.items.map { it.chapterId })
        assertEquals(listOf(""), validOnly.failed.map { it.item.chapterId })
        assertEquals(1, validOnly.changed)
    }

    @Test fun dismissCountsActualChangesAndRejectsBlankIds() {
        val original = listOf(failure("a"), failure("a"), failure(""))
        val missing = DownloadQueueFailures.dismiss(emptyList(), original, "missing")
        assertEquals(0, missing.changed)
        assertEquals(original, missing.failed)
        val blank = DownloadQueueFailures.dismiss(emptyList(), original, "")
        assertEquals(0, blank.changed)
        assertEquals(original, blank.failed)
        val removed = DownloadQueueFailures.dismiss(emptyList(), original, "a")
        assertEquals(2, removed.changed)
        assertEquals(listOf(""), removed.failed.map { it.item.chapterId })
    }

    @Test fun clearCountsFailuresAndLeavesQueueUntouched() {
        val queued = listOf(item("keep"))
        val edited = DownloadQueueFailures.clear(queued, listOf(failure("x"), failure("y")))
        assertEquals(queued, edited.items)
        assertEquals(emptyList<FailedDownload>(), edited.failed)
        assertEquals(2, edited.changed)
    }

    @Test fun unrelatedFailuresRemainUntouched() {
        val original = listOf(item("queued"))
        val failed = listOf(failure("old"))
        val edited = DownloadQueueFailures.retry(original, failed, setOf("missing"))
        assertEquals(original, edited.items)
        assertEquals(failed, edited.failed)
        assertEquals(0, edited.changed)
    }
}
