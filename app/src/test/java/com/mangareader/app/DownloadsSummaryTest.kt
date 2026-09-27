package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSummaryTest {

    @Test
    fun summarizeDownloads_countsSeriesItemsAndBytes() {
        val series = listOf(
            DownloadedSeries(
                sourceId = "source-a",
                seriesId = "series-a",
                title = "A",
                cover = "",
                chapters = listOf(
                    DownloadedChapter("a1", "Chapter 1"),
                    DownloadedChapter("a2", "Chapter 2"),
                ),
                sizeBytes = 1_500L,
            ),
            DownloadedSeries(
                sourceId = "source-b",
                seriesId = "series-b",
                title = "B",
                cover = "",
                chapters = listOf(DownloadedChapter("b1", "Episode 1")),
                sizeBytes = 2_500L,
            ),
        )

        assertEquals(
            DownloadsSummary(seriesCount = 2, downloadCount = 3, sizeBytes = 4_000L),
            summarizeDownloads(series),
        )
    }

    @Test
    fun summarizeDownloads_handlesEmptyList() {
        assertEquals(
            DownloadsSummary(seriesCount = 0, downloadCount = 0, sizeBytes = 0L),
            summarizeDownloads(emptyList()),
        )
    }
}
