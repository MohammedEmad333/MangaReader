package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateSchedulePrefsTest {
    @Test fun unknownFrequencyFailsSafeToOff() {
        assertEquals(ChapterUpdateFrequency.OFF, ChapterUpdateFrequency.from(-10))
        assertEquals(ChapterUpdateFrequency.OFF, ChapterUpdateFrequency.from(48))
    }

    @Test fun everySupportedIntervalRoundTrips() {
        ChapterUpdateFrequency.entries.forEach {
            assertEquals(it, ChapterUpdateFrequency.from(it.hours))
        }
    }

    @Test fun cyclingSettingsReturnsToOff() {
        var option = ChapterUpdateFrequency.OFF
        repeat(ChapterUpdateFrequency.entries.size) { option = option.next() }
        assertEquals(ChapterUpdateFrequency.OFF, option)
    }
}
