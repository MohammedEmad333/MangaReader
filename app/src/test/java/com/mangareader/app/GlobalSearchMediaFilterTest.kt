package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalSearchMediaFilterTest {
    @Test
    fun mapsSupportedMediaFilters() {
        assertNull(globalSearchMediaIsAnime("All"))
        assertEquals(false, globalSearchMediaIsAnime("Manga"))
        assertEquals(true, globalSearchMediaIsAnime("Anime"))
    }

    @Test
    fun unknownFilterFallsBackToAllMedia() {
        assertNull(globalSearchMediaIsAnime("unexpected"))
    }
}
