package com.mangareader.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import org.json.JSONArray
import org.json.JSONObject
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen in-app video player for direct streams discovered by Yomu.
 *
 * Media3 playback UI and persistence live in [VideoPlayerScreen]; this activity
 * owns intent decoding, immersive mode and picture-in-picture lifecycle.
 */
class VideoPlayerActivity : ComponentActivity() {
    private var inPictureInPicture by mutableStateOf(false)
    private var playbackActive = false
    private var landscapeLocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val referer = intent.getStringExtra(EXTRA_REFERER).orEmpty()
        val initialVideo = intent.getStringExtra(EXTRA_VIDEO_JSON)
            ?.let(::decodeVideo)
            ?: PlayableVideo(
                url = intent.getStringExtra(EXTRA_URL).orEmpty(),
                headers = intent.getStringArrayExtra(EXTRA_HEADERS)
                    ?.toList()
                    ?.chunked(2)
                    ?.mapNotNull { pair ->
                        pair.takeIf { it.size == 2 }?.let { it[0] to it[1] }
                    }
                    ?.toMap()
                    .orEmpty(),
                resumeKey = intent.getStringExtra(EXTRA_RESUME_KEY).orEmpty(),
                subtitles = intent.getStringArrayExtra(EXTRA_SUBTITLES)
                    ?.toList()
                    ?.chunked(2)
                    ?.mapNotNull { pair ->
                        pair.takeIf { it.size == 2 }?.let { VideoSubtitle(it[0], it[1]) }
                    }
                    .orEmpty(),
            )
        val streams = intent.getStringExtra(EXTRA_STREAMS_JSON)
            ?.let(::decodeVideos)
            .orEmpty()
            .ifEmpty { listOf(initialVideo) }

        if (initialVideo.url.isBlank()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    initialVideo = initialVideo,
                    streams = streams,
                    referer = referer,
                    inPictureInPicture = inPictureInPicture,
                    landscapeLocked = landscapeLocked,
                    onPlaybackActiveChanged = ::updatePictureInPictureState,
                    onEnterPictureInPicture = ::enterPictureInPictureNow,
                    onLandscapeLockChange = ::applyLandscapeLock,
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !inPictureInPicture) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            playbackActive &&
            !isInPictureInPictureMode
        ) {
            enterPictureInPictureMode(
                buildPictureInPictureParams(autoEnter = false),
            )
        }
    }

    private fun enterPictureInPictureNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            enterPictureInPictureMode(buildPictureInPictureParams(autoEnter = false))
        }
    }

    private fun applyLandscapeLock(locked: Boolean) {
        landscapeLocked = locked
        requestedOrientation = if (locked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun updatePictureInPictureState(active: Boolean) {
        playbackActive = active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(
                buildPictureInPictureParams(
                    autoEnter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && active,
                ),
            )
        }
    }

    private fun buildPictureInPictureParams(
        autoEnter: Boolean,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    companion object {
        private const val EXTRA_URL = "video_url"
        private const val EXTRA_REFERER = "video_referer"
        private const val EXTRA_HEADERS = "video_headers"
        private const val EXTRA_RESUME_KEY = "video_resume_key"
        private const val EXTRA_SUBTITLES = "video_subtitles"
        private const val EXTRA_VIDEO_JSON = "video_json"
        private const val EXTRA_STREAMS_JSON = "video_streams_json"

        fun intent(
            context: Context,
            video: PlayableVideo,
            streams: List<PlayableVideo>,
            referer: String = "",
        ): Intent =
            Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_REFERER, referer)
                putExtra(EXTRA_VIDEO_JSON, encodeVideo(video).toString())
                val options = (listOf(video) + streams)
                    .distinctBy { it.url }
                    .take(MAX_STREAM_OPTIONS)
                putExtra(EXTRA_STREAMS_JSON, encodeVideos(options).toString())
            }

        fun intent(
            context: Context,
            url: String,
            referer: String = "",
            headers: Map<String, String> = emptyMap(),
            resumeKey: String = "",
            subtitles: List<VideoSubtitle> = emptyList(),
        ): Intent =
            intent(
                context = context,
                video = PlayableVideo(
                    url = url,
                    headers = headers,
                    resumeKey = resumeKey,
                    subtitles = subtitles,
                ),
                streams = emptyList(),
                referer = referer,
            ).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_REFERER, referer)
                putExtra(
                    EXTRA_HEADERS,
                    headers.flatMap { listOf(it.key, it.value) }.toTypedArray(),
                )
                putExtra(EXTRA_RESUME_KEY, resumeKey)
                putExtra(
                    EXTRA_SUBTITLES,
                    subtitles.flatMap { listOf(it.url, it.language) }.toTypedArray(),
                )
            }

        private const val MAX_STREAM_OPTIONS = 24

        private fun encodeVideos(videos: List<PlayableVideo>): JSONArray =
            JSONArray().apply {
                videos.forEach { put(encodeVideo(it)) }
            }

        private fun encodeVideo(video: PlayableVideo): JSONObject =
            JSONObject().apply {
                put("url", video.url)
                put("title", video.title)
                put("resumeKey", video.resumeKey)
                put("episodeTitle", video.episodeTitle)
                put("headers", JSONObject(video.headers))
                put(
                    "subtitles",
                    JSONArray().apply {
                        video.subtitles.forEach { subtitle ->
                            put(
                                JSONObject().apply {
                                    put("url", subtitle.url)
                                    put("language", subtitle.language)
                                },
                            )
                        }
                    },
                )
            }

        private fun decodeVideos(raw: String): List<PlayableVideo> =
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        decodeVideo(array.getJSONObject(index).toString())?.let(::add)
                    }
                }
            }.getOrDefault(emptyList())

        private fun decodeVideo(raw: String): PlayableVideo? =
            runCatching {
                val json = JSONObject(raw)
                val headersJson = json.optJSONObject("headers") ?: JSONObject()
                val headers = buildMap {
                    headersJson.keys().forEach { key ->
                        put(key, headersJson.optString(key))
                    }
                }
                val subtitlesJson = json.optJSONArray("subtitles") ?: JSONArray()
                val subtitles = buildList {
                    for (index in 0 until subtitlesJson.length()) {
                        val item = subtitlesJson.optJSONObject(index) ?: continue
                        val url = item.optString("url")
                        if (url.isNotBlank()) {
                            add(VideoSubtitle(url, item.optString("language")))
                        }
                    }
                }
                PlayableVideo(
                    url = json.optString("url"),
                    title = json.optString("title"),
                    headers = headers,
                    resumeKey = json.optString("resumeKey"),
                    subtitles = subtitles,
                    episodeTitle = json.optString("episodeTitle"),
                ).takeIf { it.url.isNotBlank() }
            }.getOrNull()
    }
}
