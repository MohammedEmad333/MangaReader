package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun RootFallbackRoute(
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
                context = context,
                scope = scope,
                appState = appState,
                globalSearch = globalSearch,
                actions = actions,
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
