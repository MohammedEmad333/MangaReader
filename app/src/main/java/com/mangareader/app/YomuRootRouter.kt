package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun YomuRootRouter(
    state: YomuStateBundle,
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
    val appState = state.app
    val browseState = state.browse
    val seriesState = state.series
    val globalSearch = state.search
    val root = RootRouteContext(
        context = context,
        scope = scope,
        state = state,
        actions = actions
    )

    when (
        RootTransientRoute(root)
    ) {
        TransientRouteResult.EMBED -> return
        TransientRouteResult.CONTENT -> Unit
        TransientRouteResult.NONE -> {
            if (seriesState.active != null) {
                RootSeriesRoute(
                    root = root,
                    scroll = seriesScroll
                )
            } else if (globalSearch.open) {
                RootGlobalSearchRoute(root)
            } else if (browseState.source != null) {
                RootSourceBrowseRoute(
                    root = root,
                    scroll = browseScroll
                )
            } else {
                RootFallbackRoute(
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

    RootOverlays(
        root = root,
        releaseNotes = releaseNotes,
        whatsNewOpen = whatsNewOpen,
        onDismissWhatsNew = onDismissWhatsNew
    )

}
