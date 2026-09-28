package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionsViewActiveTest {

    @Test
    fun extensionsViewIsActive_detectsAnyNonDefaultControl() {
        assertFalse(extensionsViewIsActive("", false, "All"))
        assertTrue(extensionsViewIsActive("aniyomi", false, "All"))
        assertTrue(extensionsViewIsActive("", true, "All"))
        assertTrue(extensionsViewIsActive("", false, "Manga"))
        assertTrue(extensionsViewIsActive("", false, "Anime"))
        assertFalse(extensionsViewIsActive("", false, "Unknown"))
    }
}
