package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueSnapshotRecoveryTest {
    private fun item(id: String, source: String = "source") = DownloadItem(
        sourceId = source, chapterId = id, chapterName = id, seriesTitle = "Series",
    )
    private fun failure(id: String, source: String = "source") =
        FailedDownload(item(id, source), "network")

    @Test fun dropsCorruptAndCompletedQueueRowsBeforeCheckingDisk() {
        val checked = mutableListOf<String>()
        val snapshot = normalizeRestoredDownloadQueue(
            items = listOf(item(""), item("bad-source", ""), item("done"), item("ok"), item("ok")),
            failed = emptyList(),
            paused = true,
            pausedIds = setOf("", "done", "ok", "obsolete"),
            isComplete = { checked += it; it == "done" },
        )
        assertEquals(listOf("ok"), snapshot.items.map { it.chapterId })
        assertEquals(setOf("ok"), snapshot.pausedIds)
        assertTrue(snapshot.paused)
        assertFalse(checked.contains(""))
        assertFalse(checked.contains("bad-source"))
    }

    @Test fun keepsFailuresOnlyForDistinctValidUnqueuedIncompleteChapters() {
        val snapshot = normalizeRestoredDownloadQueue(
            items = listOf(item("active")),
            failed = listOf(
                failure("active"), failure(""), failure("bad", ""),
                failure("already-complete"), failure("retry"), failure("retry"),
            ),
            paused = false,
            pausedIds = setOf("unknown"),
            isComplete = { it == "already-complete" },
        )
        assertEquals(listOf("retry"), snapshot.failed.map { it.item.chapterId })
        assertEquals(listOf("active"), snapshot.items.map { it.chapterId })
        assertTrue(snapshot.pausedIds.isEmpty())
        assertFalse(snapshot.paused)
    }

    @Test fun emptySnapshotIsPreservedWithoutDiskChecks() {
        val snapshot = normalizeRestoredDownloadQueue(
            items = emptyList(), failed = emptyList(), paused = false,
            pausedIds = setOf("orphan"),
            isComplete = { error("No disk lookup expected") },
        )
        assertTrue(snapshot.items.isEmpty())
        assertTrue(snapshot.failed.isEmpty())
        assertTrue(snapshot.pausedIds.isEmpty())
    }
}
