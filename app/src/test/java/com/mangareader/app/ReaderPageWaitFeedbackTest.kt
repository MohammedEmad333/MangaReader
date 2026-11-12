package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageWaitFeedbackTest {
    @Test fun showsBasicLoadingForFirstTwelveSeconds() {
        assertEquals(ReaderPageWaitLevel.LOADING, readerPageWaitLevel(0))
        assertEquals(ReaderPageWaitLevel.LOADING, readerPageWaitLevel(11_999L))
        assertEquals(ReaderPageWaitLevel.LOADING, readerPageWaitLevel(-1L))
    }

    @Test fun slowHintAppearsOnlyAfterThreshold() {
        assertEquals(ReaderPageWaitLevel.SLOW, readerPageWaitLevel(12_000L))
        assertEquals(ReaderPageWaitLevel.SLOW, readerPageWaitLevel(29_999L))
    }

    @Test fun verySlowHintDoesNotForceFailureOrRetry() {
        assertEquals(ReaderPageWaitLevel.VERY_SLOW, readerPageWaitLevel(30_000L))
        assertEquals(ReaderPageWaitLevel.VERY_SLOW, readerPageWaitLevel(90_000L))
    }
}
