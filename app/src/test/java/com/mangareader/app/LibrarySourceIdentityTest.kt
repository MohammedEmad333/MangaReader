package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySourceIdentityTest {
    private fun entry(source: String, id: String, title: String = "Series") =
        LibraryEntry(id, source, title, "", 1L)

    @Test fun uiKeysSeparateSourcesEvenWithSameSeriesId() {
        assertNotEquals(libraryEntryKey("a", "same"), libraryEntryKey("b", "same"))
        assertNotEquals(libraryEntryKey("a|b", "c"), libraryEntryKey("a", "b|c"))
    }

    @Test fun addingSeriesFromOtherSourceIsAConflict() {
        val existing = listOf(entry("a", "same"))
        assertTrue(hasLibrarySourceCollision(existing, entry("b", "same")))
        assertFalse(hasLibrarySourceCollision(existing, entry("a", "same")))
        assertFalse(hasLibrarySourceCollision(existing, entry("b", "other")))
    }

    @Test fun batchImportKeepsExistingOwnershipInsteadOfOverwriting() {
        val saved = listOf(entry("a", "same", "Original"), entry("x", "untouched"))
        val merged = mergeLibraryWithoutSourceCollisions(saved, listOf(
            entry("b", "same", "Incorrect"),
            entry("a", "same", "Updated"),
            entry("b", "new", "New"),
        ))
        assertEquals(listOf("same", "new", "untouched"), merged.map { it.seriesId })
        assertEquals("a", merged.first().sourceId)
        assertEquals("Updated", merged.first().title)
    }

    @Test fun importedAmbiguousNewIdIsSkipped() {
        val merged = mergeLibraryWithoutSourceCollisions(
            emptyList(), listOf(entry("a", "collision"), entry("b", "collision"), entry("c", "safe")),
        )
        assertEquals(listOf("safe"), merged.map { it.seriesId })
    }

    @Test fun sourceQualifiedLastReadGroupingDoesNotMixSources() {
        val history = listOf(
            Triple("a", "shared", 100L), Triple("b", "shared", 200L),
        )
        val mapped = history.groupBy { libraryEntryKey(it.first, it.second) }
            .mapValues { (_, rows) -> rows.maxOf { it.third } }
        assertEquals(100L, mapped[libraryEntryKey("a", "shared")])
        assertEquals(200L, mapped[libraryEntryKey("b", "shared")])
    }
}
