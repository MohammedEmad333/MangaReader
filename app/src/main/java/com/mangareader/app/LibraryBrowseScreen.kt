package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
// PullToRefreshBox lives in a SUB-PACKAGE of material3, which the wildcard
// above does not reach. Needs naming explicitly or it resolves to nothing.
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import dalvik.system.PathClassLoader
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    title: String,
    series: List<Series>?,
    loading: Boolean,
    error: String?,
    supportsSearch: Boolean,
    supportsLatest: Boolean,
    supportsFilters: Boolean,
    onOpenFilters: () -> Unit,
    onDiagnose: () -> Unit,
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    query: String,
    hasNext: Boolean,
    loadingMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRescan: () -> Unit,
    onOpen: (Series) -> Unit,
    onBack: () -> Unit,
    /**
     * Bumped whenever library state moves, so the corner markers below refresh
     * after adding or removing a series without leaving the screen.
     */
    libraryTick: Int,
    /** Drives the `Local` chip; extension sources use `tachi:` or `aniyomi:` prefixes. */
    isLocalSource: Boolean,
    /** Held by the root so the grid's position outlives this branch. */
    scroll: ScrollMemory,
    /**
     * Opens a visible WebView at this source's site so the user can answer a
     * Cloudflare challenge by hand. Null for sources with no site to open —
     * local folders, and any extension that isn't an `HttpSource`.
     */
    onSolveChallenge: (() -> Unit)? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var searchField by remember(query) { mutableStateOf(query) }
    // Opens itself when a search is already running, so returning to a screen
    // showing results doesn't hide the field that produced them.
    var searchOpen by remember(title) { mutableStateOf(query.isNotBlank()) }
    var view by remember {
        mutableStateOf(BrowseView.from(prefs(context).getString(KEY_BROWSE_VIEW, null)))
    }
    val coverMinDp = when (prefs(context).getString("cover_size", "medium")) {
        "small" -> 88.dp
        "large" -> 140.dp
        else -> 110.dp
    }

    // No category chips here any more (0.56). They filtered only the page
    // already loaded — on an extension source that's Popular's first ~20 titles
    // — against the user's library categories, so they were near-always empty
    // and disabled "Load more" while active. Library filtering belongs to the
    // library; the listing controls are Popular, Latest and Filter.
    val shown = series ?: emptyList()

    // Survives opening a series and coming back: this screen is a branch of the
    // routing chain, so it's torn down and rebuilt, and the grid's own state
    // goes with it. Keyed per source and view; reset when the listing itself
    // changes underneath it.
    val browseOrdering = remember(title, query, mode) { listOf(title, query, mode) }
    scroll.sync(browseOrdering)
    val gridState = rememberRestoredGridState(
        memory = scroll,
        key = "$title#${view.key}",
        ordering = browseOrdering
    )

    Column(modifier = Modifier.fillMaxSize()) {
        BrowseScreenControls(
            title = title,
            supportsSearch = supportsSearch,
            supportsLatest = supportsLatest,
            supportsFilters = supportsFilters,
            searchOpen = searchOpen,
            query = query,
            searchField = searchField,
            onSearchFieldChange = { searchField = it },
            onToggleSearch = {
                if (searchOpen && query.isNotBlank()) {
                    searchField = ""
                    onSearch("")
                }
                searchOpen = !searchOpen
            },
            onSearch = { onSearch(searchField.trim()) },
            onClearSearch = {
                searchField = ""
                onSearch("")
            },
            mode = mode,
            onModeChange = onModeChange,
            onOpenFilters = onOpenFilters,
            view = view,
            onViewChange = { option ->
                view = option
                prefs(context).edit()
                    .putString(KEY_BROWSE_VIEW, option.key)
                    .apply()
            },
            onRescan = onRescan,
            onDiagnose = onDiagnose,
            onBack = onBack,
        )

        BrowseScreenBody(
            loading = loading,
            error = error,
            onSolveChallenge = onSolveChallenge,
            shown = shown,
            query = query,
            isLocalSource = isLocalSource,
            onDiagnose = onDiagnose,
            libraryTick = libraryTick,
            onRescan = onRescan,
            view = view,
            coverMinDp = coverMinDp,
            gridState = gridState,
            hasNext = hasNext,
            loadingMore = loadingMore,
            onLoadMore = onLoadMore,
            onOpen = onOpen,
        )
    }
}
