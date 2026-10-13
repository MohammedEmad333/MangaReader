package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterUpdateNavigationTest {
    private fun saved(source: String, series: String) =
        LibraryEntry(seriesId = series, sourceId = source, title = "Same title", cover = "", addedAt = 1L)
    private fun update(source: String, series: String) =
        ChapterUpdateEvent(source, series, "Same title", "chapter", "New chapter", 1L)

    @Test fun opensOnlyExactSourceAndSeriesMatch() {
        val other = saved("b", "same")
        val target = saved("a", "same")
        assertEquals(target, findUpdateLibraryEntry(listOf(other, target), update("a", "same")))
    }

    @Test fun removedSeriesCannotOpenUnrelatedTitle() {
        assertNull(findUpdateLibraryEntry(listOf(saved("b", "same")), update("a", "same")))
        assertNull(findUpdateLibraryEntry(emptyList(), update("a", "same")))
    }
}
