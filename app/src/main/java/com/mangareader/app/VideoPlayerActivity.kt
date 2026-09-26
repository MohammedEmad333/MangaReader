package com.mangareader.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

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
            ?.mapNotNull { pair ->
                pair.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }
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
                    inPictureInPicture = inPictureInPicture,
                    onPlaybackActiveChanged = ::updatePictureInPictureState,
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
    }
}
