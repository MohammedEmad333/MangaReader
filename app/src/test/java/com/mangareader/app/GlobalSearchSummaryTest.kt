package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchSummaryTest {

    @Test
    fun globalSearchSubmission_requiresTextAndIdleSearch() {
        assertEquals(false, canSubmitGlobalSearch("", false))
        assertEquals(false, canSubmitGlobalSearch("  ", false))
        assertEquals(false, canSubmitGlobalSearch("manga", true))
        assertEquals(true, canSubmitGlobalSearch(" manga ", false))
    }

    @Test
    fun globalSearchProgress_tracksSourcesAndClampsOutOfRangeValues() {
        assertEquals(0f, globalSearchProgress(0, 10), 0.0001f)
        assertEquals(0.25f, globalSearchProgress(1, 4), 0.0001f)
        assertEquals(1f, globalSearchProgress(4, 4), 0.0001f)
        assertEquals(1f, globalSearchProgress(9, 4), 0.0001f)
        assertEquals(0f, globalSearchProgress(-2, 4), 0.0001f)
        assertEquals(0f, globalSearchProgress(5, 0), 0.0001f)
    }


    @Test
    fun globalSearchSummaryLabel_formatsAndClampsCounts() {
        assertEquals(
            "Searched 12 of 20 sources · 5 with results · 37 titles",
            globalSearchSummaryLabel(12, 20, 5, 37),
        )
        assertEquals(
            "Searched 0 of 0 sources · 0 with results · 0 titles",
            globalSearchSummaryLabel(-1, -2, -3, -4),
        )
    }
}
