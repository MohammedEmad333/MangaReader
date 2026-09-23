package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun YomuRootRouter(
    appState: AppUiState,
    browseState: SourceBrowseState,
    seriesState: SeriesNavigationState,
    readerSession: ReaderSessionState,
    globalSearch: GlobalSearchState,
    migrationState: MigrationNavigationState,
    mediaState: MediaNavigationState,
    actions: AppActionController,
    libraryCategory: String?,
    onLibraryCategoryChange: (String?) -> Unit,
    librarySearch: String,
    onLibrarySearchChange: (String) -> Unit,
    librarySearchOpen: Boolean,
    onLibrarySearchOpenChange: (Boolean) -> Unit,
    libraryScroll: ScrollMemory,
    browseScroll: ScrollMemory,
    seriesScroll: ScrollMemory,
    sourcesScroll: ScrollMemory,
    releaseNotes: List<ReleaseNote>,
    whatsNewOpen: Boolean,
    onDismissWhatsNew: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    when (
        RootTransientRoute(
            context = context,
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            readerSession = readerSession,
            mediaState = mediaState,
            actions = actions
        )
    ) {
        TransientRouteResult.EMBED -> return
        TransientRouteResult.CONTENT -> Unit
        TransientRouteResult.NONE -> {
if (seriesState.active != null) {
        RootSeriesRoute(
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            globalSearch = globalSearch,
            migrationState = migrationState,
            actions = actions,
            scroll = seriesScroll
        )
    } else if (globalSearch.open) {
        RootGlobalSearchRoute(
            context = context,
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            globalSearch = globalSearch,
            migrationState = migrationState,
            actions = actions
        )
    } else if (browseState.source != null) {
        RootSourceBrowseRoute(
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            actions = actions,
            scroll = browseScroll
        )
    } else if (appState.downloadsOpen) {
        // Above settings on purpose, and it's the ordering that does the work.
        // The queue is reachable from More *and* from Settings > Downloads, and
        // leaving `appState.settingsOpen` set while this renders means backing out of the
        // queue falls through to whichever of the two it was opened from — no
        // "where did I come from" flag, just two booleans read in order.
        DownloadQueueScreen(onBack = { appState.downloadsOpen = false })
    } else if (appState.settingsOpen) {
        SettingsScreen(
            onBack = { appState.settingsOpen = false },
            onOpenDownloadQueue = { appState.downloadsOpen = true }
        )
    } else {
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


    RootOverlays(
        context = context,
        appState = appState,
        browseState = browseState,
        seriesState = seriesState,
        mediaState = mediaState,
        actions = actions,
        releaseNotes = releaseNotes,
        whatsNewOpen = whatsNewOpen,
        onDismissWhatsNew = onDismissWhatsNew
    )

}
