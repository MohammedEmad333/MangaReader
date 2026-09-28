package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionsViewPrefsTest {

    @Test
    fun normalizeExtensionsMediaFilter_acceptsKnownValuesAndFallsBack() {
        assertEquals("All", normalizeExtensionsMediaFilter("All"))
        assertEquals("Manga", normalizeExtensionsMediaFilter("Manga"))
        assertEquals("Anime", normalizeExtensionsMediaFilter("Anime"))
        assertEquals("All", normalizeExtensionsMediaFilter(null))
        assertEquals("All", normalizeExtensionsMediaFilter(""))
        assertEquals("All", normalizeExtensionsMediaFilter("Unknown"))
    }
}
