package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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

internal object VideoPlayerPrefs {
    private const val PREFS = "video_player"
    private const val SPEED = "speed"

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
}

internal val VIDEO_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
internal fun VideoPlayerQuickControls(
    player: Player,
    subtitles: List<VideoSubtitle>,
    speed: Float,
    streams: List<PlayableVideo>,
    selectedStream: PlayableVideo,
    onStreamChange: (PlayableVideo) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var subtitleLabel by remember(player) { mutableStateOf("Auto") }
    var streamMenu by remember { mutableStateOf(false) }
    val streamOptions = remember(streams) {
        streams.distinctBy { it.url }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    player.seekTo(seekBackTarget(player.currentPosition))
                },
            ) {
                Text("−10s")
            }

            TextButton(
                onClick = {
                    player.seekTo(seekForwardTarget(player.currentPosition, player.duration))
                },
            ) {
                Text("+10s")
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
                    Text(if (subtitleLabel == "Off") "CC off" else "CC")
                }
                DropdownMenu(
                    expanded = subtitleMenu,
                    onDismissRequest = { subtitleMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Subtitles: Auto") },
                        onClick = {
                            subtitleMenu = false
                            subtitleLabel = "Auto"
                            player.trackSelectionParameters =
                                player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setPreferredTextLanguage(null)
                                    .build()
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
                                    subtitleLabel = language
                                    player.trackSelectionParameters =
                                        player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(language)
                                            .build()
                                },
                            )
                        }
                    DropdownMenuItem(
                        text = { Text("Subtitles: Off") },
                        onClick = {
                            subtitleMenu = false
                            subtitleLabel = "Off"
                            player.trackSelectionParameters =
                                player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                        },
                    )
                }
            }

            TextButton(onClick = onLock) {
                Text("Lock")
            }
        }
    }
}

@Composable
internal fun VideoPlayerGestureLayer(
    locked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onVerticalStart: (Boolean) -> Unit,
    onVerticalProgress: (Float) -> Unit,
    onVerticalEnd: () -> Unit,
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
                            if (offset.x < size.width / 2f) {
                                onDoubleTapLeft()
                            } else {
                                onDoubleTapRight()
                            }
                        }
                    },
                )
            }
            .pointerInput(locked) {
                var dragFromLeft = true
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (!locked) {
                            dragFromLeft = offset.x < size.width / 2f
                            accumulated = 0f
                            onVerticalStart(dragFromLeft)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!locked) {
                            accumulated += dragAmount
                            change.consume()
                            val progress = (-accumulated / size.height)
                                .coerceIn(-1f, 1f)
                            onVerticalProgress(progress)
                        }
                    },
                    onDragEnd = {
                        if (!locked) onVerticalEnd()
                    },
                    onDragCancel = {
                        if (!locked) onVerticalEnd()
                    },
                )
            },
    )
}

internal fun seekBackTarget(positionMs: Long): Long =
    (positionMs - 10_000L).coerceAtLeast(0L)

internal fun seekForwardTarget(positionMs: Long, durationMs: Long): Long {
    val target = positionMs + 10_000L
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

internal fun formatPlaybackSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() + "×" else speed.toString() + "×"

internal fun streamDisplayLabel(video: PlayableVideo, index: Int): String {
    val title = video.title.trim()
    if (title.isNotBlank()) return title
    return if (index >= 0) "Stream " + (index + 1) else "Quality"
}
