package com.mangareader.app

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun VideoPlayerScreen(
    initialVideo: PlayableVideo,
    streams: List<PlayableVideo>,
    referer: String,
    inPictureInPicture: Boolean,
    landscapeLocked: Boolean,
    onPlaybackActiveChanged: (Boolean) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onLandscapeLockChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = LocalView.current.context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val initialPreferredVideo = remember(initialVideo, streams, context) {
        preferredStream(
            initial = initialVideo,
            streams = streams,
            preferredTitle = VideoPlayerPrefs.preferredStreamTitle(context),
        )
    }
    var selectedVideo by remember(initialVideo.url, streams) {
        mutableStateOf(initialPreferredVideo)
    }
    var switchPositionMs by remember { mutableStateOf<Long?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var gestureKind by remember { mutableStateOf(VideoVerticalGesture.NONE) }
    var gestureStartFraction by remember { mutableStateOf(0f) }
    var gestureOverlay by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember(context) {
        mutableStateOf(VideoPlayerPrefs.speed(context))
    }
    var videoResizeMode by remember(context) {
        mutableStateOf(VideoPlayerPrefs.resizeMode(context))
    }
    var loopEnabled by remember(context) {
        mutableStateOf(VideoPlayerPrefs.loop(context))
    }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var sleepTimerDeadline by remember { mutableStateOf<Long?>(null) }
    var seekSeconds by remember(context) {
        mutableStateOf(VideoPlayerPrefs.seekSeconds(context))
    }
    var controlsTimeoutSeconds by remember(context) {
        mutableStateOf(VideoPlayerPrefs.controlsTimeoutSeconds(context))
    }
    var subtitleLanguage by remember(context) {
        mutableStateOf(VideoPlayerPrefs.subtitleLanguage(context))
    }
    var audioLanguage by remember(context) {
        mutableStateOf(VideoPlayerPrefs.audioLanguage(context))
    }
    var audioLanguages by remember { mutableStateOf(emptyList<String>()) }

    val progressKey = remember(selectedVideo.url, selectedVideo.resumeKey) {
        selectedVideo.resumeKey.ifBlank { "url:" + selectedVideo.url }
    }
    val resumePosition = remember(progressKey, context) {
        VideoPlaybackProgress.position(context, progressKey)
    }

    val player = remember(
        selectedVideo.url,
        selectedVideo.headers,
        selectedVideo.subtitles,
        referer,
        progressKey,
        resumePosition,
        switchPositionMs,
        context,
    ) {
        val requestHeaders = selectedVideo.headers.toMutableMap().apply {
            if (referer.isNotBlank() && "Referer" !in this) {
                put("Referer", referer)
            }
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)

                val subtitleConfigurations = selectedVideo.subtitles.mapNotNull { subtitle ->
                    subtitleMimeType(subtitle.url)?.let { mime ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(mime)
                            .apply {
                                if (subtitle.language.isNotBlank()) {
                                    setLanguage(subtitle.language)
                                }
                            }
                            .build()
                    }
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(selectedVideo.url)
                    .setSubtitleConfigurations(subtitleConfigurations)
                    .build()

                setMediaItem(mediaItem)
                val startPosition = switchPositionMs ?: resumePosition
                if (startPosition > 0L) seekTo(startPosition)
                setPlaybackSpeed(playbackSpeed)
                repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                trackSelectionParameters = languageTrackParameters(
                    base = trackSelectionParameters,
                    subtitleLanguage = subtitleLanguage,
                    audioLanguage = audioLanguage,
                )
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING ||
                    playbackState == Player.STATE_IDLE

                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }

                if (playbackState == Player.STATE_ENDED) {
                    VideoPlaybackProgress.markCompleted(
                        context,
                        progressKey,
                        player.duration.coerceAtLeast(0L),
                    )
                    if (selectedVideo.resumeKey.isNotBlank()) {
                        ReadState.setRead(context, selectedVideo.resumeKey, true)
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioLanguages = availableAudioLanguages(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                playbackError = error.message?.takeIf { it.isNotBlank() }
                    ?: "Video playback failed"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackActiveChanged(isPlaying)
                if (!isPlaying && player.playbackState != Player.STATE_ENDED) {
                    persistPlaybackProgress(context, progressKey, selectedVideo.resumeKey, player)
                }
            }
        }

        player.addListener(listener)
        onDispose {
            onPlaybackActiveChanged(false)
            player.removeListener(listener)
            persistPlaybackProgress(context, progressKey, selectedVideo.resumeKey, player)
            player.release()
        }
    }

    LaunchedEffect(gestureOverlay) {
        if (gestureOverlay != null) {
            delay(850L)
            gestureOverlay = null
        }
    }

    LaunchedEffect(
        controlsVisible,
        controlsLocked,
        inPictureInPicture,
        controlsTimeoutSeconds,
    ) {
        if (
            controlsVisible &&
            !controlsLocked &&
            !inPictureInPicture &&
            controlsTimeoutSeconds > 0
        ) {
            delay(controlsTimeoutSeconds * 1_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(sleepTimerDeadline, player) {
        val deadline = sleepTimerDeadline ?: return@LaunchedEffect
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        delay(remaining)
        player.pause()
        sleepTimerDeadline = null
        sleepTimerMinutes = null
        controlsVisible = true
        gestureOverlay = "Sleep timer ended"
    }

    LaunchedEffect(player, progressKey) {
        while (true) {
            delay(10_000L)
            if (player.playbackState != Player.STATE_ENDED) {
                persistPlaybackProgress(context, progressKey, selectedVideo.resumeKey, player)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = !inPictureInPicture && !controlsLocked
                    controllerAutoShow = false
                    this.player = player
                    resizeMode = videoResizeMode.playerViewMode
                    keepScreenOn = true
                    if (controlsVisible && !controlsLocked) showController() else hideController()
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = videoResizeMode.playerViewMode
                view.useController = !inPictureInPicture && !controlsLocked
                view.controllerAutoShow = false
                if (controlsVisible && !controlsLocked && !inPictureInPicture) {
                    view.showController()
                } else {
                    view.hideController()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPictureInPicture && controlsVisible && !controlsLocked) {
            VideoPlayerQuickControls(
                player = player,
                subtitles = selectedVideo.subtitles,
                speed = playbackSpeed,
                streams = streams,
                selectedStream = selectedVideo,
                resizeMode = videoResizeMode,
                loopEnabled = loopEnabled,
                sleepTimerMinutes = sleepTimerMinutes,
                landscapeLocked = landscapeLocked,
                seekSeconds = seekSeconds,
                controlsTimeoutSeconds = controlsTimeoutSeconds,
                subtitleLanguage = subtitleLanguage,
                audioLanguages = audioLanguages,
                audioLanguage = audioLanguage,
                onStreamChange = { next ->
                    if (next.url != selectedVideo.url) {
                        switchPositionMs = player.currentPosition.coerceAtLeast(0L)
                        persistPlaybackProgress(
                            context,
                            progressKey,
                            selectedVideo.resumeKey,
                            player,
                        )
                        selectedVideo = next
                        if (next.title.isNotBlank()) {
                            VideoPlayerPrefs.setPreferredStreamTitle(context, next.title)
                        }
                        buffering = true
                        playbackError = null
                    }
                },
                onSpeedChange = { next ->
                    playbackSpeed = next
                    VideoPlayerPrefs.setSpeed(context, next)
                    player.setPlaybackSpeed(next)
                },
                onResizeModeChange = { next ->
                    videoResizeMode = next
                    VideoPlayerPrefs.setResizeMode(context, next)
                },
                onLoopChange = { enabled ->
                    loopEnabled = enabled
                    VideoPlayerPrefs.setLoop(context, enabled)
                    player.repeatMode =
                        if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    gestureOverlay = if (enabled) "Loop on" else "Loop off"
                },
                onSleepTimerChange = { minutes ->
                    sleepTimerMinutes = minutes
                    sleepTimerDeadline = minutes?.let {
                        SystemClock.elapsedRealtime() + sleepTimerDurationMs(it)
                    }
                    gestureOverlay = minutes?.let { "Sleep timer " + it + "m" } ?: "Sleep timer off"
                },
                onPictureInPicture = onEnterPictureInPicture,
                onLandscapeLockChange = onLandscapeLockChange,
                onSeekSecondsChange = { seconds ->
                    seekSeconds = seconds
                    VideoPlayerPrefs.setSeekSeconds(context, seconds)
                    gestureOverlay = "Seek " + seconds + "s"
                },
                onControlsTimeoutChange = { seconds ->
                    controlsTimeoutSeconds = seconds
                    VideoPlayerPrefs.setControlsTimeoutSeconds(context, seconds)
                    gestureOverlay =
                        if (seconds == 0) "Controls stay visible"
                        else "Controls hide after " + seconds + "s"
                },
                onSubtitleLanguageChange = { language ->
                    subtitleLanguage = language
                    VideoPlayerPrefs.setSubtitleLanguage(context, language)
                },
                onAudioLanguageChange = { language ->
                    audioLanguage = language
                    VideoPlayerPrefs.setAudioLanguage(context, language)
                    player.trackSelectionParameters = languageTrackParameters(
                        base = player.trackSelectionParameters,
                        subtitleLanguage = subtitleLanguage,
                        audioLanguage = language,
                    )
                    gestureOverlay =
                        if (language == VIDEO_LANGUAGE_AUTO) "Audio auto"
                        else "Audio " + language
                },
                onLock = {
                    controlsLocked = true
                    controlsVisible = false
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )
        }

        if (!inPictureInPicture && (!controlsVisible || controlsLocked)) {
            VideoPlayerGestureLayer(
                locked = controlsLocked,
                onSingleTap = { controlsVisible = true },
                onDoubleTapLeft = {
                    player.seekTo(seekBackTarget(player.currentPosition, seekSeconds))
                    gestureOverlay = "−" + seekSeconds + "s"
                },
                onDoubleTapRight = {
                    player.seekTo(seekForwardTarget(player.currentPosition, player.duration, seekSeconds))
                    gestureOverlay = "+" + seekSeconds + "s"
                },
                onVerticalStart = { fromLeft ->
                    gestureKind = if (fromLeft) {
                        VideoVerticalGesture.BRIGHTNESS
                    } else {
                        VideoVerticalGesture.VOLUME
                    }
                    gestureStartFraction = when (gestureKind) {
                        VideoVerticalGesture.BRIGHTNESS ->
                            currentScreenBrightnessFraction(activity, context)
                        VideoVerticalGesture.VOLUME ->
                            currentVolumeFraction(audioManager)
                        VideoVerticalGesture.NONE -> 0f
                    }
                },
                onVerticalProgress = { progress ->
                    val target = adjustedGestureFraction(gestureStartFraction, progress)
                    when (gestureKind) {
                        VideoVerticalGesture.BRIGHTNESS -> {
                            setScreenBrightnessFraction(activity, target)
                            gestureOverlay = "Brightness " + gesturePercent(target)
                        }
                        VideoVerticalGesture.VOLUME -> {
                            setVolumeFraction(audioManager, target)
                            gestureOverlay = "Volume " + gesturePercent(target)
                        }
                        VideoVerticalGesture.NONE -> Unit
                    }
                },
                onVerticalEnd = {
                    gestureKind = VideoVerticalGesture.NONE
                },
                onHoldStart = {
                    player.setPlaybackSpeed(2f)
                    gestureOverlay = "2×"
                },
                onHoldEnd = {
                    playbackSpeed = 1f
                    VideoPlayerPrefs.setSpeed(context, 1f)
                    player.setPlaybackSpeed(1f)
                    gestureOverlay = "1×"
                },
            )
        }

        if (!inPictureInPicture && gestureOverlay != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Text(
                    text = gestureOverlay.orEmpty(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color.White,
                )
            }
        }

        if (!inPictureInPicture && controlsLocked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            ) {
                Button(
                    onClick = {
                        controlsLocked = false
                        controlsVisible = true
                    },
                ) {
                    Text("Unlock")
                }
            }
        }

        if (playbackError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = playbackError.orEmpty(),
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        playbackError = null
                        buffering = true
                        player.prepare()
                        player.playWhenReady = true
                    },
                ) {
                    Text("Retry")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClose) {
                    Text("Close player")
                }
            }
        } else if (buffering) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = 0.58f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text("Buffering…", color = Color.White)
                }
            }
        }
    }
}

private fun persistPlaybackProgress(
    context: Context,
    progressKey: String,
    resumeKey: String,
    player: Player,
) {
    val duration = player.duration
    val position = player.currentPosition

    when {
        shouldMarkPlaybackCompleted(position, duration) -> {
            VideoPlaybackProgress.markCompleted(context, progressKey, duration)
            if (resumeKey.isNotBlank()) {
                ReadState.setRead(context, resumeKey, true)
            }
        }

        duration > 0L && position > 5_000L -> {
            VideoPlaybackProgress.save(context, progressKey, position, duration)
        }
    }
}

private fun subtitleMimeType(url: String): String? {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        path.endsWith(".ttml") || path.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}


internal enum class VideoVerticalGesture {
    NONE,
    BRIGHTNESS,
    VOLUME,
}

internal fun adjustedGestureFraction(start: Float, progress: Float): Float =
    (start + progress).coerceIn(0f, 1f)

internal fun gesturePercent(value: Float): String =
    ((value.coerceIn(0f, 1f) * 100f).roundToInt()).toString() + "%"

private fun currentVolumeFraction(audioManager: AudioManager): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        .coerceAtLeast(1)
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        .toFloat()
        .div(max.toFloat())
        .coerceIn(0f, 1f)
}

private fun setVolumeFraction(audioManager: AudioManager, fraction: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        .coerceAtLeast(1)
    val volume = (fraction.coerceIn(0f, 1f) * max).roundToInt()
    audioManager.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        volume,
        0,
    )
}

private fun currentScreenBrightnessFraction(
    activity: Activity?,
    context: Context,
): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue >= 0f) return windowValue.coerceIn(0f, 1f)

    val system = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128,
    )
    return (system / 255f).coerceIn(0f, 1f)
}

private fun setScreenBrightnessFraction(activity: Activity?, fraction: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply {
        screenBrightness = fraction.coerceIn(0.01f, 1f)
    }
}


internal fun availableAudioLanguages(tracks: Tracks): List<String> =
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { group ->
            (0 until group.length).mapNotNull { index ->
                group.getTrackFormat(index).language?.trim()?.takeIf { it.isNotBlank() }
            }
        }
        .distinct()

internal fun languageTrackParameters(
    base: androidx.media3.common.TrackSelectionParameters,
    subtitleLanguage: String,
    audioLanguage: String,
): androidx.media3.common.TrackSelectionParameters =
    base.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleLanguage == VIDEO_LANGUAGE_OFF)
        .setPreferredTextLanguage(
            subtitleLanguage.takeUnless {
                it == VIDEO_LANGUAGE_AUTO || it == VIDEO_LANGUAGE_OFF
            },
        )
        .setPreferredAudioLanguage(
            audioLanguage.takeUnless { it == VIDEO_LANGUAGE_AUTO },
        )
        .build()
