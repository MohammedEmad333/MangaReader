package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSourceImportSelectionTest {
    private fun entry(source: String, id: String) = LibraryEntry(
        seriesId = id, sourceId = source, title = "Test", cover = "", addedAt = 1L,
    )

    private fun history(source: String, series: String, timestamp: Long) =
        HistoryEntry(
            chapterKey = "$source|chapter", title = "Test",
            sourceId = source, seriesId = series, coverPath = "",
            page = 0, total = 1, updatedAt = timestamp,
        )

    @Test fun scopedRemovalLeavesSameIdFromOtherSourceUntouched() {
        val saved = listOf(entry("a", "same"), entry("b", "same"))
        assertEquals(
            listOf(entry("b", "same")),
            withoutLibraryIdentities(saved, setOf("a" to "same")),
        )
    }

    @Test fun rejectedCollisionCannotRewriteSavedOwnersCategories() {
        val saved = listOf(entry("original", "shared"))
        val proposed = listOf(
            entry("incoming", "shared"),
            entry("incoming", "new"),
        )
        val accepted = acceptedLibraryImportEntries(saved, proposed)
        assertEquals(listOf("new"), accepted.map { it.seriesId })
        val identities = accepted.map { it.sourceId to it.seriesId }.toSet()
        val result = acceptedBackupCategoryAssignments(
            listOf(
                ("incoming" to "shared") to setOf("incorrect"),
                ("incoming" to "new") to setOf("correct"),
            ),
            identities,
        )
        assertFalse("shared" in result)
        assertEquals(setOf("correct"), result["new"])
    }

    @Test fun acceptedOwnerWinsCategoryWhenBackupContainsSameIdTwice() {
        val identities = setOf("kept" to "same")
        val result = acceptedBackupCategoryAssignments(
            listOf(
                ("kept" to "same") to setOf("good"),
                ("other" to "same") to setOf("bad"),
            ), identities,
        )
        assertEquals(setOf("good"), result["same"])
    }

    @Test fun skippedSourcesCannotReplaceReadingHistory() {
        val allowed = history("kept", "shared", 1L)
        val rejected = history("other", "shared", 2L)
        val filtered = acceptedBackupHistory(
            listOf(allowed, rejected),
            setOf("kept" to "shared"),
        )
        assertEquals(listOf(allowed), filtered)
    }

    @Test fun emptyAcceptedSetHasNoCategoryOrHistorySideEffects() {
        assertTrue(acceptedBackupCategoryAssignments(
            listOf(("a" to "s") to setOf("cat")), emptySet(),
        ).isEmpty())
        assertTrue(acceptedBackupHistory(
            listOf(history("a", "s", 10L)), emptySet(),
        ).isEmpty())
    }
}
