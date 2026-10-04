package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeVideoDownloadTest {

    @Test
    fun detectsPlainM3u8Path() {
        assertTrue(looksLikeHlsUrl("https://cdn.example.com/master.m3u8?token=abc"))
    }

    @Test
    fun detectsM3u8InsideSignedQuery() {
        assertTrue(
            looksLikeHlsUrl(
                "https://cdn.example.com/proxy?src=https%3A%2F%2Forigin.example%2Fmaster.m3u8&token=abc",
            ),
        )
    }

    @Test
    fun detectsExplicitHlsFormatHint() {
        assertTrue(looksLikeHlsUrl("https://cdn.example.com/play?id=42&format=hls"))
        assertTrue(looksLikeHlsUrl("https://cdn.example.com/play?id=42&manifest=m3u8"))
    }

    @Test
    fun doesNotGuessGenericExtensionlessVideoAsHls() {
        assertFalse(looksLikeHlsUrl("https://cdn.example.com/play?id=42&token=abc"))
        assertFalse(looksLikeHlsUrl("https://cdn.example.com/video.mp4?token=abc"))
    }
}
