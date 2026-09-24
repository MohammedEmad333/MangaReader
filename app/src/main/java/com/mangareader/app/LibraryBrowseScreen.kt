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

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        // Matched on the message rather than a status code because that's all
        // that survives: the failure arrives here as an already-formatted string
        // from `Response.failureMessage()`, which is the one place that can see
        // the Cloudflare headers.
        val challengeable = onSolveChallenge != null &&
            error?.contains("Cloudflare", ignoreCase = true) == true
        ErrorBanner(
            error = error,
            actionLabel = if (challengeable) "Open in WebView" else null,
            onAction = if (challengeable) onSolveChallenge else null
        )

        if (shown.isEmpty()) {
            BrowseEmptyState(
                loading = loading,
                error = error,
                query = query,
                isLocalSource = isLocalSource,
                onDiagnose = onDiagnose,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
        } else {
            // Once per screen, never per cell — each field behind this is a
            // whole-store read, and asking per row is the shape §5 records as
            // "an import is a load test".
            val marks = rememberEntryMarks(libraryTick)

            // RESTORED in 0.165. 0.163 took this back out on the theory that
            // it broke the scroll handle's reach — it did not. Reverting it
            // changed nothing, the arithmetic was the cause (0.164), and the
            // wrapper was blamed only because it was the most recent change.
            // Removing it was still the right call at the time: it was a
            // feature bundled into a bug-fix release, and a real fix should not
            // wait behind one.
            var refreshing by remember { mutableStateOf(false) }
            // Cleared from an EFFECT: PullToRefreshBox has to observe the flag
            // go true and then false to run its retract animation, and clearing
            // it inline leaves the arrow parked on screen (0.160/0.161).
            LaunchedEffect(refreshing) {
                if (refreshing) refreshing = false
            }
            PullToRefreshBox(
                // onRescan already reloads page one with the current query and
                // mode, and is what the ⋮ menu calls, so the gesture and the
                // menu cannot drift into meaning different things.
                //
                // This one IS a network call, unlike Downloads and History, and
                // the indicator still retracts immediately rather than tracking
                // it. Deliberate: `loading` is app-wide and written by six
                // launch blocks in YomuApp, and binding a gesture to it is how
                // the series screen came to need a chapters.isNotEmpty() gate.
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    onRescan()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                // List is the same grid with one column, so paging, the empty
                // state and "Load more" stay on one code path instead of two.
                columns = if (view == BrowseView.LIST) GridCells.Fixed(1)
                else GridCells.Adaptive(minSize = coverMinDp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp)
            ) {
                items(shown) { s ->
                    when (view) {
                        BrowseView.COMFORTABLE -> ComfortableCell(s, marks, isLocalSource, onOpen)
                        BrowseView.COMPACT -> CompactCell(s, marks, isLocalSource, onOpen)
                        BrowseView.LIST -> ListRow(s, marks, isLocalSource, onOpen)
                    }
                }

                // Paging is manual rather than infinite-scroll: one tap per page
                // keeps request volume predictable and visible.
                if (hasNext) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator()
                            } else {
                                OutlinedButton(onClick = onLoadMore) { Text("Load more") }
                            }
                        }
                    }
                }
            }

            // GridScrollHandle, which seeks in ROWS. 0.158 used ScrollHandle
            // with cell indices and could not reach the last cell: scrollToItem
            // aligns the row containing an index to the top, so the start gets
            // pulled back to a row boundary and the final partial row drops
            // below the fold. 0.163 wrongly blamed a PullToRefreshBox wrapper
            // and reverting it changed nothing, because the arithmetic was
            // always the cause.
            //
            // shown.size + 1 when there is a next page, because "Load more" is
            // a lazy item and scrollToItem counts it. It spans the full width,
            // so it is its own row and the row arithmetic handles it.
            GridScrollHandle(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            }
            } // PullToRefreshBox
        }
    }
}
