package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMediaFilterTest {

    @Test
    fun mediaFilter_fromStoredKey_restoresExpectedChoice() {
        assertEquals(LibraryMediaFilter.ALL, LibraryMediaFilter.from(null))
        assertEquals(LibraryMediaFilter.ALL, LibraryMediaFilter.from("unknown"))
        assertEquals(LibraryMediaFilter.MANGA, LibraryMediaFilter.from("manga"))
        assertEquals(LibraryMediaFilter.ANIME, LibraryMediaFilter.from("anime"))
    }
}
