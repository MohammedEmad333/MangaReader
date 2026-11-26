package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedMangaUpdateDetectionTest {
    @Test fun baseHttpSourceDoesNotUseCombinedExtensionPath() {
        assertFalse(hasCombinedMangaUpdateImplementation(eu.kanade.tachiyomi.source.online.HttpSource::class.java))
    }

    @Test fun inheritedCombinedMethodIsDetected() {
        assertTrue(hasCombinedMangaUpdateImplementation(CombinedSource::class.java))
    }

    private open class CombinedSource {
        fun getMangaUpdate() = Unit
    }
}
