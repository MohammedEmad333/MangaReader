package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable

@Composable
internal fun RootGlobalSearchRoute(
    context: Context,
    appState: AppUiState,
    browseState: SourceBrowseState,
    seriesState: SeriesNavigationState,
    globalSearch: GlobalSearchState,
    migrationState: MigrationNavigationState,
    actions: AppActionController
) {
    GlobalSearchRoute(
        query = globalSearch.query,
        results = globalSearch.results,
        running = globalSearch.running,
        done = globalSearch.done,
        total = globalSearch.total,
        pinnedOnly = globalSearch.pinnedOnly,
        onTogglePinnedOnly = { actions.setGlobalPinnedOnly(it) },
        hasResultsOnly = globalSearch.hasResultsOnly,
        onToggleHasResultsOnly = { globalSearch.hasResultsOnly = it },
        recents = globalSearch.recents,
        onRemoveRecent = {
            globalSearch.recents = SourcePrefs.removeRecentSearch(context, it)
        },
        onClearRecents = {
            SourcePrefs.clearRecentSearches(context)
            globalSearch.recents = emptyList()
        },
        onSearch = { actions.runGlobalSearch(it) },
        onCancel = { actions.cancelGlobalSearch() },
        onOpenSource = { actions.openGlobalSource(it) },
        migrating = migrationState.from != null,
        onOpenSeries = { source, series ->
            if (migrationState.from != null) {
                migrationState.target = source to series
            } else {
                actions.openGlobalResult(source, series)
            }
        },
        libraryTick = appState.libraryTick,
        onBack = {
            actions.cancelGlobalSearch()
            globalSearch.open = false
            migrationState.from = null
            browseState.series = null

            val cameFromTag = seriesState.tagReturn
            if (cameFromTag != null) {
                seriesState.tagReturn = null
                seriesState.active = cameFromTag
            } else {
                browseState.source = null
                browseState.sourceId = null
            }
        },
        migrateFrom = migrationState.from,
        migrateTarget = migrationState.target,
        onDismissMigration = { migrationState.target = null },
        onConfirmMigration = { from, targetSource, targetSeries ->
            actions.performMigration(from, targetSource, targetSeries)
        }
    )
}
