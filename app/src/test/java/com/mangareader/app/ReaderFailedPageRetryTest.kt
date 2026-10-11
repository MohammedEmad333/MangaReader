package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFailedPageRetryTest {
    @Test fun successfulPagesAreExcluded() {
        assertEquals(listOf(2, 3), readerFailedPageRetryOrder(
            listOf(true, true, false, false), 2, emptyList(), 1
        ))
    }

    @Test fun visibleFailedPageWinsOverOffscreenPages() {
        assertEquals(listOf(1, 5, 3), readerFailedPageRetryOrder(
            listOf(true, false, true, false, true, false), 1, listOf(5), 1
        ))
    }

    @Test fun backwardReadingPrioritizesNearbyFailures() {
        assertEquals(listOf(4, 3, 1), readerFailedPageRetryOrder(
            listOf(true, false, true, false, false, true), 4, emptyList(), -1
        ))
    }

    @Test fun fullyLoadedOrEmptyChaptersNeedNoRetry() {
        assertEquals(emptyList<Int>(), readerFailedPageRetryOrder(
            emptyList(), 0, emptyList(), 1
        ))
        assertEquals(emptyList<Int>(), readerFailedPageRetryOrder(
            listOf(true, true), 1, emptyList(), 1
        ))
    }
}
