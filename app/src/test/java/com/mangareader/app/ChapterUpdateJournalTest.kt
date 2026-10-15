package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateJournalTest {
    @Test fun duplicateDiscoveryPreservesExistingReadState() {
        val read = ChapterUpdateEvent("a", "s", "Series", "c", "Chapter", 10L, isRead = true)
        val repeated = ChapterUpdateEvent("a", "s", "Series", "c", "Chapter", 20L)
        val result = ChapterUpdateJournal.merge(listOf(read), listOf(repeated))
        assertEquals(1, result.size)
        assertEquals(true, result.single().isRead)
        assertEquals(20L, result.single().detectedAt)
    }

    @Test fun newChapterRemainsUnreadByDefault() {
        val result = ChapterUpdateJournal.merge(emptyList(), listOf(event("a", "s", "new", 40L)))
        assertEquals(false, result.single().isRead)
    }

    private fun event(source: String, series: String, chapter: String, at: Long) =
        ChapterUpdateEvent(source, series, "Series", chapter, "Chapter", at)

    @Test fun duplicateChapterIsKeptOnceAtNewestTimestamp() {
        val previous = listOf(event("source", "series", "chapter", 10L))
        val incoming = listOf(event("source", "series", "chapter", 20L))
        val merged = ChapterUpdateJournal.merge(previous, incoming)
        assertEquals(1, merged.size)
        assertEquals(20L, merged.single().detectedAt)
    }

    @Test fun differentSourcesAndSeriesRemainSeparate() {
        val result = ChapterUpdateJournal.merge(
            listOf(event("a", "one", "same", 1L)),
            listOf(event("b", "one", "same", 2L), event("a", "two", "same", 3L)),
        )
        assertEquals(3, result.size)
        assertEquals(listOf(3L, 2L, 1L), result.map { it.detectedAt })
    }

    @Test fun historyIsBoundedToNewestFiveHundred() {
        val incoming = (0..600).map { event("a", "one", "chapter-$it", it.toLong()) }
        val result = ChapterUpdateJournal.merge(emptyList(), incoming)
        assertEquals(500, result.size)
        assertEquals(600L, result.first().detectedAt)
        assertEquals(101L, result.last().detectedAt)
    }

    @Test fun noIncomingEventsPreservesExistingHistory() {
        val previous = listOf(event("a", "one", "chapter", 42L))
        assertEquals(previous, ChapterUpdateJournal.merge(previous, emptyList()))
    }
}
