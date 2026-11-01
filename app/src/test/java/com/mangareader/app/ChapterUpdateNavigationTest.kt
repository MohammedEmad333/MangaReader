package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterUpdateNavigationTest {
    @Test fun selectsOnlyExactChapterId() {
        val chapters = listOf(Chapter("chapter-a", "First"), Chapter("chapter-b", "Second"))
        assertEquals(1, findUpdateChapterIndex(chapters, "chapter-b"))
        assertNull(findUpdateChapterIndex(chapters, "missing"))
        assertNull(findUpdateChapterIndex(emptyList(), "chapter-a"))
    }

    @Test fun blankAndDuplicateChapterIdentifiersDoNotNavigate() {
        val chapters = listOf(
            Chapter("same", "First"),
            Chapter("same", "Second"),
            Chapter("unique", "Third"),
        )
        assertNull(findUpdateChapterIndex(chapters, ""))
        assertNull(findUpdateChapterIndex(chapters, "same"))
        assertEquals(2, findUpdateChapterIndex(chapters, "unique"))
    }

    private fun saved(source: String, series: String) =
        LibraryEntry(seriesId = series, sourceId = source, title = "Same title", cover = "", addedAt = 1L)
    private fun update(source: String, series: String) =
        ChapterUpdateEvent(source, series, "Same title", "chapter", "New chapter", 1L)

    @Test fun opensOnlyExactSourceAndSeriesMatch() {
        val other = saved("b", "same")
        val target = saved("a", "same")
        assertEquals(target, findUpdateLibraryEntry(listOf(other, target), update("a", "same")))
    }

    @Test fun duplicateAndBlankLibraryIdentifiersNeverChooseAnArbitraryEntry() {
        val duplicate = saved("a", "same")
        assertNull(findUpdateLibraryEntry(listOf(duplicate, duplicate), update("a", "same")))
        assertNull(findUpdateLibraryEntry(listOf(saved("", "same")), update("", "same")))
        assertNull(findUpdateLibraryEntry(listOf(saved("a", "")), update("a", "")))
        assertNull(findUpdateLibraryEntry(listOf(saved("a", "same")), update("a", "")))
    }

    @Test fun removedSeriesCannotOpenUnrelatedTitle() {
        assertNull(findUpdateLibraryEntry(listOf(saved("b", "same")), update("a", "same")))
        assertNull(findUpdateLibraryEntry(emptyList(), update("a", "same")))
    }
}
