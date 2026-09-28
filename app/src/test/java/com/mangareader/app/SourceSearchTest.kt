package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchTest {

    private val row = BrowseRow(
        id = "tachi:test",
        name = "Manga Plus",
        lang = "English",
        iconPkg = null,
        isNsfw = false,
        configurable = false,
        isAnime = false,
        config = null,
        source = null,
    )

    @Test
    fun sourceMatchesQuery_matchesNameOrLanguageIgnoringCase() {
        assertTrue(sourceMatchesQuery(row, ""))
        assertTrue(sourceMatchesQuery(row, "manga"))
        assertTrue(sourceMatchesQuery(row, "PLUS"))
        assertTrue(sourceMatchesQuery(row, "english"))
        assertFalse(sourceMatchesQuery(row, "arabic"))
    }
}
