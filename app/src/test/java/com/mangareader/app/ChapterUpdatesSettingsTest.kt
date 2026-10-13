package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdatesSettingsTest {
    @Test fun emptyStateUsesFriendlyLabel() {
        assertEquals("No new chapters detected yet", chapterUpdatesCountLabel(0))
        assertEquals("No new chapters detected yet", chapterUpdatesCountLabel(-1))
    }

    @Test fun nonEmptyStateDisplaysDetectedCount() {
        assertEquals("1 recent chapter updates", chapterUpdatesCountLabel(1))
        assertEquals("23 recent chapter updates", chapterUpdatesCountLabel(23))
    }
}
