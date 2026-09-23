package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SeriesActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val readerSession: ReaderSessionState,
    private val mediaState: MediaNavigationState
) {
    fun enrich(source: Source, series: Series) {
        scope.launch {
            val enriched = withContext(Dispatchers.IO) {
                loadAndHealSeriesDetails(
                    context,
                    source,
                    series
                )
            }
            if (
                enriched != null &&
                seriesState.active?.id == series.id
            ) {
                seriesState.active = enriched
            }
        }
    }

    fun open(series: Series) {
        val source = browseState.source ?: return
        seriesState.begin(series, SeriesOrigin.BROWSE)
        seriesState.tagReturn = null
        appState.error = null
        enrich(source, series)

        scope.launch {
            appState.loading = true
            appState.error = seriesState.reloadChapters(
                context,
                source,
                series
            )
            appState.loading = false
        }
    }

    fun findVideos(chapter: Chapter) {
        val source = browseState.source ?: return
        mediaState.scan = null
        mediaState.scanning = true

        scope.launch {
            mediaState.scan = withContext(Dispatchers.IO) {
                scanChapterVideos(source, chapter)
            }
            mediaState.scanning = false
        }
    }

    fun refreshChapters() {
        val source = browseState.source ?: return
        val series = seriesState.active ?: return

        appState.error = null
        Downloads.invalidateCompletion()
        appState.downloadTick++
        enrich(source, series)

        scope.launch {
            appState.loading = true
            appState.error = seriesState.reloadChapters(
                context,
                source,
                series
            )
            appState.loading = false
        }
    }

    fun openChapter(index: Int) {
        val source = browseState.source ?: return
        readerSession.open(
            context = context,
            scope = scope,
            source = source,
            chapters = seriesState.chapters,
            index = index,
            onRootLoadingChanged = { appState.loading = it },
            onClearError = { appState.error = null },
            onError = { appState.error = it }
        )
    }

    fun queueDownloads(source: Source, chapters: List<Chapter>) {
        queueSeriesDownloads(
            context,
            seriesState.active,
            source,
            chapters
        )
    }

    fun downloadChapter(source: Source, chapter: Chapter) {
        queueDownloads(source, listOf(chapter))
    }

    fun downloadAll(source: Source, chapters: List<Chapter>) {
        queueDownloads(source, chapters)
    }

    fun cancelDownloads(seriesId: String) {
        if (cancelSeriesDownloadsAction(context, seriesId)) {
            appState.downloadTick++
        }
    }
}
