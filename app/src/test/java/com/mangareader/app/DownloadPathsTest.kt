package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPathsTest {

    @Test
    fun clean_replacesFilesystemIllegalCharacters() {
        assertEquals("Series Name Chapter", DownloadPaths.clean("""Series:Name/Chapter?""", "fallback"))
    }

    @Test
    fun clean_collapsesWhitespaceAndTrimsTrailingDots() {
        assertEquals("Chapter 12", DownloadPaths.clean("  Chapter   12...  ", "fallback"))
    }

    @Test
    fun clean_prefixesWindowsReservedNames() {
        assertEquals("_CON", DownloadPaths.clean("CON", "fallback"))
        assertEquals("_lpt1", DownloadPaths.clean("lpt1", "fallback"))
    }

    @Test
    fun clean_usesFallbackWhenNothingRemains() {
        assertEquals("fallback", DownloadPaths.clean("   ", "fallback"))
    }

    @Test
    fun clean_limitsFolderLength() {
        assertEquals(60, DownloadPaths.clean("a".repeat(100), "fallback").length)
    }
}
