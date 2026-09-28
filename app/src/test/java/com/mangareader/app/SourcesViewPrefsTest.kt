package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcesViewPrefsTest {

    @Test
    fun normalizeSourcesQuery_handlesNullAndCapsLength() {
        assertEquals("", normalizeSourcesQuery(null))
        assertEquals("manga", normalizeSourcesQuery("manga"))
        assertEquals(200, normalizeSourcesQuery("x".repeat(250)).length)
    }
}
