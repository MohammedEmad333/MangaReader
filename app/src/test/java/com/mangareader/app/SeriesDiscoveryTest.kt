package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesDiscoveryTest {

    @Test
    fun `prefers a descriptive genre over broad format tags`() {
        val series = Series(
            id = "1",
            title = "Example",
            cover = null,
            genres = listOf("Manga", "Action", "Fantasy"),
        )

        assertEquals("Action", SeriesDiscovery.similarQuery(series))
    }

    @Test
    fun `falls back to author when genres are absent`() {
        val series = Series(
            id = "1",
            title = "Example",
            cover = null,
            author = "Jane Doe",
        )

        assertEquals("Jane Doe", SeriesDiscovery.similarQuery(series))
    }

    @Test
    fun `returns null without useful metadata`() {
        val series = Series(id = "1", title = "Example", cover = null)

        assertNull(SeriesDiscovery.similarQuery(series))
    }
}
