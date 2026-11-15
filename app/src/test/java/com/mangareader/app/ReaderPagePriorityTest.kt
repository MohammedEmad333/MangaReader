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
    @Test fun viewportPagesComeBeforeOffscreenPages() {
        assertEquals(
            listOf(9, 8, 10, 11, 7, 6, 5, 4, 3, 2, 1, 0),
            prioritizedPageOrder(12, 9, visiblePages = listOf(9, 8, 10))
        )
    }

    @Test fun invalidViewportAndStartedPagesAreSkipped() {
        assertEquals(
            listOf(4, 3, 2, 1, 0),
            prioritizedPageOrder(6, 4, setOf(5), listOf(-1, 5, 99, 4, 5))
        )
    }

    @Test fun viewportPriorityClearedWithFinishedLoad() {
        val key = "viewport-priority-test"
        val ticket = ReaderPagePriority.begin(key, 0)
        ReaderPagePriority.updateVisible(key, listOf(3, 4))
        assertEquals(listOf(3, 4), ReaderPagePriority.visiblePages(key))
        ReaderPagePriority.finish(key, ticket)
        assertEquals(emptyList<Int>(), ReaderPagePriority.visiblePages(key))
    }

    @Test fun backwardNavigationPrefetchesPreviousPagesFirst() {
        assertEquals(listOf(5, 4, 3, 6, 2, 1, 0, 7, 8, 9),
            prioritizedPageOrder(10, 5, direction = -1))
    }

    @Test fun visiblePagesRemainHighestPriorityWhenMovingBackwards() {
        assertEquals(listOf(5, 6, 4, 3, 2, 1, 0, 7, 8, 9),
            prioritizedPageOrder(10, 5, visiblePages = listOf(5, 6), direction = -1))
    }

    @Test fun switchingDirectionUpdatesActivePriority() {
        val key = "direction-switch-test"
        val ticket = ReaderPagePriority.begin(key, 9)
        ReaderPagePriority.update(key, 5)
        assertEquals(-1, ReaderPagePriority.direction(key))
        assertEquals(4, ReaderPagePriority.stride(key))
        ReaderPagePriority.update(key, 7)
        assertEquals(1, ReaderPagePriority.direction(key))
        assertEquals(2, ReaderPagePriority.stride(key))
        ReaderPagePriority.finish(key, ticket)
        assertEquals(1, ReaderPagePriority.direction(key))
        assertEquals(1, ReaderPagePriority.stride(key))
    }

    @Test fun nearbyOppositePageIsNotStarvedByLongForwardChapter() {
        assertEquals(
            listOf(5, 6, 7, 4, 8, 9, 3, 2, 1, 0),
            prioritizedPageOrder(10, 5),
        )
    }

    @Test fun largeSeekPrefersThreeUpcomingPagesButKeepsPreviousNeighbor() {
        assertEquals(
            listOf(20, 21, 22, 23, 19, 24, 25, 26, 27, 28, 29, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
            prioritizedPageOrder(30, 20, direction = 1, stride = 20),
        )
    }

    @Test fun fastBackwardSeekPrioritizesLocalPagesOnBothSides() {
        assertEquals(
            listOf(10, 9, 8, 7, 11, 6, 5, 4, 3, 2, 1, 0, 12, 13, 14),
            prioritizedPageOrder(15, 10, direction = -1, stride = 10),
        )
    }

    @Test fun visiblePagesAndStartedRequestsStayAheadOfSpeculation() {
        assertEquals(
            listOf(6, 9, 7, 11, 12, 13, 5, 4, 3, 2, 1, 0),
            prioritizedPageOrder(14, 8, alreadyStarted = setOf(8, 10), visiblePages = listOf(6, 8, 9, 6, -1), stride = 10),
        )
    }

    @Test fun newSessionResetsStaleSeekStride() {
        val key = "stride-reset-test"
        val first = ReaderPagePriority.begin(key, 0)
        ReaderPagePriority.update(key, 25)
        assertEquals(25, ReaderPagePriority.stride(key))
        val second = ReaderPagePriority.begin(key, 3)
        assertEquals(1, ReaderPagePriority.stride(key))
        ReaderPagePriority.finish(key, first)
        assertEquals(3, second.get())
        ReaderPagePriority.finish(key, second)
    }
}
