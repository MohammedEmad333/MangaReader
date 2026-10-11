package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPagePriorityTest {
    @Test fun openedPageLoadsBeforeEarlierPages() {
        assertEquals(listOf(9, 10, 11, 8, 7, 6, 5, 4, 3, 2, 1, 0),
            prioritizedPageOrder(12, 9))
    }

    @Test fun jumpingAheadReordersRemainingPages() {
        assertEquals(listOf(9, 10, 11, 8, 7, 6, 5, 4, 3, 2),
            prioritizedPageOrder(12, 9, setOf(0, 1)))
    }

    @Test fun outOfBoundsAndEmptyCountsAreSafe() {
        assertEquals(emptyList<Int>(), prioritizedPageOrder(0, 10))
        assertEquals(listOf(0, 1, 2), prioritizedPageOrder(3, -1))
        assertEquals(listOf(2, 1, 0), prioritizedPageOrder(3, 99))
    }

    @Test fun completedRequestsAreNotRepeated() {
        assertEquals(listOf(2, 0), prioritizedPageOrder(3, 2, setOf(1)))
    }

    @Test fun olderLoadCannotClearNewerPriority() {
        val key = "unit-test-priority"
        val old = ReaderPagePriority.begin(key, 0)
        val newer = ReaderPagePriority.begin(key, 4)
        ReaderPagePriority.finish(key, old)
        ReaderPagePriority.update(key, 7)
        assertEquals(7, newer.get())
        ReaderPagePriority.finish(key, newer)
    }
}
