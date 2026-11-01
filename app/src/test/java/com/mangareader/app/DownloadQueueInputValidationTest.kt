package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueInputValidationTest {
    private fun item(id: String, source: String = "source") = DownloadItem(
        sourceId = source,
        chapterId = id,
        chapterName = "Chapter $id",
        seriesTitle = "Series",
    )

    @Test fun rejectsBlankSourceAndChapterBeforeLookingUpDisk() {
        val checked = mutableListOf<String>()
        val selected = eligibleNewDownloadItems(
            queued = emptyList(),
            requested = listOf(item(""), item("  "), item("valid", ""), item("ok")),
            isComplete = { checked.add(it); false },
        )
        assertEquals(listOf("ok"), selected.map { it.chapterId })
        assertEquals(listOf("ok"), checked)
    }

    @Test fun retainsOrderAndAvoidsDuplicateOrAlreadyDownloadedEntries() {
        val selected = eligibleNewDownloadItems(
            queued = listOf(item("queued")),
            requested = listOf(
                item("queued"), item("a"), item("a"), item("completed"), item("b"),
            ),
            isComplete = { it == "completed" },
        )
        assertEquals(listOf("a", "b"), selected.map { it.chapterId })
    }

    @Test fun noOpWhenAllEntriesAlreadyQueuedOrCompleted() {
        assertTrue(eligibleNewDownloadItems(
            queued = listOf(item("queued")),
            requested = listOf(item("queued"), item("complete")),
            isComplete = { it == "complete" },
        ).isEmpty())
    }
}
