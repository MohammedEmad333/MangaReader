package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GlobalSearchScrollSignatureTest {
    @Test fun streamingResultsDoNotResetScrollSignature() {
        val unchanged = globalSearchScrollSignature("one piece", false, false, "All", false)
        assertEquals(unchanged, globalSearchScrollSignature("one piece", false, false, "All", false))
    }

    @Test fun changingSearchOrFiltersResetsScrollSignature() {
        val base = globalSearchScrollSignature("one piece", false, false, "All", false)
        assertNotEquals(base, globalSearchScrollSignature("naruto", false, false, "All", false))
        assertNotEquals(base, globalSearchScrollSignature("one piece", true, false, "All", false))
        assertNotEquals(base, globalSearchScrollSignature("one piece", false, true, "All", false))
        assertNotEquals(base, globalSearchScrollSignature("one piece", false, false, "Manga", false))
    }
}
