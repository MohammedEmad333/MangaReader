package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class ShellActionController(
    context: Context,
    scope: CoroutineScope,
    appState: AppUiState,
    browseState: SourceBrowseState,
    seriesState: SeriesNavigationState,
    globalSearch: GlobalSearchState,
    migrationState: MigrationNavigationState,
    mediaState: MediaNavigationState,
    openSource: (Source, String, BrowseMode) -> Unit,
    runGlobalSearch: (String) -> Unit
) {
    private val appShell = AppShellActionController(
        context = context,
        scope = scope,
        appState = appState,
        openSource = openSource
    )

    private val navigation = RootNavigationActionController(
        context = context,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        globalSearch = globalSearch,
        migrationState = migrationState,
        openSource = openSource,
        runGlobalSearch = runGlobalSearch
    )

    private val media = MediaShellActionController(
        context = context,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        mediaState = mediaState
    )

    fun selectTab(tab: Int) =
        appShell.selectTab(tab)

    fun removeLibrary(ids: Set<String>) =
        appShell.removeLibrary(ids)

    fun addSource() =
        appShell.addSource()

    fun editSource(config: SourceConfig) =
        appShell.editSource(config)

    fun deleteSource(config: SourceConfig) =
        appShell.deleteSource(config)

    fun refreshExtensions() =
        appShell.refreshExtensions()

    fun deleteHistory(entry: HistoryEntry) =
        appShell.deleteHistory(entry)

    fun clearHistory() =
        appShell.clearHistory()

    fun refreshHistory() =
        appShell.refreshHistory()

    fun openDownloadQueue() =
        appShell.openDownloadQueue()

    fun closeDownloadQueue() =
        appShell.closeDownloadQueue()

    fun openSettings() =
        appShell.openSettings()

    fun closeSettings() =
        appShell.closeSettings()

    fun showFilters() =
        appShell.showFilters()

    fun dismissFilters() =
        appShell.dismissFilters()

    fun applyFilters(source: Source) =
        appShell.applyFilters(source)

    fun showProbe() =
        appShell.showProbe()

    fun dismissProbe() =
        appShell.dismissProbe()

    fun setEditingConfig(config: SourceConfig?) =
        appShell.setEditingConfig(config)

    fun dismissSourceDialog() =
        appShell.dismissSourceDialog()

    fun saveSource(config: SourceConfig) =
        appShell.saveSource(config)

    fun openGlobalSearch() =
        navigation.openGlobalSearch()

    fun backFromSourceBrowse() =
        navigation.backFromSourceBrowse()

    fun backFromSeries() =
        navigation.backFromSeries()

    fun searchSeriesTag(tag: String) =
        navigation.searchSeriesTag(tag)

    fun searchGlobalTag(tag: String) =
        navigation.searchGlobalTag(tag)

    fun startMigration() =
        navigation.startMigration()

    fun backFromGlobalSearch() =
        navigation.backFromGlobalSearch()

    fun setSearchHasResultsOnly(value: Boolean) =
        navigation.setSearchHasResultsOnly(value)

    fun removeRecentSearch(query: String) =
        navigation.removeRecentSearch(query)

    fun clearRecentSearches() =
        navigation.clearRecentSearches()

    fun selectMigrationTarget(
        source: Source,
        series: Series
    ) = navigation.selectMigrationTarget(source, series)

    fun dismissMigrationTarget() =
        navigation.dismissMigrationTarget()

    fun dismissVideoScan() =
        media.dismissVideoScan()

    fun openEmbed(url: String) =
        media.openEmbed(url)

    fun openExternalVideo(video: PlayableVideo) =
        media.openExternalVideo(video)

    fun openExternalVideo(url: String) =
        media.openExternalVideo(PlayableVideo(url))
}
