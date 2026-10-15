package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterUpdateCheckSummaryTest {
    @Test fun emptyHistoryDoesNotClaimSuccess() {
        assertEquals("No automatic check completed yet", scheduledCheckOutcomeLabel(null))
    }

    @Test fun failuresAndMissingSourcesAreVisible() {
        val summary = ChapterUpdateCheckSummary(1234L, 8, 2, 2)
        assertEquals("8 checked • 2 failed • 2 unavailable", scheduledCheckOutcomeLabel(summary))
    }
}
