package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcesMediaFilterTest {

    @Test
    fun normalizeSourcesMediaFilter_acceptsKnownValuesAndFallsBack() {
        assertEquals("All", normalizeSourcesMediaFilter("All"))
        assertEquals("Manga", normalizeSourcesMediaFilter("Manga"))
        assertEquals("Anime", normalizeSourcesMediaFilter("Anime"))
        assertEquals("All", normalizeSourcesMediaFilter(null))
        assertEquals("All", normalizeSourcesMediaFilter(""))
        assertEquals("All", normalizeSourcesMediaFilter("Unknown"))
    }
}
