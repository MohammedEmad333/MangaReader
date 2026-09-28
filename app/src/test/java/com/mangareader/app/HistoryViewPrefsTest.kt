package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryViewPrefsTest {

    @Test
    fun normalizeHistoryMediaFilter_acceptsKnownValues() {
        assertEquals("All", normalizeHistoryMediaFilter("All"))
        assertEquals("Manga", normalizeHistoryMediaFilter("Manga"))
        assertEquals("Anime", normalizeHistoryMediaFilter("Anime"))
    }

    @Test
    fun normalizeHistoryMediaFilter_fallsBackToAll() {
        assertEquals("All", normalizeHistoryMediaFilter(null))
        assertEquals("All", normalizeHistoryMediaFilter(""))
        assertEquals("All", normalizeHistoryMediaFilter("Unknown"))
    }
}
