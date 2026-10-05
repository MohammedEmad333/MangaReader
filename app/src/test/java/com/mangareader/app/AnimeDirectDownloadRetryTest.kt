package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeDirectDownloadRetryTest {
    @Test
    fun `direct download retries only once`() {
        assertTrue(shouldRetryDirectAnimeDownload(0))
        assertFalse(shouldRetryDirectAnimeDownload(1))
        assertFalse(shouldRetryDirectAnimeDownload(2))
    }
}
