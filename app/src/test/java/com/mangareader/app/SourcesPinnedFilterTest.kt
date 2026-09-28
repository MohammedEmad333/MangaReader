package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesPinnedFilterTest {

    @Test
    fun sourcePassesPinnedFilter_respectsPinnedOnlyMode() {
        val pinned = setOf("source:a", "source:b")
        assertTrue(sourcePassesPinnedFilter("source:c", false, pinned))
        assertTrue(sourcePassesPinnedFilter("source:a", true, pinned))
        assertFalse(sourcePassesPinnedFilter("source:c", true, pinned))
    }
}
