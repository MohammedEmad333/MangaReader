package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsViewActiveTest {

    @Test
    fun downloadsViewIsActive_detectsAnyNonDefaultControl() {
        assertFalse(
            downloadsViewIsActive(
                query = "",
                sortMode = DownloadsSortMode.SIZE,
                descending = true,
                mediaFilter = DownloadsMediaFilter.ALL,
            ),
        )
        assertTrue(downloadsViewIsActive("one piece", DownloadsSortMode.SIZE, true, DownloadsMediaFilter.ALL))
        assertTrue(downloadsViewIsActive("", DownloadsSortMode.TITLE, true, DownloadsMediaFilter.ALL))
        assertTrue(downloadsViewIsActive("", DownloadsSortMode.SIZE, false, DownloadsMediaFilter.ALL))
        assertTrue(downloadsViewIsActive("", DownloadsSortMode.SIZE, true, DownloadsMediaFilter.ANIME))
    }
}
