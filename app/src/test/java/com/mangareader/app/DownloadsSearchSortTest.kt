package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSearchSortTest {

    private val alpha = DownloadedSeries(
        sourceId = "source",
        seriesId = "alpha",
        title = "Alpha",
        cover = "",
        chapters = listOf(
            DownloadedChapter("a1", "Chapter 1"),
            DownloadedChapter("a2", "Final Battle"),
        ),
        sizeBytes = 300,
    )
    private val beta = DownloadedSeries(
        sourceId = "source",
        seriesId = "beta",
        title = "Beta",
        cover = "",
        chapters = listOf(DownloadedChapter("b1", "Episode 12")),
        sizeBytes = 900,
    )
    private val gamma = DownloadedSeries(
        sourceId = "source",
        seriesId = "gamma",
        title = "Gamma",
        cover = "",
        chapters = listOf(
            DownloadedChapter("g1", "One"),
            DownloadedChapter("g2", "Two"),
            DownloadedChapter("g3", "Three"),
        ),
        sizeBytes = 100,
    )

    @Test
    fun search_matchesSeriesAndChapterNames_caseInsensitively() {
        assertEquals(
            listOf("alpha"),
            filterAndSortDownloads(
                listOf(alpha, beta, gamma),
                query = "ALP",
                sortMode = DownloadsSortMode.TITLE,
                descending = false,
            ).map { it.seriesId },
        )
        assertEquals(
            listOf("alpha"),
            filterAndSortDownloads(
                listOf(alpha, beta, gamma),
                query = "battle",
                sortMode = DownloadsSortMode.TITLE,
                descending = false,
            ).map { it.seriesId },
        )
    }

    @Test
    fun sizeSort_respectsDirection() {
        val input = listOf(alpha, beta, gamma)
        assertEquals(
            listOf("gamma", "alpha", "beta"),
            filterAndSortDownloads(input, "", DownloadsSortMode.SIZE, false)
                .map { it.seriesId },
        )
        assertEquals(
            listOf("beta", "alpha", "gamma"),
            filterAndSortDownloads(input, "", DownloadsSortMode.SIZE, true)
                .map { it.seriesId },
        )
    }

    @Test
    fun mediaFilter_separatesMangaAndAnime() {
        val anime = beta.copy(sourceId = "anime:source")
        val manga = alpha.copy(sourceId = "manga:source")
        assertEquals(
            listOf("beta"),
            filterAndSortDownloads(
                listOf(manga, anime),
                "",
                DownloadsSortMode.TITLE,
                false,
                DownloadsMediaFilter.ANIME,
            ).map { it.seriesId },
        )
        assertEquals(
            listOf("alpha"),
            filterAndSortDownloads(
                listOf(manga, anime),
                "",
                DownloadsSortMode.TITLE,
                false,
                DownloadsMediaFilter.MANGA,
            ).map { it.seriesId },
        )
    }

    @Test
    fun chapterCountSort_ordersByDownloadedCount() {
        assertEquals(
            listOf("beta", "alpha", "gamma"),
            filterAndSortDownloads(
                listOf(alpha, beta, gamma),
                "",
                DownloadsSortMode.CHAPTERS,
                false,
            ).map { it.seriesId },
        )
    }
}
