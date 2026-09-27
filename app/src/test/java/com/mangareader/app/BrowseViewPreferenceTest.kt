package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseViewPreferenceTest {

    @Test
    fun browseViewPreferenceKey_isScopedPerSource() {
        assertEquals(KEY_BROWSE_VIEW, browseViewPreferenceKey(""))
        assertEquals("browse_view:tachi:123", browseViewPreferenceKey("tachi:123"))
        assertEquals("browse_view:aniyomi:456", browseViewPreferenceKey("aniyomi:456"))
    }
}
