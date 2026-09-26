package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerControlsTest {

    @Test
    fun seekBack_clampsAtStart() {
        assertEquals(0L, seekBackTarget(4_000L))
        assertEquals(5_000L, seekBackTarget(15_000L))
    }

    @Test
    fun seekForward_clampsAtKnownDuration() {
        assertEquals(30_000L, seekForwardTarget(25_000L, 30_000L))
        assertEquals(35_000L, seekForwardTarget(25_000L, 0L))
    }

    @Test
    fun streamLabelsPreferProvidedQualityThenFallback() {
        assertEquals(
            "1080p",
            streamDisplayLabel(
                PlayableVideo(url = "https://example/1080", title = "1080p"),
                0,
            ),
        )
        assertEquals(
            "Stream 2",
            streamDisplayLabel(
                PlayableVideo(url = "https://example/alt"),
                1,
            ),
        )
    }

    @Test
    fun playbackSpeed_labelsAreCompact() {
        assertEquals("1×", formatPlaybackSpeed(1f))
        assertEquals("1.25×", formatPlaybackSpeed(1.25f))
        assertEquals("2×", formatPlaybackSpeed(2f))
    }
}
