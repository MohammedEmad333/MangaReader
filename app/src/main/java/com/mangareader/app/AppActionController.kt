package com.mangareader.app

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AppActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appState: AppUiState,
    private val browseState: SourceBrowseState,
    private val seriesState: SeriesNavigationState,
    private val readerSession: ReaderSessionState,
    private val globalSearch: GlobalSearchState,
    private val migrationState: MigrationNavigationState,
    private val mediaState: MediaNavigationState
) {
    fun openSource(source: Source, query: String = "", mode: BrowseMode = BrowseMode.POPULAR) {
        appState.error = null
        scope.launch {
            appState.loading = true
            appState.error = browseState.loadFirstPage(context, source, query, mode)
            appState.loading = false
        }
    }

    fun loadMoreSeries() {
        scope.launch { browseState.loadNextPage()?.let { appState.error = it } }
    }

    fun runGlobalSearch(query: String) {
        globalSearch.search(context, scope, appState.configs, appState.extensionSources, query)
    }

    fun cancelGlobalSearch() = globalSearch.cancel()

    fun setGlobalPinnedOnly(value: Boolean) {
        globalSearch.setPinnedOnly(
            context, scope, appState.configs, appState.extensionSources, value
        )
    }

    fun openSourceConfig(config: SourceConfig) {
        val source = resolveSourceConfig(context, config)
        if (source == null) {
            appState.error = "\"${config.label}\" isn't configured yet"
            return
        }
        openSource(source)
    }

    fun enrichSeries(source: Source, series: Series) {
        scope.launch {
            val enriched = withContext(Dispatchers.IO) {
                loadAndHealSeriesDetails(context, source, series)
            }
            if (enriched != null && seriesState.active?.id == series.id) {
                seriesState.active = enriched
            }
        }
    }

    fun openSeries(series: Series) {
        val source = browseState.source ?: return
        seriesState.begin(series, SeriesOrigin.BROWSE)
        seriesState.tagReturn = null
        appState.error = null
        enrichSeries(source, series)
        scope.launch {
            appState.loading = true
            appState.error = seriesState.reloadChapters(context, source, series)
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
        enrichSeries(source, series)
        scope.launch {
            appState.loading = true
            appState.error = seriesState.reloadChapters(context, source, series)
            appState.loading = false
        }
    }

    fun openGlobalResult(source: Source, series: Series) {
        browseState.source = source
        browseState.sourceId = source.id
        browseState.series = null
        browseState.page = 1
        browseState.hasNext = false
        browseState.query = globalSearch.query
        openSeries(series)
        seriesState.origin = SeriesOrigin.GLOBAL_SEARCH
    }

    fun openGlobalSource(source: Source) {
        cancelGlobalSearch()
        globalSearch.open = false
        openSource(source, globalSearch.query)
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
        queueSeriesDownloads(context, seriesState.active, source, chapters)
    }

    fun downloadChapter(source: Source, chapter: Chapter) =
        queueDownloads(source, listOf(chapter))

    fun downloadAll(source: Source, chapters: List<Chapter>) =
        queueDownloads(source, chapters)

    fun bulkSetRead(ids: Set<String>, value: Boolean) {
        if (ids.isEmpty()) return
        scope.launch {
            val result = setLibrarySeriesRead(context, ids, value)
            appState.libraryTick++
            val verb = if (value) "read" else "unread"
            val message = buildString {
                append("Marked ${result.changed} series $verb")
                if (result.skipped > 0) {
                    append(" · ${result.skipped} skipped (no chapter list)")
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun bulkDownload(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            val result = queueLibraryDownloads(context, ids)
            appState.downloadTick++
            val message = buildString {
                append(
                    if (result.added > 0) {
                        "Queued ${result.added} " +
                            if (result.added == 1) "chapter" else "chapters"
                    } else {
                        "Nothing to download — already downloaded or queued"
                    }
                )
                if (result.skipped > 0) {
                    append(" · ${result.skipped} skipped (no chapter list)")
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun performMigration(from: MigrateFrom, toSource: Source, toSeries: Series) {
        scope.launch {
            val migrated = migrateLibrarySeries(context, from, toSource, toSeries)
            migrationState.clear()
            if (migrated) {
                cancelGlobalSearch()
                globalSearch.open = false
                browseState.series = null
                seriesState.tagReturn = null
                seriesState.active = null
                browseState.source = null
                browseState.sourceId = null
                appState.currentTab = 0
                appState.libraryTick++
                Toast.makeText(
                    context,
                    "Migrated to ${toSource.name}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                appState.error = "Couldn't migrate this series"
            }
        }
    }

    fun cancelSeriesDownloads(seriesId: String) {
        if (cancelSeriesDownloadsAction(context, seriesId)) appState.downloadTick++
    }

    fun openFromLibrary(entry: LibraryEntry) {
        appState.error = null
        seriesState.begin(
            Series(entry.seriesId, entry.title, entry.cover.ifBlank { null }),
            SeriesOrigin.LIBRARY
        )
        scope.launch {
            appState.loading = true
            try {
                openLibraryEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = { source ->
                        browseState.source = source
                        browseState.sourceId = source.id
                    },
                    onCachedChapters = { cached ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.chapters = cached
                        }
                    },
                    onResolved = { source, series, chapters ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.active = series
                            seriesState.chapters = chapters
                            seriesState.fetched = true
                            enrichSeries(source, series)
                        }
                    }
                )
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(error, "Could not open this series")
            }
            appState.loading = false
        }
    }

    fun openFromDownloads(entry: DownloadedSeries) {
        appState.error = null
        seriesState.begin(
            Series(entry.seriesId, entry.title, entry.cover.ifBlank { null }),
            SeriesOrigin.DOWNLOADS
        )
        scope.launch {
            appState.loading = true
            try {
                openDownloadedEntry(
                    context = context,
                    entry = entry,
                    onSourceResolved = { source ->
                        browseState.source = source
                        browseState.sourceId = source.id
                    },
                    onCachedChapters = { cached ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.chapters = cached
                        }
                    },
                    onResolved = { source, series, chapters ->
                        if (seriesState.active?.id == entry.seriesId) {
                            seriesState.active = series
                            seriesState.chapters = chapters
                            seriesState.fetched = true
                            enrichSeries(source, series)
                        }
                    }
                )
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(error, "Could not open this series")
            }
            appState.loading = false
        }
    }

    fun openFromHistory(entry: HistoryEntry) {
        appState.error = null
        scope.launch {
            appState.loading = true
            try {
                openHistoryEntry(context, entry) { target ->
                    seriesState.origin = SeriesOrigin.HISTORY
                    browseState.source = target.source
                    browseState.sourceId = target.source.id
                    seriesState.active = target.series
                    seriesState.chapters = target.chapters
                    enrichSeries(target.source, target.series)
                    openChapter(target.index)
                }
            } catch (error: Throwable) {
                appState.error = sourceFailureMessage(error, "Could not resume")
            }
            appState.loading = false
        }
    }
}
