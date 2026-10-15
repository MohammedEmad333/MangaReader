package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChapterUpdateNotificationGroupingTest {
    @Test fun sameSeriesReusesOneNotification() {
        assertEquals(chapterUpdateNotificationTag("source", "series"), chapterUpdateNotificationTag("source", "series"))
    }

    @Test fun differentSourcesDoNotReplaceEachOther() {
        assertNotEquals(chapterUpdateNotificationTag("source-a", "series"), chapterUpdateNotificationTag("source-b", "series"))
    }

    @Test fun differentSeriesDoNotReplaceEachOther() {
        assertNotEquals(chapterUpdateNotificationTag("source", "series-a"), chapterUpdateNotificationTag("source", "series-b"))
    }
}
