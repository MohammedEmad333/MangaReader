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
    fun verticalGesture_clampsAndFormatsPercent() {
        assertEquals(0f, adjustedGestureFraction(0.2f, -0.5f))
        assertEquals(0.7f, adjustedGestureFraction(0.2f, 0.5f))
        assertEquals(1f, adjustedGestureFraction(0.8f, 0.5f))
        assertEquals("0%", gesturePercent(0f))
        assertEquals("55%", gesturePercent(0.55f))
        assertEquals("100%", gesturePercent(1.5f))
    }

    @Test
    fun resizeModes_exposeStableLabels() {
        assertEquals("Fit", VideoResizeMode.FIT.label)
        assertEquals("Fill", VideoResizeMode.FILL.label)
        assertEquals("Zoom", VideoResizeMode.ZOOM.label)
    }

    @Test
    fun playbackSpeed_labelsAreCompact() {
        assertEquals("1×", formatPlaybackSpeed(1f))
        assertEquals("1.25×", formatPlaybackSpeed(1.25f))
        assertEquals("2×", formatPlaybackSpeed(2f))
    }
    @Test
    fun sleepTimer_labelsAndDuration_areStable() {
        assertEquals("Sleep", sleepTimerLabel(null))
        assertEquals("Sleep 30m", sleepTimerLabel(30))
        assertEquals("Sleep timer off", sleepTimerMenuLabel(null))
        assertEquals("Stop after 45 min", sleepTimerMenuLabel(45))
        assertEquals(900_000L, sleepTimerDurationMs(15))
        assertEquals(60_000L, sleepTimerDurationMs(0))
    }

    @Test
    fun customSeek_usesSelectedInterval() {
        assertEquals(10_000L, seekBackTarget(25_000L, 15))
        assertEquals(30_000L, seekForwardTarget(25_000L, 30_000L, 15))
        assertEquals(55_000L, seekForwardTarget(25_000L, 0L, 30))
    }

    @Test
    fun controlsTimeout_labelsAreReadable() {
        assertEquals("Hide 4s", controlsTimeoutLabel(4))
        assertEquals("Controls always", controlsTimeoutLabel(0))
        assertEquals("Hide after 10 seconds", controlsTimeoutMenuLabel(10))
        assertEquals("Never hide controls", controlsTimeoutMenuLabel(0))
    }

    @Test
    fun languageButtons_showCurrentPreference() {
        assertEquals("CC", subtitleButtonLabel(VIDEO_LANGUAGE_AUTO))
        assertEquals("CC off", subtitleButtonLabel(VIDEO_LANGUAGE_OFF))
        assertEquals("CC ar", subtitleButtonLabel("ar"))
        assertEquals("Audio", audioButtonLabel(VIDEO_LANGUAGE_AUTO))
        assertEquals("Audio ja", audioButtonLabel("ja"))
    }

}
