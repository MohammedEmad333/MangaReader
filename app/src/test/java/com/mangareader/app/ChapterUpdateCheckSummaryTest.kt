package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateCheckSummaryTest {
    @Test fun emptyHistoryDoesNotClaimSuccess() {
        assertEquals("No automatic check completed yet", scheduledCheckOutcomeLabel(null))
    }

    @Test fun failuresAndMissingSourcesAreVisible() {
        val summary = ChapterUpdateCheckSummary(1234L, 8, 2, 2)
        assertEquals("8 checked • 0 new chapters • 2 failed • 2 unavailable", scheduledCheckOutcomeLabel(summary))
    }

    @Test fun newlyDetectedChaptersAreIncludedInSummary() {
        val summary = ChapterUpdateCheckSummary(1234L, 8, 0, 0, 5)
        assertEquals("8 checked • 5 new chapters • 0 failed • 0 unavailable", scheduledCheckOutcomeLabel(summary))
    }
}
