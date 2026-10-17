package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateHistoryFilterTest {
    private val entries = listOf(
        ChapterUpdateEvent("source-a", "series-1", "Blue Night", "ch-2", "Chapter Two", 20L),
        ChapterUpdateEvent("source-b", "series-2", "Red Dawn", "ch-3", "Final Chapter", 10L),
    )

    @Test fun blankSearchRetainsOrder() {
        assertEquals(entries, filterChapterUpdateEvents(entries, "  "))
    }

    @Test fun matchesSeriesOrChapterIgnoringCase() {
        assertEquals(listOf(entries[0]), filterChapterUpdateEvents(entries, "blue"))
        assertEquals(listOf(entries[1]), filterChapterUpdateEvents(entries, "FINAL"))
    }

    @Test fun separatesReadAndUnreadWithoutChangingNewestFirstOrder() {
        val history = listOf(
            entries[0],
            entries[1].copy(isRead = true),
            entries[0].copy(chapterId = "ch-4", detectedAt = 5L),
        )
        assertEquals(history, filterChapterUpdateEvents(history, "", ChapterUpdateReadFilter.ALL))
        assertEquals(
            listOf(history[0], history[2]),
            filterChapterUpdateEvents(history, "", ChapterUpdateReadFilter.UNREAD),
        )
        assertEquals(
            listOf(history[1]),
            filterChapterUpdateEvents(history, "", ChapterUpdateReadFilter.READ),
        )
    }

    @Test fun composesSearchWithReadStatusFilter() {
        val history = listOf(entries[0], entries[1].copy(isRead = true))
        assertEquals(
            emptyList<ChapterUpdateEvent>(),
            filterChapterUpdateEvents(history, "Final", ChapterUpdateReadFilter.UNREAD),
        )
        assertEquals(
            listOf(history[1]),
            filterChapterUpdateEvents(history, "FINAL", ChapterUpdateReadFilter.READ),
        )
        assertEquals(
            emptyList<ChapterUpdateEvent>(),
            filterChapterUpdateEvents(emptyList(), "", ChapterUpdateReadFilter.READ),
        )
    }

    @Test fun matchesSourceAndShowsNoResultsForUnknownQuery() {
        assertEquals(listOf(entries[1]), filterChapterUpdateEvents(entries, "source-b"))
        assertEquals(emptyList<ChapterUpdateEvent>(), filterChapterUpdateEvents(entries, "missing"))
    }
}
