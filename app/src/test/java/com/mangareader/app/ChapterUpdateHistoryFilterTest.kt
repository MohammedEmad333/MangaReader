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

    @Test fun matchesSourceAndShowsNoResultsForUnknownQuery() {
        assertEquals(listOf(entries[1]), filterChapterUpdateEvents(entries, "source-b"))
        assertEquals(emptyList<ChapterUpdateEvent>(), filterChapterUpdateEvents(entries, "missing"))
    }
}
