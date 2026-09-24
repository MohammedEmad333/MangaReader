package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Full-screen in-app video player for direct streams discovered by Yomu.
 *
 * Media3 handles ordinary progressive files plus adaptive HLS streams.
 * Referer is forwarded because many source/CDN links reject a request detached
 * from the page that produced it.
 */
class VideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val referer = intent.getStringExtra(EXTRA_REFERER).orEmpty()
        val resumeKey = intent.getStringExtra(EXTRA_RESUME_KEY).orEmpty()
        val subtitles = intent.getStringArrayExtra(EXTRA_SUBTITLES)
            ?.toList()
            ?.chunked(2)
            ?.mapNotNull { pair ->
                pair.takeIf { it.size == 2 }?.let { VideoSubtitle(it[0], it[1]) }
            }
            .orEmpty()
        val headers = intent.getStringArrayExtra(EXTRA_HEADERS)
            ?.toList()
            ?.chunked(2)
            ?.mapNotNull { pair -> pair.takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            ?.toMap()
            .orEmpty()

        if (url.isBlank()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    url = url,
                    referer = referer,
                    headers = headers,
                    resumeKey = resumeKey,
                    subtitles = subtitles,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "video_url"
        private const val EXTRA_REFERER = "video_referer"
        private const val EXTRA_HEADERS = "video_headers"
        private const val EXTRA_RESUME_KEY = "video_resume_key"
        private const val EXTRA_SUBTITLES = "video_subtitles"

        fun intent(
            context: Context,
            url: String,
            referer: String = "",
            headers: Map<String, String> = emptyMap(),
            resumeKey: String = "",
            subtitles: List<VideoSubtitle> = emptyList(),
        ): Intent =
            Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_REFERER, referer)
                putExtra(EXTRA_HEADERS, headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
                putExtra(EXTRA_RESUME_KEY, resumeKey)
                putExtra(
                    EXTRA_SUBTITLES,
                    subtitles.flatMap { listOf(it.url, it.language) }.toTypedArray(),
                )
            }
    }
}

@Composable
private fun VideoPlayerScreen(
    url: String,
    referer: String,
    headers: Map<String, String>,
    resumeKey: String,
    subtitles: List<VideoSubtitle>,
) {
    var buffering by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val progressKey = remember(url, resumeKey) { resumeKey.ifBlank { "url:$url" } }
    val resumePosition = remember(progressKey, context) {
        VideoPlaybackProgress.position(context, progressKey)
    }

    val player = remember(url, referer, headers, progressKey, resumePosition, subtitles, context) {
        val requestHeaders = headers.toMutableMap().apply {
            if (referer.isNotBlank() && "Referer" !in this) put("Referer", referer)
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                val subtitleConfigurations = subtitles.mapNotNull { subtitle ->
                    subtitleMimeType(subtitle.url)?.let { mime ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(mime)
                            .apply {
                                if (subtitle.language.isNotBlank()) setLanguage(subtitle.language)
                            }
                            .build()
                    }
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setSubtitleConfigurations(subtitleConfigurations)
                    .build()
                setMediaItem(mediaItem)
                if (resumePosition > 0L) seekTo(resumePosition)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING ||
                    playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_ENDED) {
                    VideoPlaybackProgress.clear(context, progressKey)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            val duration = player.duration
            val position = player.currentPosition
            if (duration > 0L && position > 5_000L && position < duration - 10_000L) {
                VideoPlaybackProgress.save(context, progressKey, position, duration)
            } else if (duration > 0L && position >= duration - 10_000L) {
                VideoPlaybackProgress.clear(context, progressKey)
            }
            player.release()
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
                    useController = true
                    this.player = player
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
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
