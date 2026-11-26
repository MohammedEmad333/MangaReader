package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {

    @Test
    fun since_returnsOnlyNewerVersionsNewestFirst() {
        assertEquals(
            listOf(206, 205, 204),
            Changelog.since(installed = 203, current = 206).map { it.code }
        )
    }

    @Test
    fun since_includesVersion333ReleaseNotes() {
        assertEquals(listOf(333), Changelog.since(installed = 332, current = 333).map { it.code })
        assertTrue(Changelog.notes.single { it.code == 333 }.body.contains("Asura Scans"))
    }

    @Test
    fun since_includesVersion334MangaUpdateLockFix() {
        assertEquals(listOf(334), Changelog.since(installed = 333, current = 334).map { it.code })
        assertTrue(Changelog.notes.single { it.code == 334 }.body.contains("per-series mutex"))
    }

    @Test
    fun since_includesVersion335SeriesProbe() {
        assertEquals(listOf(335), Changelog.since(installed = 334, current = 335).map { it.code })
        assertTrue(Changelog.notes.single { it.code == 335 }.body.contains("series page"))
    }

    @Test
    fun since_returnsNothingWhenAlreadyCurrent() {
        assertTrue(Changelog.since(installed = 206, current = 206).isEmpty())
    }

    @Test
    fun since_respectsCurrentUpperBound() {
        assertEquals(
            listOf(205, 204),
            Changelog.since(installed = 203, current = 205).map { it.code }
        )
    }
}
