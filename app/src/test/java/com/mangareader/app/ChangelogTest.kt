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
        assertTrue(Changelog.notes.first().body.contains("Asura Scans"))
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
