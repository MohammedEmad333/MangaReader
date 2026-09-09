package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM coverage for [ChapterRecognition] — no Android, so it runs under
 * `gradle testDebugUnitTest` with nothing but JUnit on the classpath.
 *
 * The point is that these regexes decide how a whole library sorts and what the
 * NUMBER display mode shows, and they have no other guard: a well-meaning tweak
 * to one pattern can quietly turn "Vol.4 Ch.12" back into chapter 4 across every
 * source at once. Each case below is one behaviour the parser is relied on for.
 */
class ChapterRecognitionTest {

    // Float results are built by addition (5f + 0.2f), so compare with a delta
    // rather than trusting exact IEEE equality on the fractional cases.
    private val delta = 0.0001f

    @Test
    fun `honours the source's own number when it is set`() {
        // A source that filled chapter_number in is trusted over any regex.
        assertEquals(12f, ChapterRecognition.parse("", "Anything at all", fromSource = 12f), delta)
    }

    @Test
    fun `reads the plain trailing number`() {
        assertEquals(5f, ChapterRecognition.parse("", "Chapter 5"), delta)
        assertEquals(567f, ChapterRecognition.parse("Bleach", "Bleach 567: Down With Snowwhite"), delta)
    }

    @Test
    fun `prefers the Ch marker over a volume number`() {
        // Two numbers present: the volume tag is stripped and the chapter wins.
        assertEquals(4f, ChapterRecognition.parse("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation"), delta)
        assertEquals(12f, ChapterRecognition.parse("", "Vol.4 Ch.12"), delta)
    }

    @Test
    fun `strips the series title so a number in it is not mistaken for the chapter`() {
        assertEquals(12f, ChapterRecognition.parse("7 Seeds", "7 Seeds 12"), delta)
    }

    @Test
    fun `treats a hyphen or comma as a decimal point`() {
        assertEquals(5.2f, ChapterRecognition.parse("", "Ch 5-2"), delta)
        assertEquals(5.2f, ChapterRecognition.parse("", "Ch 5,2"), delta)
    }

    @Test
    fun `sorts an extra just after its chapter`() {
        // "extra" -> .99, so it lands right after the whole chapter it follows.
        assertEquals(5.99f, ChapterRecognition.parse("", "Chapter 5 extra"), delta)
    }

    @Test
    fun `returns NO_NUMBER when there is no number to find`() {
        assertEquals(Chapter.NO_NUMBER, ChapterRecognition.parse("", "Oneshot"), 0f)
        assertEquals(Chapter.NO_NUMBER, ChapterRecognition.parse("", "Prologue"), 0f)
    }
}
