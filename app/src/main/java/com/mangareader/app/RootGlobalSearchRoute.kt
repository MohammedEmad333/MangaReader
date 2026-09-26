package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootGlobalSearchRoute(
    root: RootRouteContext
) {
    val appState = root.app
    val globalSearch = root.search
    val migrationState = root.migration
    val actions = root.actions
    GlobalSearchRoute(
        query = globalSearch.query,
        results = globalSearch.results,
        running = globalSearch.running,
        done = globalSearch.done,
        total = globalSearch.total,
        pinnedOnly = globalSearch.pinnedOnly,
        onTogglePinnedOnly = { actions.setGlobalPinnedOnly(it) },
        hasResultsOnly = globalSearch.hasResultsOnly,
        onToggleHasResultsOnly = actions::setSearchHasResultsOnly,
        mediaFilter = globalSearch.mediaFilter,
        onMediaFilterChange = actions::setGlobalMediaFilter,
        recents = globalSearch.recents,
        onRemoveRecent = actions::removeRecentSearch,
        onClearRecents = actions::clearRecentSearches,
        onSearch = { actions.runGlobalSearch(it) },
        onCancel = { actions.cancelGlobalSearch() },
        onOpenSource = { actions.openGlobalSource(it) },
        migrating = migrationState.from != null,
        onOpenSeries = { source, series ->
            if (migrationState.from != null) {
                actions.selectMigrationTarget(source, series)
            } else {
                actions.openGlobalResult(source, series)
            }
        },
        libraryTick = appState.libraryTick,
        scroll = root.ui.globalSearchScroll,
        onBack = actions::backFromGlobalSearch,

        migrateFrom = migrationState.from,
        migrateTarget = migrationState.target,
        onDismissMigration = actions::dismissMigrationTarget,
        onConfirmMigration = { from, targetSource, targetSeries ->
            actions.performMigration(from, targetSource, targetSeries)
        }
    )
}
