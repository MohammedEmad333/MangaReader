package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class AppActionController(
    context: Context,
    scope: CoroutineScope,
    state: YomuStateBundle
) {
    private val appState = state.app
    private val browseState = state.browse
    private val seriesState = state.series
    private val readerSession = state.reader
    private val globalSearch = state.search
    private val migrationState = state.migration
    private val mediaState = state.media

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

    private val shellActions = ShellActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        globalSearch = globalSearch,
        migrationState = migrationState,
        mediaState = mediaState,
        openSource = sourceActions::openSource,
        runGlobalSearch = searchActions::run
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
        openChapter = seriesActions::openChapter,
        openEpisode = seriesActions::findVideos,
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
    fun selectTab(tab: Int) =
        shellActions.selectTab(tab)

    fun removeLibrary(ids: Set<String>) =
        shellActions.removeLibrary(ids)

    fun showGlobalSearch() =
        shellActions.openGlobalSearch()

    fun addSource() =
        shellActions.addSource()

    fun editSource(config: SourceConfig) =
        shellActions.editSource(config)

    fun deleteSource(config: SourceConfig) =
        shellActions.deleteSource(config)

    fun refreshExtensions() =
        shellActions.refreshExtensions()

    fun deleteHistory(entry: HistoryEntry) =
        shellActions.deleteHistory(entry)

    fun clearHistory() =
        shellActions.clearHistory()

    fun refreshHistory() =
        shellActions.refreshHistory()

    fun openDownloadQueue() =
        shellActions.openDownloadQueue()

    fun closeDownloadQueue() =
        shellActions.closeDownloadQueue()

    fun openSettings() =
        shellActions.openSettings()

    fun closeSettings() =
        shellActions.closeSettings()

    fun showFilters() =
        shellActions.showFilters()

    fun dismissFilters() =
        shellActions.dismissFilters()

    fun applyFilters(source: Source) =
        shellActions.applyFilters(source)

    fun showProbe() =
        shellActions.showProbe()

    fun dismissProbe() =
        shellActions.dismissProbe()

    fun setEditingConfig(config: SourceConfig?) =
        shellActions.setEditingConfig(config)

    fun dismissSourceDialog() =
        shellActions.dismissSourceDialog()

    fun saveSource(config: SourceConfig) =
        shellActions.saveSource(config)

    fun dismissVideoScan() =
        shellActions.dismissVideoScan()

    fun openEmbed(url: String) =
        shellActions.openEmbed(url)

    fun openExternalVideo(video: PlayableVideo) =
        shellActions.openExternalVideo(video)

    fun openExternalVideo(url: String) =
        shellActions.openExternalVideo(url)

    fun backFromSourceBrowse() =
        shellActions.backFromSourceBrowse()

    fun backFromSeries() =
        shellActions.backFromSeries()

    fun searchSeriesTag(tag: String) =
        shellActions.searchSeriesTag(tag)

    fun searchGlobalTag(tag: String) =
        shellActions.searchGlobalTag(tag)

    fun startMigration() =
        shellActions.startMigration()

    fun backFromGlobalSearch() =
        shellActions.backFromGlobalSearch()

    fun setSearchHasResultsOnly(value: Boolean) =
        shellActions.setSearchHasResultsOnly(value)

    fun removeRecentSearch(query: String) =
        shellActions.removeRecentSearch(query)

    fun clearRecentSearches() =
        shellActions.clearRecentSearches()

    fun selectMigrationTarget(source: Source, series: Series) =
        shellActions.selectMigrationTarget(source, series)

    fun dismissMigrationTarget() =
        shellActions.dismissMigrationTarget()

}
