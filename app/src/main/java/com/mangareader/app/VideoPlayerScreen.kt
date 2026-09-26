package com.mangareader.app

import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlayerScreen(
    initialVideo: PlayableVideo,
    streams: List<PlayableVideo>,
    referer: String,
    inPictureInPicture: Boolean,
    onPlaybackActiveChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var selectedVideo by remember(initialVideo.url) { mutableStateOf(initialVideo) }
    var switchPositionMs by remember { mutableStateOf<Long?>(null) }
    var playbackSpeed by remember(context) {
        mutableStateOf(VideoPlayerPrefs.speed(context))
    }

    val progressKey = remember(selectedVideo.url, selectedVideo.selectedVideo.resumeKey) {
        selectedVideo.selectedVideo.resumeKey.ifBlank { "url:" + selectedVideo.url }
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
                    useController = !inPictureInPicture
                    this.player = player
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = player
                view.useController = !inPictureInPicture
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPictureInPicture) {
            VideoPlayerQuickControls(
                player = player,
                subtitles = selectedVideo.subtitles,
                speed = playbackSpeed,
                streams = streams,
                selectedStream = selectedVideo,
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
                        buffering = true
                        playbackError = null
                    }
                },
                onSpeedChange = { next ->
                    playbackSpeed = next
                    VideoPlayerPrefs.setSpeed(context, next)
                    player.setPlaybackSpeed(next)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )
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
    selectedVideo.resumeKey: String,
    player: Player,
) {
    val duration = player.duration
    val position = player.currentPosition

    when {
        shouldMarkPlaybackCompleted(position, duration) -> {
            VideoPlaybackProgress.markCompleted(context, progressKey, duration)
            if (selectedVideo.resumeKey.isNotBlank()) {
                ReadState.setRead(context, selectedVideo.resumeKey, true)
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
