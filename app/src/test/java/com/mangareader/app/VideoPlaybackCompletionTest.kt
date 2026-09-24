package com.mangareader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackCompletionTest {
    @Test
    fun longVideoCompletesWithinLastTenSeconds() {
        assertTrue(shouldMarkPlaybackCompleted(1_795_000L, 1_800_000L))
        assertFalse(shouldMarkPlaybackCompleted(1_780_000L, 1_800_000L))
    }

    @Test
    fun shortVideoRequiresNinetyPercentWatched() {
        assertFalse(shouldMarkPlaybackCompleted(1_000L, 5_000L))
        assertTrue(shouldMarkPlaybackCompleted(4_500L, 5_000L))
    }

    @Test
    fun invalidDurationsNeverComplete() {
        assertFalse(shouldMarkPlaybackCompleted(10_000L, 0L))
        assertFalse(shouldMarkPlaybackCompleted(0L, 10_000L))
    }
}
