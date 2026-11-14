package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPreviewImageSizeTest {
    @Test fun normalDensityUsesThumbnailDimensions() {
        assertEquals(72 to 96, readerPreviewDecodeSize(1f))
        assertEquals(144 to 192, readerPreviewDecodeSize(2f))
        assertEquals(216 to 288, readerPreviewDecodeSize(3f))
    }

    @Test fun veryDenseScreensRemainBounded() {
        assertEquals(512 to 512, readerPreviewDecodeSize(10f))
        val (width, height) = readerPreviewDecodeSize(100f)
        assertTrue(width in 1..512)
        assertTrue(height in 1..512)
    }

    @Test fun invalidDensityFallsBackToSafeDimensions() {
        assertEquals(72 to 96, readerPreviewDecodeSize(Float.NaN))
        assertEquals(72 to 96, readerPreviewDecodeSize(Float.POSITIVE_INFINITY))
        assertEquals(72 to 96, readerPreviewDecodeSize(0f))
        assertEquals(72 to 96, readerPreviewDecodeSize(-2f))
    }
}
