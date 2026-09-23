package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootMainTabsRoute(
    context: Context,
    scope: CoroutineScope,
    appState: AppUiState,
    globalSearch: GlobalSearchState,
    actions: AppActionController,
    libraryCategory: String?,
    onLibraryCategoryChange: (String?) -> Unit,
    librarySearch: String,
    onLibrarySearchChange: (String) -> Unit,
    librarySearchOpen: Boolean,
    onLibrarySearchOpenChange: (Boolean) -> Unit,
    libraryScroll: ScrollMemory,
    sourcesScroll: ScrollMemory
) {
    MainTabsRoute(
        currentTab = appState.currentTab,
        onSelectTab = { tab ->
            appState.currentTab = tab
            if (tab == 2) {
                appState.history = History.forDisplay(context)
            }
        },
        libraryTick = appState.libraryTick,
        error = appState.error,
        libraryCategory = libraryCategory,
        onLibraryCategoryChange = { category ->
            onLibraryCategoryChange(category)
            LibraryPrefs.setLastCategory(context, category)
        },
        librarySearch = librarySearch,
        onLibrarySearchChange = onLibrarySearchChange,
        librarySearchOpen = librarySearchOpen,
        onLibrarySearchOpenChange = onLibrarySearchOpenChange,
        libraryScroll = libraryScroll,
        onOpenLibrary = { actions.openFromLibrary(it) },
        onRemoveLibraryMany = { ids ->
            Library.removeAll(context, ids)
            appState.libraryTick++
        },
        onMarkRead = { actions.bulkSetRead(it, true) },
        onMarkUnread = { actions.bulkSetRead(it, false) },
        onDownloadMany = { actions.bulkDownload(it) },
        configs = appState.configs,
        extensions = appState.extensionSources,
        sourcesScroll = sourcesScroll,
        onGlobalSearch = {
            globalSearch.open = true
            if (
                globalSearch.query.isNotBlank() &&
                globalSearch.results.isEmpty() &&
                !globalSearch.running
            ) {
                actions.runGlobalSearch(globalSearch.query)
            }
        },
        onAddSource = {
            appState.editingConfig = SourceConfig(
                SourceManager.newId(),
                "local",
                ""
            )
            appState.showSourceDialog = true
        },
        onOpenConfig = { actions.openSourceConfig(it) },
        onOpenExtension = { actions.openSource(it) },
        onEditConfig = {
            appState.editingConfig = it
            appState.showSourceDialog = true
        },
        onDeleteConfig = {
            SourceManager.remove(context, it.id)
            appState.configs = SourceManager.list(context)
        },
        onExtensionsChanged = {
            scope.launch {
                appState.extensionSources = withContext(Dispatchers.IO) {
                    runCatching {
                        SourceManager.listAllSources(context)
                            .filter { it.id.startsWith("tachi:") }
                    }.getOrDefault(emptyList())
                }
            }
        },
        history = appState.history,
        loading = appState.loading,
        onOpenHistory = { actions.openFromHistory(it) },
        onDeleteHistory = {
            History.remove(context, it.chapterKey)
            appState.history = History.forDisplay(context)
        },
        onClearHistory = {
            History.list(context).forEach {
                History.remove(context, it.chapterKey)
            }
            appState.history = History.forDisplay(context)
        },
        onRefreshHistory = {
            appState.history = History.forDisplay(context)
        },
        downloadTick = appState.downloadTick + DownloadQueue.tick,
        onOpenDownload = { actions.openFromDownloads(it) },
        onOpenDownloadQueue = { appState.downloadsOpen = true },
        onOpenSettings = { appState.settingsOpen = true }
    )
}
