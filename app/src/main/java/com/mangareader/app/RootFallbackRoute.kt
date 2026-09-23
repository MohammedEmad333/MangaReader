package com.mangareader.app

import androidx.compose.runtime.Composable

@Composable
internal fun RootFallbackRoute(
    root: RootRouteContext,
    libraryCategory: String?,
    onLibraryCategoryChange: (String?) -> Unit,
    librarySearch: String,
    onLibrarySearchChange: (String) -> Unit,
    librarySearchOpen: Boolean,
    onLibrarySearchOpenChange: (Boolean) -> Unit,
    libraryScroll: ScrollMemory,
    sourcesScroll: ScrollMemory
) {
    val appState = root.app
    when {
        appState.downloadsOpen -> {
            DownloadQueueScreen(
                onBack = { appState.downloadsOpen = false }
            )
        }

        appState.settingsOpen -> {
            SettingsScreen(
                onBack = { appState.settingsOpen = false },
                onOpenDownloadQueue = {
                    appState.downloadsOpen = true
                }
            )
        }

        else -> {
            RootMainTabsRoute(
                root = root,
                libraryCategory = libraryCategory,
                onLibraryCategoryChange = onLibraryCategoryChange,
                librarySearch = librarySearch,
                onLibrarySearchChange = onLibrarySearchChange,
                librarySearchOpen = librarySearchOpen,
                onLibrarySearchOpenChange = onLibrarySearchOpenChange,
                libraryScroll = libraryScroll,
                sourcesScroll = sourcesScroll
            )
        }
    }
}
