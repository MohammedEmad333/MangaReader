package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateDetectorTest {
    private fun ch(id: String, name: String) = Chapter(id = id, name = name)

    @Test fun initialIndexIsNotAnUpdate() {
        assertEquals(emptyList<Chapter>(), ChapterUpdateDetector.newlyAvailable(
            emptyList(), listOf(ch("1", "Chapter 1"))
        ))
    }

    @Test fun detectsOnlyUnseenChapters() {
        val old = listOf(ch("1", "Chapter 1"))
        val fresh = listOf(ch("2", "Chapter 2"), ch("1", "Chapter 1"))
        assertEquals(listOf("2"), ChapterUpdateDetector.newlyAvailable(old, fresh).map { it.id })
    }

    @Test fun changedUrlsAndOrderingDoNotTriggerFalseUpdates() {
        val old = listOf(ch("old-1", "Chapter 1"), ch("old-2", "Chapter 2"))
        val fresh = listOf(ch("new-2", " chapter  2 "), ch("new-1", "CHAPTER 1"))
        assertEquals(emptyList<Chapter>(), ChapterUpdateDetector.newlyAvailable(old, fresh))
    }

    @Test fun duplicateResultsCountOnceAndMissingChaptersDoNotCount() {
        val old = listOf(ch("1", "Chapter 1"))
        val fresh = listOf(ch("2", "Chapter 2"), ch("2", "Chapter 2"), ch("1", "Chapter 1"))
        assertEquals(1, ChapterUpdateDetector.newlyAvailable(old, fresh).size)
        assertEquals(emptyList<Chapter>(), ChapterUpdateDetector.newlyAvailable(old, emptyList()))
    }
}
