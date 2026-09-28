package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchSummaryTest {

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
