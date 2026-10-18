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
        assertEquals(1, edited.changed)
    }

    @Test fun retryRemovesStaleFailureWithoutDuplicatingQueuedChapter() {
        val original = listOf(item("already"))
        val edited = DownloadQueueFailures.retry(original, listOf(failure("already")), setOf("already"))
        assertEquals(original, edited.items)
        assertEquals(emptyList<FailedDownload>(), edited.failed)
        assertEquals(0, edited.changed)
    }

    @Test fun duplicateFailedEntriesOnlyQueueOnce() {
        val edited = DownloadQueueFailures.retry(
            emptyList(), listOf(failure("a"), failure("a")), setOf("a"),
        )
        assertEquals(listOf("a"), edited.items.map { it.chapterId })
        assertEquals(1, edited.changed)
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
