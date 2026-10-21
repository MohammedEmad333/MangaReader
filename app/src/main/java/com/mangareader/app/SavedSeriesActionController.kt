package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SavedSeriesActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val readerSession: ReaderSessionState,
    private val enrichSeries: (Source, Series) -> Unit,
    private val openChapter: (Int) -> Unit,
    private val openEpisode: (Chapter) -> Unit,
) {
    fun openLibrary(entry: LibraryEntry, requestedChapterId: String? = null) {
        readerSession.close()
        appState.error = null
        seriesState.begin(
            Series(
                entry.seriesId,
                entry.title,
                entry.cover.ifBlank { null }
            ),
            SeriesOrigin.LIBRARY
        )

        val generation = seriesState.snapshotGeneration()
        scope.launch {
            appState.loading = true
            try {
                openLibraryEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = { source ->
                        if (seriesState.isCurrent(entry.seriesId, generation)) adoptSource(source)
                    },
                    onCachedChapters = { cached ->
                        applyCached(entry.seriesId, generation, cached)
                    },
                    onResolved = { source, series, chapters ->
                        applyResolved(
                            entry.seriesId,
                            generation,
                            source,
                            series,
                            chapters
                        )
                        if (requestedChapterId != null && seriesState.isCurrent(entry.seriesId, generation)) {
                            findUpdateChapterIndex(chapters, requestedChapterId)
                                ?.let { index ->
                                    if (source.isAnime) chapters.getOrNull(index)?.let(openEpisode)
                                    else openChapter(index)
                                }
                        }
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (seriesState.isCurrent(entry.seriesId, generation)) {
                    appState.error = sourceFailureMessage(error, "Could not open this series")
                }
            } finally {
                if (seriesState.isCurrent(entry.seriesId, generation)) appState.loading = false
            }
        }
    }

    fun openDownloads(entry: DownloadedSeries) {
        readerSession.close()
        appState.error = null
        seriesState.begin(
            Series(
                entry.seriesId,
                entry.title,
                entry.cover.ifBlank { null }
            ),
            SeriesOrigin.DOWNLOADS
        )

        val generation = seriesState.snapshotGeneration()
        scope.launch {
            appState.loading = true
            try {
                openDownloadedEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = { source ->
                        if (seriesState.isCurrent(entry.seriesId, generation)) adoptSource(source)
                    },
                    onCachedChapters = { cached ->
                        applyCached(entry.seriesId, generation, cached)
                    },
                    onResolved = { source, series, chapters ->
                        applyResolved(
                            entry.seriesId,
                            generation,
                            source,
                            series,
                            chapters
                        )
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (seriesState.isCurrent(entry.seriesId, generation)) {
                    appState.error = sourceFailureMessage(error, "Could not open this series")
                }
            } finally {
                if (seriesState.isCurrent(entry.seriesId, generation)) appState.loading = false
            }
        }
    }

    fun openHistory(entry: HistoryEntry) {
        readerSession.close()
        appState.error = null
        // Invalidate any in-flight library/download opening request immediately.
        seriesState.clear()
        val generation = seriesState.snapshotGeneration()

        scope.launch {
            appState.loading = true
            var adoptedGeneration = generation
            try {
                openHistoryEntry(context, entry) { target ->
                    if (generation != seriesState.snapshotGeneration()) return@openHistoryEntry
                    seriesState.begin(target.series, SeriesOrigin.HISTORY)
                    adoptedGeneration = seriesState.snapshotGeneration()
                    adoptSource(target.source)
                    seriesState.active = target.series
                    seriesState.chapters = target.chapters
                    enrichSeries(target.source, target.series)
                    if (target.source.isAnime) {
                        target.chapters.getOrNull(target.index)?.let(openEpisode)
                    } else {
                        openChapter(target.index)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (adoptedGeneration == seriesState.snapshotGeneration()) {
                    appState.error = sourceFailureMessage(error, "Could not resume")
                }
            } finally {
                if (adoptedGeneration == seriesState.snapshotGeneration()) appState.loading = false
            }
        }
    }

    private fun adoptSource(source: Source) {
        browseState.source = source
        browseState.sourceId = source.id
    }

    private fun applyCached(
        expectedSeriesId: String,
        generation: Long,
        chapters: List<Chapter>
    ) {
        if (seriesState.isCurrent(expectedSeriesId, generation)) {
            seriesState.chapters = chapters
        }
    }

    private fun applyResolved(
        expectedSeriesId: String,
        generation: Long,
        source: Source,
        series: Series,
        chapters: List<Chapter>
    ) {
        if (!seriesState.isCurrent(expectedSeriesId, generation)) return

        seriesState.active = series
        seriesState.chapters = chapters
        seriesState.fetched = true
        enrichSeries(source, series)
    }
}
