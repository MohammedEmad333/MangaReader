package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SavedSeriesActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val enrichSeries: (Source, Series) -> Unit,
    private val openChapter: (Int) -> Unit
) {
    fun openLibrary(entry: LibraryEntry) {
        appState.error = null
        seriesState.begin(
            Series(
                entry.seriesId,
                entry.title,
                entry.cover.ifBlank { null }
            ),
            SeriesOrigin.LIBRARY
        )

        scope.launch {
            appState.loading = true
            try {
                openLibraryEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = ::adoptSource,
                    onCachedChapters = { cached ->
                        applyCached(entry.seriesId, cached)
                    },
                    onResolved = { source, series, chapters ->
                        applyResolved(
                            entry.seriesId,
                            source,
                            series,
                            chapters
                        )
                    }
                )
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(
                    error,
                    "Could not open this series"
                )
            }
            appState.loading = false
        }
    }

    fun openDownloads(entry: DownloadedSeries) {
        appState.error = null
        seriesState.begin(
            Series(
                entry.seriesId,
                entry.title,
                entry.cover.ifBlank { null }
            ),
            SeriesOrigin.DOWNLOADS
        )

        scope.launch {
            appState.loading = true
            try {
                openDownloadedEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = ::adoptSource,
                    onCachedChapters = { cached ->
                        applyCached(entry.seriesId, cached)
                    },
                    onResolved = { source, series, chapters ->
                        applyResolved(
                            entry.seriesId,
                            source,
                            series,
                            chapters
                        )
                    }
                )
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(
                    error,
                    "Could not open this series"
                )
            }
            appState.loading = false
        }
    }

    fun openHistory(entry: HistoryEntry) {
        appState.error = null

        scope.launch {
            appState.loading = true
            try {
                openHistoryEntry(context, entry) { target ->
                    seriesState.origin = SeriesOrigin.HISTORY
                    adoptSource(target.source)
                    seriesState.active = target.series
                    seriesState.chapters = target.chapters
                    enrichSeries(target.source, target.series)
                    openChapter(target.index)
                }
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(
                    error,
                    "Could not resume"
                )
            }
            appState.loading = false
        }
    }

    private fun adoptSource(source: Source) {
        browseState.source = source
        browseState.sourceId = source.id
    }

    private fun applyCached(
        expectedSeriesId: String,
        chapters: List<Chapter>
    ) {
        if (seriesState.active?.id == expectedSeriesId) {
            seriesState.chapters = chapters
        }
    }

    private fun applyResolved(
        expectedSeriesId: String,
        source: Source,
        series: Series,
        chapters: List<Chapter>
    ) {
        if (seriesState.active?.id != expectedSeriesId) return

        seriesState.active = series
        seriesState.chapters = chapters
        seriesState.fetched = true
        enrichSeries(source, series)
    }
}
