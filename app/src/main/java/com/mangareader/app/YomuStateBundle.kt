package com.mangareader.app

internal class YomuStateBundle(
    startup: StartupUiSnapshot,
) {
    val app = AppUiState(
        initialConfigs = startup.configs,
        initialHistory = startup.history,
    )
    val browse = SourceBrowseState()
    val series = SeriesNavigationState()
    val reader = ReaderSessionState()
    val search = GlobalSearchState(
        initialPinnedOnly = startup.searchPinnedOnly,
        initialHasResultsOnly = startup.searchHasResultsOnly,
        initialMediaFilter = startup.searchMediaFilter,
        initialRecents = startup.recentSearches,
    )
    val migration = MigrationNavigationState()
    val media = MediaNavigationState()
}
