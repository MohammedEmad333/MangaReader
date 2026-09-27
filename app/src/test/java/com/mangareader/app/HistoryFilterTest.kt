package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {

    private val manga = HistoryEntry(
        chapterKey = "m1",
        title = "One Piece",
        sourceId = "s1",
        seriesId = "one-piece",
        coverPath = "",
        page = 2,
        total = 20,
        updatedAt = 1L,
        mediaType = "manga",
        detail = "Chapter 1100",
    )

    private val anime = HistoryEntry(
        chapterKey = "a1",
        title = "Frieren",
        sourceId = "s2",
        seriesId = "frieren",
        coverPath = "",
        page = 0,
        total = 0,
        updatedAt = 2L,
        mediaType = "anime",
        detail = "Episode 12",
    )

    @Test
    fun filterHistory_combinesMediaAndSearch() {
        val all = listOf(manga, anime)

        assertEquals(listOf(manga), filterHistory(all, "Manga", "piece"))
        assertEquals(listOf(anime), filterHistory(all, "Anime", "episode 12"))
        assertEquals(listOf(anime), filterHistory(all, "All", "frieren"))
        assertEquals(emptyList<HistoryEntry>(), filterHistory(all, "Manga", "frieren"))
    }
}
