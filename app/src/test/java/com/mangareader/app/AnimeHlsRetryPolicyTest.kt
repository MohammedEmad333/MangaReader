package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeHlsRetryPolicyTest {
    @Test
    fun `initial run and first retry may retry`() {
        assertTrue(shouldRetryHlsDownload(0))
        assertTrue(shouldRetryHlsDownload(1))
    }

    @Test
    fun `third run is terminal`() {
        assertFalse(shouldRetryHlsDownload(2))
        assertFalse(shouldRetryHlsDownload(3))
    }
}
