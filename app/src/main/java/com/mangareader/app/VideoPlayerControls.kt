package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout

internal object VideoPlayerPrefs {
    private const val PREFS = "video_player"
    private const val SPEED = "speed"
    private const val RESIZE_MODE = "resize_mode"
    private const val LOOP = "loop"
    private const val SEEK_SECONDS = "seek_seconds"
    private const val CONTROLS_TIMEOUT_SECONDS = "controls_timeout_seconds"
    private const val SUBTITLE_LANGUAGE = "subtitle_language"
    private const val AUDIO_LANGUAGE = "audio_language"
    private const val PREFERRED_STREAM_TITLE = "preferred_stream_title"
    private const val SUBTITLE_SIZE = "subtitle_size"
    private const val SUBTITLE_BACKGROUND = "subtitle_background"
    private const val SUBTITLE_POSITION = "subtitle_position"

    fun speed(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(SPEED, 1f)
            .takeIf { it in VIDEO_SPEEDS }
            ?: 1f

    fun setSpeed(context: Context, speed: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(SPEED, speed)
            .apply()
    }

    fun resizeMode(context: Context): VideoResizeMode =
        runCatching {
            VideoResizeMode.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(RESIZE_MODE, null)
                    .orEmpty(),
            )
        }.getOrDefault(VideoResizeMode.FIT)

    fun setResizeMode(context: Context, mode: VideoResizeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RESIZE_MODE, mode.name)
            .apply()
    }

    fun loop(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(LOOP, false)

    fun setLoop(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(LOOP, enabled)
            .apply()
    }

    fun seekSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(SEEK_SECONDS, 10)
            .takeIf { it in VIDEO_SEEK_SECONDS }
            ?: 10

    fun setSeekSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(SEEK_SECONDS, seconds.coerceIn(5, 30))
            .apply()
    }

    fun controlsTimeoutSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(CONTROLS_TIMEOUT_SECONDS, 4)
            .takeIf { it in VIDEO_CONTROL_TIMEOUT_SECONDS }
            ?: 4

    fun setControlsTimeoutSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(CONTROLS_TIMEOUT_SECONDS, seconds)
            .apply()
    }

    fun subtitleLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SUBTITLE_LANGUAGE, VIDEO_LANGUAGE_AUTO)
            ?: VIDEO_LANGUAGE_AUTO

    fun setSubtitleLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SUBTITLE_LANGUAGE, language)
            .apply()
    }

    fun audioLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(AUDIO_LANGUAGE, VIDEO_LANGUAGE_AUTO)
            ?: VIDEO_LANGUAGE_AUTO

    fun setAudioLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(AUDIO_LANGUAGE, language)
            .apply()
    }

    fun preferredStreamTitle(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFERRED_STREAM_TITLE, "")
            .orEmpty()

    fun setPreferredStreamTitle(context: Context, title: String) {
        val normalized = normalizeStreamPreference(title)
        if (normalized.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFERRED_STREAM_TITLE, normalized)
            .apply()
    }

    fun subtitleSize(context: Context): VideoSubtitleSize =
        runCatching {
            VideoSubtitleSize.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(SUBTITLE_SIZE, null)
                    .orEmpty(),
            )
        }.getOrDefault(VideoSubtitleSize.MEDIUM)

    fun setSubtitleSize(context: Context, size: VideoSubtitleSize) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SUBTITLE_SIZE, size.name)
            .apply()
    }

    fun subtitleBackground(context: Context): VideoSubtitleBackground =
        runCatching {
            VideoSubtitleBackground.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(SUBTITLE_BACKGROUND, null)
                    .orEmpty(),
            )
        }.getOrDefault(VideoSubtitleBackground.SEMI)

    fun setSubtitleBackground(context: Context, background: VideoSubtitleBackground) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SUBTITLE_BACKGROUND, background.name)
            .apply()
    }

    fun subtitlePosition(context: Context): VideoSubtitlePosition =
        runCatching {
            VideoSubtitlePosition.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(SUBTITLE_POSITION, null)
                    .orEmpty(),
            )
        }.getOrDefault(VideoSubtitlePosition.BOTTOM)

    fun setSubtitlePosition(context: Context, position: VideoSubtitlePosition) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SUBTITLE_POSITION, position.name)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

internal enum class VideoSubtitlePosition(val label: String, val bottomPaddingFraction: Float) {
    BOTTOM("Sub pos: Bottom", 0.08f),
    MIDDLE("Sub pos: Middle", 0.45f),
    TOP("Sub pos: Top", 0.82f),
}

internal enum class VideoSubtitleBackground(val label: String, val backgroundColor: Int) {
    OFF("Sub bg: Off", android.graphics.Color.TRANSPARENT),
    SEMI("Sub bg: Semi", 0x99000000.toInt()),
    SOLID("Sub bg: Solid", android.graphics.Color.BLACK),
}

internal enum class VideoSubtitleSize(val label: String, val sp: Float) {
    SMALL("Sub size: Small", 16f),
    MEDIUM("Sub size: Medium", 20f),
    LARGE("Sub size: Large", 26f),
}

internal enum class VideoResizeMode(val label: String, val playerViewMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

internal val VIDEO_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
internal val VIDEO_SEEK_SECONDS = listOf(5, 10, 15, 30)
internal val VIDEO_CONTROL_TIMEOUT_SECONDS = listOf(2, 4, 6, 10, 0)
internal const val VIDEO_LANGUAGE_AUTO = "__auto__"
internal const val VIDEO_LANGUAGE_OFF = "__off__"

@Composable
internal fun VideoPlayerQuickControls(
    player: Player,
    subtitles: List<VideoSubtitle>,
    speed: Float,
    streams: List<PlayableVideo>,
    selectedStream: PlayableVideo,
    resizeMode: VideoResizeMode,
    loopEnabled: Boolean,
    sleepTimerMinutes: Int?,
    sleepTimerRemainingMs: Long?,
    landscapeLocked: Boolean,
    seekSeconds: Int,
    controlsTimeoutSeconds: Int,
    subtitleLanguage: String,
    subtitleSize: VideoSubtitleSize,
    subtitleBackground: VideoSubtitleBackground,
    subtitlePosition: VideoSubtitlePosition,
    audioLanguages: List<String>,
    audioLanguage: String,
    muted: Boolean,
    playing: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onStreamChange: (PlayableVideo) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onResizeModeChange: (VideoResizeMode) -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onPictureInPicture: () -> Unit,
    onLandscapeLockChange: (Boolean) -> Unit,
    onSeekSecondsChange: (Int) -> Unit,
    onControlsTimeoutChange: (Int) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onSubtitleSizeChange: (VideoSubtitleSize) -> Unit,
    onSubtitleBackgroundChange: (VideoSubtitleBackground) -> Unit,
    onSubtitlePositionChange: (VideoSubtitlePosition) -> Unit,
    onAudioLanguageChange: (String) -> Unit,
    onMuteToggle: () -> Unit,
    onResetSettings: () -> Unit,
    onPlayPause: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var streamMenu by remember { mutableStateOf(false) }
    var resizeMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var seekMenu by remember { mutableStateOf(false) }
    var timeoutMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var subtitleSizeMenu by remember { mutableStateOf(false) }
    var subtitleBackgroundMenu by remember { mutableStateOf(false) }
    var subtitlePositionMenu by remember { mutableStateOf(false) }
    var seekDragFraction by remember { mutableStateOf<Float?>(null) }
    val streamOptions = remember(streams) {
        streams.distinctBy { it.url }
    }

    val sliderFraction = seekDragFraction ?: seekFraction(currentPositionMs, durationMs)
    val previewPositionMs =
        seekDragFraction?.let { seekPositionForFraction(it, durationMs) } ?: currentPositionMs

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderFraction,
                onValueChange = { seekDragFraction = it },
                onValueChangeFinished = {
                    val fraction = seekDragFraction
                    if (fraction != null && durationMs > 0L) {
                        player.seekTo(seekPositionForFraction(fraction, durationMs))
                    }
                    seekDragFraction = null
                },
                enabled = durationMs > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            TextButton(onClick = onPlayPause) {
                Text(playPauseLabel(playing))
            }

            Text(
                text = playbackTimeLabel(previewPositionMs, durationMs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            TextButton(
                onClick = {
                    player.seekTo(seekBackTarget(player.currentPosition, seekSeconds))
                },
            ) {
                Text("−" + seekSeconds + "s")
            }

            TextButton(
                onClick = {
                    player.seekTo(seekForwardTarget(player.currentPosition, player.duration, seekSeconds))
                },
            ) {
                Text("+" + seekSeconds + "s")
            }

            TextButton(onClick = { seekMenu = true }) {
                Text("Seek " + seekSeconds + "s")
            }
            DropdownMenu(
                expanded = seekMenu,
                onDismissRequest = { seekMenu = false },
            ) {
                VIDEO_SEEK_SECONDS.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text(seconds.toString() + " seconds") },
                        onClick = {
                            seekMenu = false
                            onSeekSecondsChange(seconds)
                        },
                    )
                }
            }

            TextButton(onClick = { timeoutMenu = true }) {
                Text(controlsTimeoutLabel(controlsTimeoutSeconds))
            }
            DropdownMenu(
                expanded = timeoutMenu,
                onDismissRequest = { timeoutMenu = false },
            ) {
                VIDEO_CONTROL_TIMEOUT_SECONDS.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text(controlsTimeoutMenuLabel(seconds)) },
                        onClick = {
                            timeoutMenu = false
                            onControlsTimeoutChange(seconds)
                        },
                    )
                }
            }

            if (streamOptions.size > 1) {
                TextButton(onClick = { streamMenu = true }) {
                    Text(
                        streamDisplayLabel(
                            selectedStream,
                            streamOptions.indexOfFirst { it.url == selectedStream.url },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = streamMenu,
                    onDismissRequest = { streamMenu = false },
                ) {
                    streamOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(streamDisplayLabel(option, index)) },
                            onClick = {
                                streamMenu = false
                                onStreamChange(option)
                            },
                        )
                    }
                }
            }

            TextButton(onClick = { resizeMenu = true }) {
                Text(resizeMode.label)
            }
            DropdownMenu(
                expanded = resizeMenu,
                onDismissRequest = { resizeMenu = false },
            ) {
                VideoResizeMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            resizeMenu = false
                            onResizeModeChange(option)
                        },
                    )
                }
            }

            TextButton(onClick = { onLoopChange(!loopEnabled) }) {
                Text(if (loopEnabled) "Loop ✓" else "Loop")
            }

            TextButton(onClick = { sleepMenu = true }) {
                Text(sleepTimerCountdownLabel(sleepTimerMinutes, sleepTimerRemainingMs))
            }
            DropdownMenu(
                expanded = sleepMenu,
                onDismissRequest = { sleepMenu = false },
            ) {
                listOf<Int?>(15, 30, 45, 60, null).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(sleepTimerMenuLabel(minutes)) },
                        onClick = {
                            sleepMenu = false
                            onSleepTimerChange(minutes)
                        },
                    )
                }
            }

            TextButton(onClick = onPictureInPicture) {
                Text("PiP")
            }

            TextButton(onClick = { onLandscapeLockChange(!landscapeLocked) }) {
                Text(if (landscapeLocked) "Landscape ✓" else "Landscape")
            }

            TextButton(onClick = { speedMenu = true }) {
                Text(formatPlaybackSpeed(speed))
            }
            DropdownMenu(
                expanded = speedMenu,
                onDismissRequest = { speedMenu = false },
            ) {
                VIDEO_SPEEDS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(formatPlaybackSpeed(option)) },
                        onClick = {
                            speedMenu = false
                            onSpeedChange(option)
                        },
                    )
                }
            }

            if (subtitles.isNotEmpty()) {
                TextButton(onClick = { subtitleMenu = true }) {
                    Text(subtitleButtonLabel(subtitleLanguage))
                }
                DropdownMenu(
                    expanded = subtitleMenu,
                    onDismissRequest = { subtitleMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Subtitles: Auto") },
                        onClick = {
                            subtitleMenu = false
                            player.trackSelectionParameters =
                                player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setPreferredTextLanguage(null)
                                    .build()
                            onSubtitleLanguageChange(VIDEO_LANGUAGE_AUTO)
                        },
                    )
                    subtitles
                        .map { it.language.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                onClick = {
                                    subtitleMenu = false
                                    player.trackSelectionParameters =
                                        player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(language)
                                            .build()
                                    onSubtitleLanguageChange(language)
                                },
                            )
                        }
                    DropdownMenuItem(
                        text = { Text("Subtitles: Off") },
                        onClick = {
                            subtitleMenu = false
                            player.trackSelectionParameters =
                                player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                            onSubtitleLanguageChange(VIDEO_LANGUAGE_OFF)
                        },
                    )
                }
            }

            TextButton(onClick = { subtitleSizeMenu = true }) {
                Text(subtitleSize.label)
            }
            DropdownMenu(
                expanded = subtitleSizeMenu,
                onDismissRequest = { subtitleSizeMenu = false },
            ) {
                VideoSubtitleSize.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            subtitleSizeMenu = false
                            onSubtitleSizeChange(option)
                        },
                    )
                }
            }

            TextButton(onClick = { subtitleBackgroundMenu = true }) {
                Text(subtitleBackground.label)
            }
            DropdownMenu(
                expanded = subtitleBackgroundMenu,
                onDismissRequest = { subtitleBackgroundMenu = false },
            ) {
                VideoSubtitleBackground.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            subtitleBackgroundMenu = false
                            onSubtitleBackgroundChange(option)
                        },
                    )
                }
            }

            TextButton(onClick = { subtitlePositionMenu = true }) {
                Text(subtitlePosition.label)
            }
            DropdownMenu(
                expanded = subtitlePositionMenu,
                onDismissRequest = { subtitlePositionMenu = false },
            ) {
                VideoSubtitlePosition.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            subtitlePositionMenu = false
                            onSubtitlePositionChange(option)
                        },
                    )
                }
            }

            if (audioLanguages.isNotEmpty()) {
                TextButton(onClick = { audioMenu = true }) {
                    Text(audioButtonLabel(audioLanguage))
                }
                DropdownMenu(
                    expanded = audioMenu,
                    onDismissRequest = { audioMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Audio: Auto") },
                        onClick = {
                            audioMenu = false
                            onAudioLanguageChange(VIDEO_LANGUAGE_AUTO)
                        },
                    )
                    audioLanguages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language) },
                            onClick = {
                                audioMenu = false
                                onAudioLanguageChange(language)
                            },
                        )
                    }
                }
            }

            TextButton(onClick = onMuteToggle) {
                Text(if (muted) "Unmute" else "Mute")
            }

            TextButton(onClick = onResetSettings) {
                Text("Reset")
            }

            TextButton(onClick = onLock) {
                Text("Lock")
            }
            }
        }
    }
}

internal fun seekFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f
    else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

internal fun seekPositionForFraction(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0L) 0L
    else (fraction.coerceIn(0f, 1f) * durationMs.toFloat()).toLong()

@Composable
internal fun VideoPlayerGestureLayer(
    locked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onVerticalStart: (Boolean) -> Unit,
    onVerticalProgress: (Float) -> Unit,
    onVerticalEnd: () -> Unit,
    onHorizontalStart: () -> Unit,
    onHorizontalProgress: (Float) -> Unit,
    onHorizontalEnd: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(locked) {
                detectTapGestures(
                    onPress = {
                        if (!locked) {
                            coroutineScope {
                                var boosted = false
                                val holdJob = launch {
                                    delay(450L)
                                    boosted = true
                                    onHoldStart()
                                }
                                val released = tryAwaitRelease()
                                holdJob.cancel()
                                if (boosted) {
                                    onHoldEnd()
                                }
                            }
                        }
                    },
                    onTap = {
                        if (!locked) onSingleTap()
                    },
                    onDoubleTap = { offset ->
                        if (!locked) {
                            when (doubleTapZone(offset.x, size.width.toFloat())) {
                                VideoDoubleTapZone.LEFT -> onDoubleTapLeft()
                                VideoDoubleTapZone.CENTER -> onDoubleTapCenter()
                                VideoDoubleTapZone.RIGHT -> onDoubleTapRight()
                            }
                        }
                    },
                )
            }
            .pointerInput(locked) {
                var dragFromLeft = true
                var accumulatedX = 0f
                var accumulatedY = 0f
                var horizontal: Boolean? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        if (!locked) {
                            dragFromLeft = offset.x < size.width / 2f
                            accumulatedX = 0f
                            accumulatedY = 0f
                            horizontal = null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!locked) {
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y
                            if (horizontal == null) {
                                val absX = kotlin.math.abs(accumulatedX)
                                val absY = kotlin.math.abs(accumulatedY)
                                if (absX > 12f || absY > 12f) {
                                    horizontal = absX > absY
                                    if (horizontal == true) {
                                        onHorizontalStart()
                                    } else {
                                        onVerticalStart(dragFromLeft)
                                    }
                                }
                            }
                            when (horizontal) {
                                true -> {
                                    change.consume()
                                    onHorizontalProgress(
                                        (accumulatedX / size.width).coerceIn(-1f, 1f),
                                    )
                                }
                                false -> {
                                    change.consume()
                                    onVerticalProgress(
                                        (-accumulatedY / size.height).coerceIn(-1f, 1f),
                                    )
                                }
                                null -> Unit
                            }
                        }
                    },
                    onDragEnd = {
                        if (!locked) {
                            if (horizontal == true) onHorizontalEnd()
                            else if (horizontal == false) onVerticalEnd()
                        }
                    },
                    onDragCancel = {
                        if (!locked) {
                            if (horizontal == true) onHorizontalEnd()
                            else if (horizontal == false) onVerticalEnd()
                        }
                    },
                )
            },
    )
}

internal fun seekBackTarget(positionMs: Long, seconds: Int = 10): Long =
    (positionMs - seconds.coerceAtLeast(1) * 1_000L).coerceAtLeast(0L)

internal fun seekForwardTarget(
    positionMs: Long,
    durationMs: Long,
    seconds: Int = 10,
): Long {
    val target = positionMs + seconds.coerceAtLeast(1) * 1_000L
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

internal fun formatPlaybackSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() + "×" else speed.toString() + "×"

internal fun streamDisplayLabel(video: PlayableVideo, index: Int): String {
    val title = video.title.trim()
    if (title.isNotBlank()) return title
    return if (index >= 0) "Stream " + (index + 1) else "Quality"
}


internal fun sleepTimerLabel(minutes: Int?): String =
    minutes?.let { "Sleep " + it + "m" } ?: "Sleep"

internal fun sleepTimerMenuLabel(minutes: Int?): String =
    minutes?.let { "Stop after " + it + " min" } ?: "Sleep timer off"

internal fun sleepTimerDurationMs(minutes: Int): Long =
    minutes.coerceAtLeast(1).toLong() * 60_000L


internal fun controlsTimeoutLabel(seconds: Int): String =
    if (seconds == 0) "Controls always" else "Hide " + seconds + "s"

internal fun controlsTimeoutMenuLabel(seconds: Int): String =
    if (seconds == 0) "Never hide controls" else "Hide after " + seconds + " seconds"


internal fun subtitleButtonLabel(language: String): String =
    when (language) {
        VIDEO_LANGUAGE_OFF -> "CC off"
        VIDEO_LANGUAGE_AUTO -> "CC"
        else -> "CC " + language
    }

internal fun audioButtonLabel(language: String): String =
    if (language == VIDEO_LANGUAGE_AUTO) "Audio" else "Audio " + language


internal fun normalizeStreamPreference(title: String): String =
    title.trim().lowercase()

internal fun preferredStream(
    initial: PlayableVideo,
    streams: List<PlayableVideo>,
    preferredTitle: String,
): PlayableVideo {
    val normalized = normalizeStreamPreference(preferredTitle)
    if (normalized.isBlank()) return initial
    return streams.firstOrNull {
        normalizeStreamPreference(it.title) == normalized
    } ?: initial
}


internal fun scrubTargetPosition(
    startPositionMs: Long,
    durationMs: Long,
    progress: Float,
): Long {
    val spanMs = if (durationMs > 0L) {
        (durationMs / 4L).coerceAtMost(300_000L).coerceAtLeast(30_000L)
    } else {
        120_000L
    }
    val target = startPositionMs + (spanMs * progress.coerceIn(-1f, 1f)).toLong()
    return if (durationMs > 0L) {
        target.coerceIn(0L, durationMs)
    } else {
        target.coerceAtLeast(0L)
    }
}

internal fun formatVideoTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        hours.toString() + ":" +
            minutes.toString().padStart(2, '0') + ":" +
            seconds.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    }
}


internal fun muteButtonLabel(muted: Boolean): String =
    if (muted) "Unmute" else "Mute"


internal enum class VideoGestureEdge {
    START,
    END,
}

internal fun scrubEdge(targetMs: Long, durationMs: Long): VideoGestureEdge? =
    when {
        targetMs <= 0L -> VideoGestureEdge.START
        durationMs > 0L && targetMs >= durationMs -> VideoGestureEdge.END
        else -> null
    }

internal fun levelEdge(value: Float): VideoGestureEdge? =
    when {
        value <= 0f -> VideoGestureEdge.START
        value >= 1f -> VideoGestureEdge.END
        else -> null
    }


internal fun playPauseLabel(playing: Boolean): String =
    if (playing) "Pause" else "Play"


internal fun sleepTimerCountdownLabel(minutes: Int?, remainingMs: Long?): String {
    if (minutes == null || remainingMs == null) return "Sleep"
    val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1_000L
    val mins = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "Sleep " + mins + ":" + seconds.toString().padStart(2, '0')
}


internal fun playbackTimeLabel(positionMs: Long, durationMs: Long): String {
    val position = formatVideoTime(positionMs)
    val duration = if (durationMs > 0L) formatVideoTime(durationMs) else "--:--"
    return position + " / " + duration
}


internal enum class VideoDoubleTapZone {
    LEFT,
    CENTER,
    RIGHT,
}

internal fun doubleTapZone(x: Float, width: Float): VideoDoubleTapZone {
    if (width <= 0f) return VideoDoubleTapZone.CENTER
    val fraction = (x / width).coerceIn(0f, 1f)
    return when {
        fraction < 1f / 3f -> VideoDoubleTapZone.LEFT
        fraction > 2f / 3f -> VideoDoubleTapZone.RIGHT
        else -> VideoDoubleTapZone.CENTER
    }
}
