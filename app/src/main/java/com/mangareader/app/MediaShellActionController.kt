package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.net.Uri

internal class MediaShellActionController(
    private val context: Context,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val mediaState: MediaNavigationState
) {
    fun dismissVideoScan() {
        mediaState.scan = null
    }

    fun openEmbed(url: String) {
        val page = seriesState.active?.let { series ->
            browseState.source?.seriesUrl(series)
        }
        mediaState.scan = null
        mediaState.embed = url to page.orEmpty()
    }

    fun openExternalVideo(url: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(view)
        }.onFailure {
            appState.error =
                "No app on this device can play that link"
        }
    }
}
