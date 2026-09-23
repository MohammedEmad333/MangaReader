package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class LibraryActionController(
    context: Context,
    scope: CoroutineScope,
    appState: AppUiState,
    browseState: SourceBrowseState,
    seriesState: SeriesNavigationState,
    globalSearch: GlobalSearchState,
    migrationState: MigrationNavigationState,
    enrichSeries: (Source, Series) -> Unit,
    openChapter: (Int) -> Unit
) {
    private val bulkActions = LibraryBulkActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        globalSearch = globalSearch,
        migrationState = migrationState
    )

    private val savedSeriesActions = SavedSeriesActionController(
        context = context,
        scope = scope,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        enrichSeries = enrichSeries,
        openChapter = openChapter
    )

    fun bulkSetRead(ids: Set<String>, value: Boolean) =
        bulkActions.setRead(ids, value)

    fun bulkDownload(ids: Set<String>) =
        bulkActions.download(ids)

    fun migrate(
        from: MigrateFrom,
        toSource: Source,
        toSeries: Series
    ) = bulkActions.migrate(from, toSource, toSeries)

    fun openFromLibrary(entry: LibraryEntry) =
        savedSeriesActions.openLibrary(entry)

    fun openFromDownloads(entry: DownloadedSeries) =
        savedSeriesActions.openDownloads(entry)

    fun openFromHistory(entry: HistoryEntry) =
        savedSeriesActions.openHistory(entry)
}
