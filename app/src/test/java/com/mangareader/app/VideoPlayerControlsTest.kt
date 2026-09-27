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

    @Test
    fun preferredStream_matchesNormalizedTitleOrFallsBack() {
        val initial = PlayableVideo(url = "https://example/720", title = "720p")
        val streams = listOf(
            initial,
            PlayableVideo(url = "https://example/1080", title = " 1080P "),
        )

        assertEquals(
            "https://example/1080",
            preferredStream(initial, streams, "1080p").url,
        )
        assertEquals(
            initial.url,
            preferredStream(initial, streams, "4k").url,
        )
        assertEquals("1080p", normalizeStreamPreference(" 1080P "))
    }

    @Test
    fun horizontalScrub_clampsAndFormatsTargets() {
        assertEquals(
            90_000L,
            scrubTargetPosition(60_000L, 100_000L, 1f),
        )
        assertEquals(
            30_000L,
            scrubTargetPosition(60_000L, 100_000L, -1f),
        )
        assertEquals(
            0L,
            scrubTargetPosition(10_000L, 100_000L, -1f),
        )
        assertEquals("1:05", formatVideoTime(65_000L))
        assertEquals("1:01:01", formatVideoTime(3_661_000L))
    }

    @Test
    fun muteLabels_areClear() {
        assertEquals("Mute", muteButtonLabel(false))
        assertEquals("Unmute", muteButtonLabel(true))
    }

    @Test
    fun gestureEdges_detectOnlyRealBoundaries() {
        assertEquals(VideoGestureEdge.START, scrubEdge(0L, 100_000L))
        assertEquals(VideoGestureEdge.END, scrubEdge(100_000L, 100_000L))
        assertEquals(null, scrubEdge(50_000L, 100_000L))
        assertEquals(VideoGestureEdge.START, levelEdge(0f))
        assertEquals(VideoGestureEdge.END, levelEdge(1f))
        assertEquals(null, levelEdge(0.5f))
    }

    @Test
    fun playPauseLabels_followPlaybackState() {
        assertEquals("Pause", playPauseLabel(true))
        assertEquals("Play", playPauseLabel(false))
    }

    @Test
    fun sleepCountdown_formatsRemainingTime() {
        assertEquals("Sleep", sleepTimerCountdownLabel(null, null))
        assertEquals("Sleep", sleepTimerCountdownLabel(15, null))
        assertEquals("Sleep 15:00", sleepTimerCountdownLabel(15, 900_000L))
        assertEquals("Sleep 0:01", sleepTimerCountdownLabel(15, 1L))
        assertEquals("Sleep 0:00", sleepTimerCountdownLabel(15, 0L))
    }

    @Test
    fun playbackTimeLabel_formatsKnownAndUnknownDuration() {
        assertEquals("1:05 / 2:10", playbackTimeLabel(65_000L, 130_000L))
        assertEquals("0:05 / --:--", playbackTimeLabel(5_000L, 0L))
    }

    @Test
    fun doubleTapZones_splitScreenIntoThirds() {
        assertEquals(VideoDoubleTapZone.LEFT, doubleTapZone(10f, 300f))
        assertEquals(VideoDoubleTapZone.CENTER, doubleTapZone(150f, 300f))
        assertEquals(VideoDoubleTapZone.RIGHT, doubleTapZone(290f, 300f))
        assertEquals(VideoDoubleTapZone.CENTER, doubleTapZone(0f, 0f))
    }

    @Test
    fun subtitleSizes_haveExpectedLabelsAndScale() {
        assertEquals("Sub size: Small", VideoSubtitleSize.SMALL.label)
        assertEquals(16f, VideoSubtitleSize.SMALL.sp)
        assertEquals("Sub size: Medium", VideoSubtitleSize.MEDIUM.label)
        assertEquals(20f, VideoSubtitleSize.MEDIUM.sp)
        assertEquals("Sub size: Large", VideoSubtitleSize.LARGE.label)
        assertEquals(26f, VideoSubtitleSize.LARGE.sp)
    }

    @Test
    fun subtitleBackgroundOptions_haveExpectedLabelsAndColors() {
        assertEquals("Sub bg: Off", VideoSubtitleBackground.OFF.label)
        assertEquals(android.graphics.Color.TRANSPARENT, VideoSubtitleBackground.OFF.backgroundColor)
        assertEquals("Sub bg: Semi", VideoSubtitleBackground.SEMI.label)
        assertEquals(0x99000000.toInt(), VideoSubtitleBackground.SEMI.backgroundColor)
        assertEquals("Sub bg: Solid", VideoSubtitleBackground.SOLID.label)
        assertEquals(android.graphics.Color.BLACK, VideoSubtitleBackground.SOLID.backgroundColor)
    }

}
