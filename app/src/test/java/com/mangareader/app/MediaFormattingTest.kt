package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFormattingTest {
    @Test
    fun formatsSubHourPlayback() {
        assertEquals("0:00", formatMediaTime(0))
        assertEquals("0:05", formatMediaTime(5_000))
        assertEquals("12:34", formatMediaTime(754_000))
    }

    @Test
    fun formatsHourPlayback() {
        assertEquals("1:00:00", formatMediaTime(3_600_000))
        assertEquals("2:03:04", formatMediaTime(7_384_000))
    }

    @Test
    fun clampsNegativeValues() {
        assertEquals("0:00", formatMediaTime(-1))
    }
}
