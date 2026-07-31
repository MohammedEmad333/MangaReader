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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- prefs: the same store every other object in this package uses ----------

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
    var viewMenuOpen by remember { mutableStateOf(false) }
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
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { BackButton(onBack) },
            actions = {
                if (supportsSearch) {
                    IconButton(onClick = {
                        // Closing while a search is live clears it, because the
                        // grid underneath is showing results and hiding the field
                        // would leave no way to tell that from the catalogue.
                        if (searchOpen && query.isNotBlank()) {
                            searchField = ""
                            onSearch("")
                        }
                        searchOpen = !searchOpen
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search"
                        )
                    }
                }
                IconButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                Box {
                    IconButton(onClick = { viewMenuOpen = true }) {
                        // MoreVert, not a grid glyph: `material-icons-core` has no
                        // grid_view, and `Icons.Filled.List` is the one icon this
                        // repo refuses to import because it puts a property named
                        // `List` in file scope next to `List<Foo>` type usages.
                        Icon(Icons.Default.MoreVert, contentDescription = "View options")
                    }
                    DropdownMenu(
                        expanded = viewMenuOpen,
                        onDismissRequest = { viewMenuOpen = false }
                    ) {
                        BrowseView.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (option == view) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    view = option
                                    prefs(context).edit()
                                        .putString(KEY_BROWSE_VIEW, option.key).apply()
                                    viewMenuOpen = false
                                }
                            )
                        }
                        HorizontalDivider()
                        // Lives here rather than on the error banner because the
                        // question it answers \u2014 what is this source actually
                        // returning \u2014 is worth asking when nothing looks wrong
                        // too. An empty grid and a 403 are the same mystery.
                        DropdownMenuItem(
                            text = { Text("Connection probe") },
                            onClick = {
                                viewMenuOpen = false
                                onDiagnose()
                            }
                        )
                    }
                }
            }
        )

        // Only where there's a choice to make: a source with neither a Latest
        // listing nor filters would get a single chip that does nothing.
        //
        // Popular sits outside the `supportsLatest` guard on purpose. It used to
        // be inside it, which meant a source declaring `supportsLatest = false`
        // while offering filters — Roku Hentai is one — rendered a Filter chip
        // and nothing else, so applying a filter was a one-way trip with no
        // visible way back to the plain listing. Whenever this row exists at
        // all, the default listing has to be reachable from it.
        if (supportsLatest || supportsFilters) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Neither listing chip reads as selected while a search is
                // showing: the grid is neither listing at that point, and
                // claiming otherwise is the kind of small lie that makes a
                // screen feel broken.
                FilterChip(
                    selected = query.isBlank() && mode == BrowseMode.POPULAR,
                    onClick = { onModeChange(BrowseMode.POPULAR) },
                    label = { Text("Popular") }
                )
                if (supportsLatest) {
                    FilterChip(
                        selected = query.isBlank() && mode == BrowseMode.LATEST,
                        onClick = { onModeChange(BrowseMode.LATEST) },
                        label = { Text("Latest") }
                    )
                }
                if (supportsFilters) {
                    // Opens the sheet rather than switching listing directly:
                    // it only becomes the active listing once something is
                    // applied, so it reads as selected but isn't a mode toggle.
                    FilterChip(
                        selected = query.isBlank() && mode == BrowseMode.FILTER,
                        onClick = { onOpenFilters() },
                        label = { Text("Filter") }
                    )
                }
            }
        }

        if (supportsSearch && searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchField,
                    onValueChange = { searchField = it },
                    label = { Text("Search this source") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSearch(searchField.trim()) }) { Text("Go") }
            }
            if (query.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Results for \u201c$query\u201d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        searchField = ""
                        onSearch("")
                    }) { Text("Clear") }
                }
            }
        }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!loading) {
                    Text(
                        "Nothing found in this source.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                // List is the same grid with one column, so paging, the empty
                // state and "Load more" stay on one code path instead of two.
                columns = if (view == BrowseView.LIST) GridCells.Fixed(1)
                else GridCells.Adaptive(minSize = coverMinDp),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(6.dp)
            ) {
                items(shown) { s ->
                    when (view) {
                        BrowseView.COMFORTABLE -> ComfortableCell(s, onOpen)
                        BrowseView.COMPACT -> CompactCell(s, onOpen)
                        BrowseView.LIST -> ListRow(s, onOpen)
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
        }
    }
}

// ---------- series ----------

/** One labelled icon action under the series header. */
@Composable
private fun SeriesAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SeriesScreen(
    series: Series,
    chapters: List<Chapter>,
    sourceId: String,
    sourceName: String,
    canDownload: Boolean,
    downloadProgress: Map<String, Int>,
    downloadTick: Int,
    downloadingAll: Boolean,
    onDownload: (Chapter) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownloads: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onSetRead: (List<Chapter>, Boolean) -> Unit,
    loading: Boolean,
    error: String?,
    readTick: Int,
    /**
     * Where this screen was scrolled to, held by `YomuApp`.
     *
     * Opening a chapter doesn't cover this screen, it replaces it — the routing
     * chain is an if/else and the reader's branch sits above this one — so a
     * `LazyListState` remembered in here is destroyed the moment a chapter
     * opens, and coming back landed at the top of a chapter list the user may
     * have scrolled a long way down. Same bug as the library's tab, search and
     * grid position, found a fourth time, and it takes the same answer.
     */
    scroll: ScrollMemory,
    onOpen: (Int) -> Unit,
    onLibraryChanged: () -> Unit,
    /** Runs [String] as a search of this series' own source. */
    onSearchTag: (String) -> Unit,
    /** Runs [String] across every searchable source. */
    onGlobalSearchTag: (String) -> Unit,
    onSolveChallenge: (() -> Unit)?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showCategories by remember { mutableStateOf(false) }
    var showAddToLibrary by remember { mutableStateOf(false) }
    var descriptionExpanded by remember(series.id) { mutableStateOf(false) }
    var confirmDeleteChapter by remember(series.id) { mutableStateOf<Chapter?>(null) }
    var confirmDeleteSelection by remember(series.id) { mutableStateOf(false) }
    // Chapter ids, not indices: the list is re-fetched on rescan and after a
    // solved challenge, and indices would silently point at different chapters.
    var selectedIds by remember(series.id) { mutableStateOf(emptySet<String>()) }
    val selecting = selectedIds.isNotEmpty()
    val selectedChapters = remember(selectedIds, chapters) {
        chapters.filter { it.id in selectedIds }
    }

    // Back leaves the selection before it leaves the screen — the same rule the
    // settings screen applies to its open section, and the one people expect
    // from every contextual action bar on Android.
    BackHandler {
        if (selecting) selectedIds = emptySet() else onBack()
    }
    var inLibrary by remember(series.id) { mutableStateOf(Library.contains(context, series.id)) }

    /**
     * Where the Start/Resume button goes. Recomputed on readTick so marking
     * something read moves the target without reopening the screen.
     *
     * **This used to be the first unread chapter, and that is not what Resume
     * means.** On an imported library, read state arrives from the backup and is
     * routinely full of holes — a series read to chapter 50 with a few early
     * ones never marked leaves "first unread" pointing at chapter 3, so a button
     * labelled Resume opened the beginning of the series.
     *
     * The furthest chapter with *any* progress is the honest anchor: part-way
     * through it means resume there, finished means the next one along. Progress
     * is read state or a stored page, the same pair `anyProgress` uses, because
     * a chapter opened and abandoned is progress even though nothing marked it.
     *
     * `History` would be the obvious source and cannot answer this: it is capped
     * at 40 entries for the whole app, so on a 3575-entry library almost no
     * series has one. `ReadState` and `savedPage` are per chapter and uncapped.
     *
     * One pass, and the read flags are kept rather than re-queried — this runs
     * over every chapter of the series and both lookups are a prefs read each.
     */
    val resumeIndex = remember(chapters, readTick, sourceId) {
        val read = BooleanArray(chapters.size)
        var lastTouched = -1
        chapters.forEachIndexed { index, chapter ->
            val key = chapterKeyOf(sourceId, chapter)
            read[index] = ReadState.isRead(context, key)
            if (read[index] || savedPage(context, key) > 0) lastTouched = index
        }
        when {
            // Started and not finished: this is the chapter, and the reader's
            // own saved page puts you back on the right page of it.
            lastTouched >= 0 && !read[lastTouched] -> lastTouched
            // Nothing touched, or the furthest one is done: the next unread
            // after it, falling back to the first unread anywhere for a series
            // whose later chapters were read out of order.
            else -> {
                val from = lastTouched + 1
                (from until chapters.size).firstOrNull { !read[it] }
                    ?: read.indices.firstOrNull { !read[it] }
                    ?: -1
            }
        }
    }
    val downloadedCount = remember(chapters, downloadTick) {
        chapters.count { Downloads.isComplete(context, it.id) }
    }
    var coverOpen by remember(series.id) { mutableStateOf(false) }

    // The one place in the app that has a chapter list, its source and the
    // series id in hand at the same time, which is exactly what the index needs
    // and the reason it's written from here rather than from the fetch in
    // YomuApp. Keyed on readTick as well as the list, so marking chapters read
    // — here or by finishing one in the reader, which bumps the same tick on the
    // way out — corrects the stored count rather than leaving it to drift until
    // the next fetch.
    //
    // Gated on library membership: this store only feeds the library screen, and
    // recording every series merely *browsed* would grow a JSON that gets
    // rewritten in full, for entries nothing will ever read.
    LaunchedEffect(chapters, readTick, inLibrary, sourceId) {
        if (!inLibrary || chapters.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            SeriesIndex.record(context, sourceId, series.id, chapters)
        }
    }

    // Seeded before the list below composes, not in an effect: the LazyColumn
    // builds its state as it composes, so a position belonging to a different
    // series has to be gone by then rather than one frame later.
    scroll.sync(series.id)

    val anyProgress = remember(chapters, readTick, sourceId) {
        chapters.any {
            val k = chapterKeyOf(sourceId, it)
            ReadState.isRead(context, k) || savedPage(context, k) > 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberRestoredListState(scroll, "series", series.id),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Box {
                    // Cover as a faded backdrop, then a gradient down to the
                    // background so the text at the bottom stays readable.
                    if (series.cover != null) {
                        AsyncImage(
                            model = series.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.20f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                )
                            )
                    )

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BackButton(onBack)
                        }

                        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                            CoverImage(
                                cover = series.cover,
                                title = series.title,
                                modifier = Modifier
                                    .width(108.dp)
                                    .aspectRatio(0.7f)
                                    // Only when there's something to enlarge —
                                    // CoverImage draws initials on a blank when
                                    // the source gave no cover, and opening a
                                    // viewer onto that is a black screen and a
                                    // back press.
                                    .then(
                                        if (series.cover != null)
                                            Modifier.clickable { coverOpen = true }
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                // Tapping the title searches every source for it,
                                // which is how you find the same series on a
                                // source that is still updating it. Reuses
                                // `onGlobalSearchTag` rather than growing a
                                // parameter: it already clears `activeSeries`,
                                // already routes to the results, and already has
                                // `tagSearchReturn` restoring this screen on the
                                // way back — the whole trap 0.60 shipped and 0.61
                                // fixed. A second callback doing the same thing
                                // would be a second chance to get that wrong.
                                Text(
                                    series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable { onGlobalSearchTag(series.title) }
                                )
                                // Author and artist on their own lines, each
                                // searchable on its own. 0.96 had to guess at a
                                // split because the adapter joined them; it
                                // doesn't any more, so the value shown is the
                                // value searched.
                                //
                                // The artist line is dropped when it repeats the
                                // author, which is what most sources report — a
                                // second identical name reads as a rendering bug
                                // rather than as information.
                                val credits = listOfNotNull(
                                    series.author?.takeIf { it.isNotBlank() }
                                        ?.let { "Story" to it },
                                    series.artist?.takeIf {
                                        it.isNotBlank() && !it.equals(series.author, true)
                                    }?.let { "Art" to it }
                                )
                                credits.forEach { (role, name) ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        // Unlabelled when there is only one
                                        // name: "Story" on a series with no
                                        // separate artist is a claim the source
                                        // never made.
                                        if (credits.size > 1) "$role \u00b7 $name" else name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { onGlobalSearchTag(name) }
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOfNotNull(series.status, sourceName.ifBlank { null })
                                        .joinToString(" \u2022 "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SeriesAction(
                                icon = if (inLibrary) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                label = if (inLibrary) "In library" else "Add to library",
                                tint = if (inLibrary) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    if (inLibrary) {
                                        Library.remove(context, series.id)
                                        inLibrary = false
                                        onLibraryChanged()
                                    } else {
                                        showAddToLibrary = true
                                    }
                                }
                            )
                            if (inLibrary) {
                                SeriesAction(
                                    icon = Icons.Default.Edit,
                                    label = "Categories",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { showCategories = true }
                                )
                            }
                            if (canDownload && chapters.isNotEmpty()) {
                                SeriesAction(
                                    icon = if (downloadingAll) Icons.Default.Clear
                                    else Icons.Default.KeyboardArrowDown,
                                    label = if (downloadingAll) "Stop" else "Download all",
                                    tint = if (downloadingAll) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = {
                                        if (downloadingAll) onCancelDownloads() else onDownloadAll()
                                    }
                                )
                                if (downloadedCount > 0) {
                                    SeriesAction(
                                        icon = Icons.Default.Delete,
                                        label = "Delete ($downloadedCount)",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { onDeleteDownloads() }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (!series.description.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { descriptionExpanded = !descriptionExpanded }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            series.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            if (descriptionExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (descriptionExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            if (series.genres.isNotEmpty()) {
                item {
                    // Scrolling row rather than a wrapping one: FlowRow is still
                    // an experimental layout API on this Compose version.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        series.genres.forEach { genre ->
                            // A tag was decoration until now — a chip with an
                            // empty onClick. What it actually is is a query, so
                            // tapping one offers the three things you can do
                            // with a query rather than picking one and hoping.
                            Box {
                                var tagMenu by remember(genre) { mutableStateOf(false) }
                                SuggestionChip(
                                    onClick = { tagMenu = true },
                                    label = { Text(genre) }
                                )
                                DropdownMenu(
                                    expanded = tagMenu,
                                    onDismissRequest = { tagMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Search $sourceName") },
                                        onClick = {
                                            tagMenu = false
                                            onSearchTag(genre)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Global search") },
                                        onClick = {
                                            tagMenu = false
                                            onGlobalSearchTag(genre)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy to clipboard") },
                                        onClick = {
                                            tagMenu = false
                                            clipboard.setText(AnnotatedString(genre))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                // Same test the browse screen uses: the failure arrives as an
                // already-formatted string from `Response.failureMessage()`,
                // which is the one place that can see the Cloudflare headers.
                //
                // Worth having here and not only on browse, because these are
                // different requests that fail separately. A source can list its
                // catalogue from cached clearance and then 403 on the chapter
                // list, which left the only way to solve it on a screen the user
                // had already moved past.
                val challengeable = onSolveChallenge != null &&
                    error?.contains("Cloudflare", ignoreCase = true) == true
                ErrorBanner(
                    error = error,
                    actionLabel = if (challengeable) "Open in WebView" else null,
                    onAction = if (challengeable) onSolveChallenge else null
                )
                Text(
                    if (chapters.size == 1) "1 chapter" else "${chapters.size} chapters",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            itemsIndexed(chapters) { index, ch ->
                val key = chapterKeyOf(sourceId, ch)
                val read = remember(key, readTick) { ReadState.isRead(context, key) }
                val resume = remember(key, readTick) { savedPage(context, key) }
                // Swipe either way toggles read. `confirmValueChange` returning
                // false is what makes this an *action* rather than a dismissal:
                // the box refuses the new value and animates back, so the row
                // stays where it is and the list never loses an item.
                //
                // Disabled while selecting. A horizontal drag during multi-select
                // is someone scrolling a list they are picking from, and marking
                // one chapter read out from under a selection is not what it
                // means.
                val swipeState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        // Ignored while selecting: a horizontal drag then is
                        // someone scrolling a list they are picking from, and
                        // marking one chapter read out from under a selection is
                        // not what that means. Guarded here rather than with
                        // `gesturesEnabled`, which is a parameter this Compose
                        // version may not have — and with no compiler in the
                        // loop, an uncertain parameter costs a CI round trip.
                        if (!selecting && value != SwipeToDismissBoxValue.Settled) {
                            onSetRead(listOf(ch), !read)
                        }
                        false
                    },
                    // Most of the row's width, against a default of half.
                    // Reported as firing while scrolling a long chapter list,
                    // where a fling carries enough sideways drift to cross a
                    // shallow threshold — and as not firing when meant, which is
                    // the same complaint from the other side: an inconsistent
                    // trigger point reads as both. A deliberate swipe crosses
                    // this and an incidental one doesn't.
                    //
                    // This is a tuning change, not a diagnosis. If it still
                    // misfires the next thing to suspect is velocity-based
                    // settling rather than distance, and the answer there is a
                    // hand-rolled drag detector rather than SwipeToDismissBox.
                    positionalThreshold = { distance -> distance * 0.75f }
                )
                SwipeToDismissBox(
                    state = swipeState,
                    backgroundContent = {
                        // Reads as the outcome, not the gesture: swiping an
                        // unread chapter says "Mark read", and the same swipe on
                        // a read one says the opposite. Centred on both edges so
                        // it is visible whichever way the finger goes.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                if (read) "Mark unread" else "Mark read",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                ) {
                ListItem(
                    headlineContent = {
                        Text(
                            ch.name,
                            color = if (read) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = READ_DIM)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    supportingContent = {
                        val bits = listOfNotNull(
                            formatChapterDate(ch.dateUploaded),
                            ch.scanlator,
                            // No "Read" label: the whole row dims instead. A
                            // word costs a line of subtitle on every finished
                            // chapter to say what the colour already says, and on
                            // a 171-chapter series that's most of the screen.
                            // The saved page goes with it — a chapter that's
                            // been read doesn't need a bookmark.
                            if (!read && resume > 0) "Page ${resume + 1}" else null
                        )
                        if (bits.isNotEmpty()) {
                            Text(
                                bits.joinToString(" \u2022 "),
                                color = when {
                                    read -> MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = READ_DIM)
                                    resume > 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (canDownload) {
                                val percent = downloadProgress[ch.id]
                                val downloaded = remember(ch.id, downloadTick) {
                                    Downloads.isComplete(context, ch.id)
                                }
                                // Queued and downloading are different states now
                                // that a queue exists, and read straight off it:
                                // a chapter can sit waiting behind twenty others.
                                val active = DownloadQueue.activeId == ch.id
                                val queued = DownloadQueue.isQueued(ch.id)
                                when {
                                    active -> Text(
                                        if (percent != null && percent > 0) "$percent%"
                                        else "\u2026",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    queued -> Text(
                                        "Queued",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // The bin says what the tap does. State is
                                    // still legible — an arrow means not on
                                    // disk, a bin means it is — but the glyph
                                    // now names the action rather than leaving
                                    // it to be discovered. Primary rather than
                                    // error: on a 171-chapter list every saved
                                    // row would otherwise be a red mark, and the
                                    // confirmation below is the real guard.
                                    downloaded -> IconButton(
                                        onClick = { confirmDeleteChapter = ch }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Downloaded \u2014 delete",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    else -> IconButton(onClick = { onDownload(ch) }) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Download",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            // No read control on the row. The dimming already
                            // says whether a chapter is read, and a second mark
                            // beside it was the same fact twice — on a long list
                            // that's a column of icons carrying no information.
                            // Changing it is a long-press away, where the batch
                            // actions live.
                        }
                    },
                    // Tinted rather than checkboxed: adding a checkbox column
                    // shifts every row sideways the moment selection starts,
                    // which makes the list jump under the finger that just
                    // long-pressed it.
                    colors = if (ch.id in selectedIds) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selecting) selectedIds = selectedIds.toggle(ch.id)
                            else onOpen(index)
                        },
                        onLongClick = { selectedIds = selectedIds.toggle(ch.id) }
                    )
                )
                }
                HorizontalDivider()
            }

            // Clearance so the last row isn't trapped under the button.
            item { Spacer(Modifier.height(88.dp)) }
        }

        if (selecting) {
            ChapterSelectionBar(
                count = selectedChapters.size,
                canDownload = canDownload,
                onSelectAll = { selectedIds = chapters.map { it.id }.toSet() },
                onClear = { selectedIds = emptySet() },
                onDownload = {
                    selectedChapters.forEach { onDownload(it) }
                    selectedIds = emptySet()
                },
                onRead = {
                    onSetRead(selectedChapters, true)
                    selectedIds = emptySet()
                },
                onUnread = {
                    onSetRead(selectedChapters, false)
                    selectedIds = emptySet()
                },
                // Asks first, and the selection is kept until it's answered —
                // the dialog needs it, and cancelling should leave the bar
                // exactly as it was.
                onDelete = { confirmDeleteSelection = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Hidden while selecting: it sits exactly where the action bar goes, and
        // "Resume" is not what anyone reaches for mid-selection.
        if (chapters.isNotEmpty() && !selecting) {
            ExtendedFloatingActionButton(
                onClick = { onOpen(if (resumeIndex >= 0) resumeIndex else 0) },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                text = { Text(if (anyProgress) "Resume" else "Start") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    // Confirmed rather than immediate. The row itself opens the chapter, so a
    // control inside it that deletes on the first tap is one slipped thumb away
    // from a re-download — and unlike "Delete all", this button sits next to the
    // thing people are aiming at.
    val pendingDelete = confirmDeleteChapter
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChapter = null },
            title = { Text("Delete this download?") },
            text = {
                Text(
                    "\u201c${pendingDelete.name}\u201d is removed from storage. The " +
                        "chapter stays in the list and can be saved again, and your " +
                        "read mark and place in it are untouched."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteChapter(pendingDelete)
                    confirmDeleteChapter = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChapter = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteSelection) {
        // Counted, not just "the selected ones". Selecting forty and deleting is
        // one tap further than selecting one, so the number is the thing worth
        // reading back — and it's the count of chapters actually on disk, since
        // the rest of a selection is a no-op and shouldn't inflate it.
        val onDisk = selectedChapters.count { ch ->
            remember(ch.id, downloadTick) { Downloads.isComplete(context, ch.id) }
        }
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text(if (onDisk == 1) "Delete 1 download?" else "Delete $onDisk downloads?") },
            text = {
                Text(
                    if (onDisk == 0) {
                        "None of the selected chapters are downloaded, so there's " +
                            "nothing to remove."
                    } else {
                        "Removed from storage. The chapters stay in the list and can " +
                            "be saved again, and your read marks and places in them " +
                            "are untouched."
                    }
                )
            },
            confirmButton = {
                Button(
                    enabled = onDisk > 0,
                    onClick = {
                        selectedChapters.forEach { onDeleteChapter(it) }
                        selectedIds = emptySet()
                        confirmDeleteSelection = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) { Text("Cancel") }
            }
        )
    }

    if (showCategories) {
        CategoryAssignDialog(seriesId = series.id, onDismiss = { showCategories = false })
    }

    if (showAddToLibrary) {
        AddToLibraryDialog(
            series = series,
            sourceId = sourceId,
            onDismiss = { showAddToLibrary = false },
            onSaved = {
                showAddToLibrary = false
                inLibrary = true
                onLibraryChanged()
            }
        )
    }

    if (coverOpen && series.cover != null) {
        CoverViewer(cover = series.cover, onDismiss = { coverOpen = false })
    }
}

/**
 * The series cover, full screen and zoomable.
 *
 * A `Dialog` rather than a branch of the routing chain in `YomuApp`. That chain
 * encodes real navigation rules in an if/else and is delicate enough already —
 * §5 has three separate bugs from state living in the wrong side of it — and
 * this needs none of what a branch buys: nothing below it has to know it's open,
 * it holds no state worth surviving, and a dialog's own back handling dismisses
 * it without touching the series underneath.
 *
 * `usePlatformDefaultWidth = false` is what makes it full-bleed; without it a
 * dialog is inset to the platform's alert width and a cover in the middle of it
 * is barely larger than the one on the screen behind.
 *
 * Zoomable because a cover is one of the few images in this app worth looking at
 * closely, and `telephoto` is already a dependency the reader leans on — the
 * same call that made paged zoom cheap makes this nearly free.
 */
@Composable
private fun CoverViewer(cover: Any?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            ZoomableAsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                // A zoomable image consumes its own pointer events, so a tap
                // detector wrapped around it never fires — the same thing that
                // moved the reader's show-controls tap off the pager and onto
                // the pages. Tapping the image is the way out.
                onClick = { onDismiss() }
            )
            // The scrim is black in both themes, so the arrow can't take its
            // colour from the scheme — on a light theme that's near-black on
            // black. Overridden rather than a second BackButton, so this stays
            // the one back affordance the app uses everywhere.
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                BackButton(onDismiss)
            }
        }
    }
}

/** Adds or removes one id. Written out because `Set` has no toggle. */
private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * The contextual bar shown while chapters are selected.
 *
 * Icon over label, via the same [SeriesAction] the series header uses, so the
 * two rows of actions on this screen look like the same app.
 *
 * The labels stay because the icons can't carry it alone. `material-icons-core`
 * has nothing for mark-as-read or mark-as-unread — §7's icon debt, again — so
 * read borrows `Check` and unread borrows `Clear`, and `Check` already means
 * "downloaded" three columns to the left. Bare glyphs would be a guess; with a
 * word under them they're just a target. Delete is last and coloured, so the one
 * action that can't be undone isn't adjacent to the one hit most.
 */
@Composable
private fun ChapterSelectionBar(
    count: Int,
    canDownload: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                }
                Text(
                    "$count selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSelectAll) { Text("All") }
            }
            // Spread, not scrolled. Four actions fit a phone width comfortably
            // and the header row directly above this one is already distributed
            // — bunching these at the left made the bar read as an overflow that
            // had more to show.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (canDownload) {
                    SeriesAction(
                        icon = Icons.Default.KeyboardArrowDown,
                        label = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onDownload
                    )
                }
                SeriesAction(
                    icon = Icons.Default.Check,
                    label = "Read",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onRead
                )
                SeriesAction(
                    icon = Icons.Default.Clear,
                    label = "Unread",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onUnread
                )
                SeriesAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

/**
 * How far a read chapter's text fades.
 *
 * 0.45 rather than something subtler because this is now the *only* signal that
 * a chapter has been read, and it has to survive a bright phone outdoors.
 */
private const val READ_DIM = 0.45f


private const val KEY_BROWSE_VIEW = "browse_view"

/**
 * How the browse grid draws a result.
 *
 * Mihon's three, and they answer different questions: Comfortable is for
 * reading titles you don't know, Compact fits roughly a third more covers on
 * screen for a library you recognise by art, and List is the only one that shows
 * a long title in full. The choice is global rather than per-source \u2014 it's
 * about the screen and the eyes in front of it, not about the catalogue.
 */
internal enum class BrowseView(val key: String, val label: String) {
    COMFORTABLE("comfortable", "Comfortable grid"),
    COMPACT("compact", "Compact grid"),
    LIST("list", "List");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: COMFORTABLE
    }
}

/** Cover with the title underneath it. */
@Composable
private fun ComfortableCell(series: Series, onOpen: (Series) -> Unit) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable { onOpen(series) }
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        Text(
            text = series.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Cover with the title over it, under a scrim. */
@Composable
private fun CompactCell(series: Series, onOpen: (Series) -> Unit) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .clickable { onOpen(series) }
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        // The scrim isn't decoration. Covers are arbitrary artwork and a title
        // drawn straight onto a pale one is unreadable; the gradient is what
        // makes white text safe over anything.
        Text(
            text = series.title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** One row: small cover, full title. */
@Composable
private fun ListRow(series: Series, onOpen: (Series) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(series) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            cover = series.cover,
            title = series.title,
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(0.7f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = series.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Today" / "Yesterday" / a short date, or null when the source gave no date. */
internal fun formatChapterDate(millis: Long): String? {
    if (millis <= 0L) return null
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val startOfToday = now - (now % day)
    return when {
        millis >= startOfToday -> "Today"
        millis >= startOfToday - day -> "Yesterday"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
