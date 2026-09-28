package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcesVisibleSummaryTest {

    @Test
    fun sourceVisibilitySummaryLabel_formatsAndClampsCounts() {
        assertEquals("12 of 40 sources", sourceVisibilitySummaryLabel(12, 40))
        assertEquals("0 of 0 sources", sourceVisibilitySummaryLabel(-1, -5))
    }
}
