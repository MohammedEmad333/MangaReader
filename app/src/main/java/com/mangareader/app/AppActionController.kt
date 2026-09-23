package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class AppActionController(
    context: Context,
    scope: CoroutineScope,
    appState: AppUiState,
    browseState: SourceBrowseState,
    seriesState: SeriesNavigationState,
    readerSession: ReaderSessionState,
    globalSearch: GlobalSearchState,
    migrationState: MigrationNavigationState,
    mediaState: MediaNavigationState
) {
    private val sourceActions = SourceActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState
    )

    private val seriesActions = SeriesActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        readerSession = readerSession,
        mediaState = mediaState
    )

    private val searchActions = SearchActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        globalSearch = globalSearch,
        sourceActions = sourceActions,
        openSeries = seriesActions::open
    )

    private val libraryActions = LibraryActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        globalSearch = globalSearch,
        migrationState = migrationState,
        enrichSeries = seriesActions::enrich,
        openChapter = seriesActions::openChapter
    )

    fun openSource(
        source: Source,
        query: String = "",
        mode: BrowseMode = BrowseMode.POPULAR
    ) = sourceActions.openSource(source, query, mode)

    fun loadMoreSeries() = sourceActions.loadMoreSeries()

    fun openSourceConfig(config: SourceConfig) =
        sourceActions.openSourceConfig(config)

    fun runGlobalSearch(query: String) =
        searchActions.run(query)

    fun cancelGlobalSearch() =
        searchActions.cancel()

    fun setGlobalPinnedOnly(value: Boolean) =
        searchActions.setPinnedOnly(value)

    fun openGlobalResult(source: Source, series: Series) =
        searchActions.openResult(source, series)

    fun openGlobalSource(source: Source) =
        searchActions.openSource(source)

    fun enrichSeries(source: Source, series: Series) =
        seriesActions.enrich(source, series)

    fun openSeries(series: Series) =
        seriesActions.open(series)

    fun findVideos(chapter: Chapter) =
        seriesActions.findVideos(chapter)

    fun refreshChapters() =
        seriesActions.refreshChapters()

    fun openChapter(index: Int) =
        seriesActions.openChapter(index)

    fun queueDownloads(source: Source, chapters: List<Chapter>) =
        seriesActions.queueDownloads(source, chapters)

    fun downloadChapter(source: Source, chapter: Chapter) =
        seriesActions.downloadChapter(source, chapter)

    fun downloadAll(source: Source, chapters: List<Chapter>) =
        seriesActions.downloadAll(source, chapters)

    fun cancelSeriesDownloads(seriesId: String) =
        seriesActions.cancelDownloads(seriesId)

    fun bulkSetRead(ids: Set<String>, value: Boolean) =
        libraryActions.bulkSetRead(ids, value)

    fun bulkDownload(ids: Set<String>) =
        libraryActions.bulkDownload(ids)

    fun performMigration(
        from: MigrateFrom,
        toSource: Source,
        toSeries: Series
    ) = libraryActions.migrate(from, toSource, toSeries)

    fun openFromLibrary(entry: LibraryEntry) =
        libraryActions.openFromLibrary(entry)

    fun openFromDownloads(entry: DownloadedSeries) =
        libraryActions.openFromDownloads(entry)

    fun openFromHistory(entry: HistoryEntry) =
        libraryActions.openFromHistory(entry)
}
