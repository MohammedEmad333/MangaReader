package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesViewActiveTest {

    @Test
    fun sourcesViewIsActive_detectsAnyNonDefaultControl() {
        assertFalse(sourcesViewIsActive("", false, "All"))
        assertTrue(sourcesViewIsActive("manga", false, "All"))
        assertTrue(sourcesViewIsActive("", true, "All"))
        assertTrue(sourcesViewIsActive("", false, "Manga"))
        assertTrue(sourcesViewIsActive("", false, "Anime"))
        assertFalse(sourcesViewIsActive("", false, "Unknown"))
    }
}
