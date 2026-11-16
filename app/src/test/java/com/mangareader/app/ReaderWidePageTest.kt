package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWidePageTest {

    @Test
    fun rotateMode_rotatesLandscapePagesOnly() {
        assertTrue(
            shouldRotateWidePage(
                width = 2400,
                height = 1600,
                mode = ReaderWidePageMode.ROTATE_RIGHT,
            )
        )
        assertFalse(
            shouldRotateWidePage(
                width = 1600,
                height = 2400,
                mode = ReaderWidePageMode.ROTATE_RIGHT,
            )
        )
        assertFalse(
            shouldRotateWidePage(
                width = 1600,
                height = 1600,
                mode = ReaderWidePageMode.ROTATE_RIGHT,
            )
        )
    }

    @Test
    fun fitMode_neverRotates() {
        assertFalse(
            shouldRotateWidePage(
                width = 2400,
                height = 1600,
                mode = ReaderWidePageMode.FIT,
            )
        )
    }

    @Test
    fun autoWideModeRotatesOnlyInPortrait() {
        assertTrue(effectiveReaderWidePageMode(ReaderWidePageMode.AUTO_PORTRAIT, 400, 800) == ReaderWidePageMode.ROTATE_RIGHT)
        assertTrue(effectiveReaderWidePageMode(ReaderWidePageMode.AUTO_PORTRAIT, 800, 400) == ReaderWidePageMode.FIT)
        assertTrue(effectiveReaderWidePageMode(ReaderWidePageMode.AUTO_PORTRAIT, 700, 700) == ReaderWidePageMode.FIT)
        assertTrue(effectiveReaderWidePageMode(ReaderWidePageMode.AUTO_PORTRAIT, 0, 700) == ReaderWidePageMode.FIT)
        assertTrue(effectiveReaderWidePageMode(ReaderWidePageMode.FIT, 400, 800) == ReaderWidePageMode.FIT)
        assertTrue(effectiveReaderWidePageMode(ReaderWidePageMode.ROTATE_RIGHT, 800, 400) == ReaderWidePageMode.ROTATE_RIGHT)
        assertTrue(ReaderWidePageMode.from("auto_portrait") == ReaderWidePageMode.AUTO_PORTRAIT)
        assertTrue(ReaderWidePageMode.from("unknown") == ReaderWidePageMode.FIT)
    }

    @Test
    fun splitModeRequiresLandscapeSpreadAspectRatio() {
        assertTrue(shouldSplitWideReaderPage(2400, 1500, ReaderWidePageMode.SPLIT_SPREAD))
        assertFalse(shouldSplitWideReaderPage(1400, 1200, ReaderWidePageMode.SPLIT_SPREAD))
        assertFalse(shouldSplitWideReaderPage(1500, 2400, ReaderWidePageMode.SPLIT_SPREAD))
        assertFalse(shouldSplitWideReaderPage(0, 1200, ReaderWidePageMode.SPLIT_SPREAD))
        assertFalse(shouldSplitWideReaderPage(2400, -1, ReaderWidePageMode.SPLIT_SPREAD))
        assertFalse(shouldSplitWideReaderPage(2400, 1500, ReaderWidePageMode.FIT))
        assertTrue(ReaderWidePageMode.from("split_spread") == ReaderWidePageMode.SPLIT_SPREAD)
    }

    @Test
    fun splitHalfOrderRespectsReadingDirection() {
        assertFalse(readerSplitHalfShowsRight(0, false))
        assertTrue(readerSplitHalfShowsRight(1, false))
        assertTrue(readerSplitHalfShowsRight(0, true))
        assertFalse(readerSplitHalfShowsRight(1, true))
    }

    @Test
    fun invalidBounds_neverRotate() {
        assertFalse(
            shouldRotateWidePage(
                width = 0,
                height = 1600,
                mode = ReaderWidePageMode.ROTATE_RIGHT,
            )
        )
        assertFalse(
            shouldRotateWidePage(
                width = 2400,
                height = -1,
                mode = ReaderWidePageMode.ROTATE_RIGHT,
            )
        )
    }
}
