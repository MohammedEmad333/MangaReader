package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIdClassificationTest {
    @Test
    fun classifiesMangaExtensionSources() {
        assertTrue("tachi:123".isMangaExtensionSourceId())
        assertTrue("tachi:123".isExtensionSourceId())
        assertFalse("tachi:123".isLocalSourceId())
    }

    @Test
    fun classifiesAnimeExtensionSources() {
        assertTrue("aniyomi:123".isAnimeExtensionSourceId())
        assertTrue("aniyomi:123".isExtensionSourceId())
        assertFalse("aniyomi:123".isLocalSourceId())
    }

    @Test
    fun classifiesLocalSources() {
        assertTrue("local:downloads".isLocalSourceId())
        assertFalse("local:downloads".isExtensionSourceId())
        assertTrue("".isLocalSourceId())
    }
}
