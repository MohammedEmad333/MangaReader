package com.mangareader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun MainTabsRoute(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    libraryTick: Int,
    error: String?,
    libraryCategory: String?,
    onLibraryCategoryChange: (String?) -> Unit,
    librarySearch: String,
    onLibrarySearchChange: (String) -> Unit,
    librarySearchOpen: Boolean,
    onLibrarySearchOpenChange: (Boolean) -> Unit,
    libraryScroll: ScrollMemory,
    onOpenLibrary: (LibraryEntry) -> Unit,
    onRemoveLibraryMany: (Set<String>) -> Unit,
    onMarkRead: (Set<String>) -> Unit,
    onMarkUnread: (Set<String>) -> Unit,
    onDownloadMany: (Set<String>) -> Unit,
    configs: List<SourceConfig>,
    extensions: List<Source>,
    sourcesScroll: ScrollMemory,
    onGlobalSearch: () -> Unit,
    onAddSource: () -> Unit,
    onOpenConfig: (SourceConfig) -> Unit,
    onOpenExtension: (Source) -> Unit,
    onEditConfig: (SourceConfig) -> Unit,
    onDeleteConfig: (SourceConfig) -> Unit,
    onExtensionsChanged: () -> Unit,
    history: List<HistoryEntry>,
    loading: Boolean,
    onOpenHistory: (HistoryEntry) -> Unit,
    onDeleteHistory: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onRefreshHistory: () -> Unit,
    downloadTick: Int,
    onOpenDownload: (DownloadedSeries) -> Unit,
    onOpenDownloadQueue: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        bottomBar = {
            MainBottomNavigation(
                currentTab = currentTab,
                onSelectTab = onSelectTab
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                0 -> LibraryTab(
                    libraryTick = libraryTick,
                    error = error,
                    activeCategory = libraryCategory,
                    onCategoryChange = onLibraryCategoryChange,
                    search = librarySearch,
                    onSearchChange = onLibrarySearchChange,
                    searchOpen = librarySearchOpen,
                    onSearchOpenChange = onLibrarySearchOpenChange,
                    scroll = libraryScroll,
                    onOpen = onOpenLibrary,
                    onRemoveMany = onRemoveLibraryMany,
                    onMarkRead = onMarkRead,
                    onMarkUnread = onMarkUnread,
                    onDownloadMany = onDownloadMany
                )
                1 -> BrowseTab(
                    configs = configs,
                    extensions = extensions,
                    scroll = sourcesScroll,
                    onGlobalSearch = onGlobalSearch,
                    onAdd = onAddSource,
                    onOpenConfig = onOpenConfig,
                    onOpenExtension = onOpenExtension,
                    onEdit = onEditConfig,
                    onDelete = onDeleteConfig,
                    onExtensionsChanged = onExtensionsChanged
                )
                2 -> HistoryScreen(
                    history = history,
                    loading = loading,
                    error = error,
                    onOpen = onOpenHistory,
                    onDelete = onDeleteHistory,
                    libraryTick = libraryTick,
                    onClearAll = onClearHistory,
                    onRefresh = onRefreshHistory
                )
                3 -> DownloadsTab(
                    downloadTick = downloadTick,
                    libraryTick = libraryTick,
                    onOpen = onOpenDownload,
                    onOpenQueue = onOpenDownloadQueue
                )
                4 -> MoreTab(
                    onOpenDownloads = onOpenDownloadQueue,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}
