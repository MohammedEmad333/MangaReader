package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryViewActiveTest {

    @Test
    fun historyViewIsActive_detectsSearchOrNonDefaultFilter() {
        assertFalse(historyViewIsActive("All", ""))
        assertTrue(historyViewIsActive("Manga", ""))
        assertTrue(historyViewIsActive("Anime", ""))
        assertTrue(historyViewIsActive("All", "one piece"))
        assertTrue(historyViewIsActive("Unknown", "query"))
    }
}
