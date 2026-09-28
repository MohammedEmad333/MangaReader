package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseTabPreferenceTest {

    @Test
    fun normalizeBrowseTab_clampsToSourcesOrExtensions() {
        assertEquals(0, normalizeBrowseTab(-1))
        assertEquals(0, normalizeBrowseTab(0))
        assertEquals(1, normalizeBrowseTab(1))
        assertEquals(1, normalizeBrowseTab(2))
        assertEquals(1, normalizeBrowseTab(99))
    }
}
