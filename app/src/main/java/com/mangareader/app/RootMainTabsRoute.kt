package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootMainTabsRoute(
    root: RootRouteContext
) {
    val appState = root.app
    val actions = root.actions
    MainTabsRoute(
        currentTab = appState.currentTab,
        onSelectTab = actions::selectTab,
        libraryTick = appState.libraryTick,
        error = appState.error,
        libraryCategory = root.ui.libraryCategory,
        onLibraryCategoryChange = { category ->
            root.ui.onLibraryCategoryChange(category)
            LibraryPrefs.setLastCategory(context, category)
        },
        librarySearch = root.ui.librarySearch,
        onLibrarySearchChange = root.ui.onLibrarySearchChange,
        librarySearchOpen = root.ui.librarySearchOpen,
        onLibrarySearchOpenChange = root.ui.onLibrarySearchOpenChange,
        libraryScroll = root.ui.libraryScroll,
        onOpenLibrary = { actions.openFromLibrary(it) },
        onRemoveLibraryMany = actions::removeLibrary,
        onMarkRead = { actions.bulkSetRead(it, true) },
        onMarkUnread = { actions.bulkSetRead(it, false) },
        onDownloadMany = { actions.bulkDownload(it) },
        configs = appState.configs,
        extensions = appState.extensionSources,
        sourcesScroll = root.ui.sourcesScroll,
        onGlobalSearch = actions::showGlobalSearch,
        onAddSource = actions::addSource,
        onOpenConfig = { actions.openSourceConfig(it) },
        onOpenExtension = { actions.openSource(it) },
        onEditConfig = actions::editSource,
        onDeleteConfig = actions::deleteSource,
        onExtensionsChanged = actions::refreshExtensions,
        history = appState.history,
        loading = appState.loading,
        onOpenHistory = { actions.openFromHistory(it) },
        onDeleteHistory = actions::deleteHistory,
        onClearHistory = actions::clearHistory,
        onRefreshHistory = actions::refreshHistory,
        downloadTick = appState.downloadTick + DownloadQueue.tick,
        onOpenDownload = { actions.openFromDownloads(it) },
        onOpenDownloadQueue = actions::openDownloadQueue,
        onOpenSettings = actions::openSettings
    )
}
