package com.mangareader.app

import android.content.Context

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

    fun openExternalVideo(video: PlayableVideo) {
        runCatching {
            val source = browseState.source
            val series = seriesState.active
            if (
                source?.isAnime == true &&
                series != null &&
                video.resumeKey.isNotBlank() &&
                !isIncognito(context)
            ) {
                val cover = when (val value = series.cover) {
                    is String -> value
                    is java.io.File -> value.absolutePath
                    else -> ""
                }
                History.touch(
                    context,
                    HistoryEntry(
                        chapterKey = video.resumeKey,
                        title = series.title,
                        sourceId = source.id,
                        seriesId = series.id,
                        coverPath = cover,
                        page = 0,
                        total = 0,
                        updatedAt = System.currentTimeMillis(),
                        mediaType = "anime",
                        detail = video.episodeTitle,
                    ),
                )
                appState.history = History.forDisplay(context)
            }

            context.startActivity(
                VideoPlayerActivity.intent(
                    context = context,
                    url = video.url,
                    headers = video.headers,
                    resumeKey = video.resumeKey,
                    subtitles = video.subtitles,
                ),
            )
        }.onFailure {
            appState.error = "Unable to open the built-in video player"
        }
    }
}
