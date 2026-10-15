package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateGroupingTest {
    private fun event(source: String, series: String, chapter: String, timestamp: Long) =
        ChapterUpdateEvent(source, series, "Title", chapter, chapter, timestamp)

    @Test fun groupsByDayAndSeriesWithoutMixingSources() {
        val updates = listOf(
            event("a", "same", "one", 100),
            event("b", "same", "two", 110),
            event("a", "same", "three", 120),
            event("a", "same", "four", 20),
        )
        val grouped = groupChapterUpdatesByDay(updates) { if (it >= 100) "today" else "yesterday" }
        assertEquals(listOf("today", "yesterday"), grouped.keys.toList())
        assertEquals(2, grouped["today"]!!.size)
        assertEquals(listOf("three", "one"), grouped["today"]!![0].events.map { it.chapterId })
        assertEquals("b", grouped["today"]!![1].sourceId)
        assertEquals(1, grouped["yesterday"]!!.size)
    }

    @Test fun emptyInputHasNoGroups() {
        assertEquals(emptyMap<String, List<ChapterUpdateGroup>>(), groupChapterUpdatesByDay(emptyList()) { "day" })
    }
}
