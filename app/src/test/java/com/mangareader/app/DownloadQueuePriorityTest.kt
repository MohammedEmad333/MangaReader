package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueuePriorityTest {
    private fun prioritize(items: List<String>, id: String, active: String? = null) =
        prioritizeQueuedItem(items, id, active) { it }

    @Test fun selectedChapterMovesImmediatelyAfterActive() {
        assertEquals(listOf("active", "third", "first", "second"),
            prioritize(listOf("active", "first", "second", "third"), "third", "active"))
    }

    @Test fun idleQueueMovesSelectedChapterToFront() {
        assertEquals(listOf("third", "first", "second"),
            prioritize(listOf("first", "second", "third"), "third"))
    }

    @Test fun noOpForActiveOrMissingSelection() {
        val queue = listOf("active", "next")
        assertEquals(queue, prioritize(queue, "active", "active"))
        assertEquals(queue, prioritize(queue, "missing", "active"))
        assertEquals(queue, prioritize(queue, "", "active"))
    }

    @Test fun stableOrderOfOtherChapters() {
        assertEquals(listOf("active", "fourth", "first", "second", "third"),
            prioritize(listOf("active", "first", "second", "third", "fourth"), "fourth", "active"))
    }
}
