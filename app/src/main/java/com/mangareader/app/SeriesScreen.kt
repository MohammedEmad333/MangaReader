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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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

// ---------- prefs: the same store every other object in this package uses ----------

// ---------- series ----------

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
internal fun SeriesScreen(
    series: Series,
    chapters: List<Chapter>,
    /**
     * Whether a chapter fetch has COMPLETED for this series.
     *
     * An empty [chapters] means three things — nothing fetched yet, a fetch
     * that failed, and a fetch that genuinely returned nothing — and this
     * screen used to assert the third whenever it saw the first, announcing
     * "This source returned no chapters" while the request was still running.
     */
    chaptersFetched: Boolean,
    /**
     * Scans a chapter's page for video urls. See Source.scanVideos.
     *
     * Takes the chapter rather than picking one upstream: the caller cannot see
     * the sort or the filter, so "the first chapter" up there meant the first
     * of the RAW list, which on a descending sort is the last one drawn. This
     * passes what is actually at the top of the list being looked at.
     */
    onFindVideos: (Chapter) -> Unit,
    sourceId: String,
    sourceName: String,
    canDownload: Boolean,
    downloadProgress: Map<String, DownloadQueue.DownloadProgress>,
    downloadTick: Int,
    downloadingAll: Boolean,
    onDownload: (Chapter) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownloads: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onSetRead: (List<Chapter>, Boolean) -> Unit,
    /** Bookmarks a batch. Independent of read state — see `Bookmarks`. */
    onSetBookmarked: (List<Chapter>, Boolean) -> Unit,
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
    /**
     * Opens a chapter **by id**.
     *
     * It took a list index until 0.110, which was safe only while this screen
     * drew `chapters` unchanged: `YomuApp.openChapter` indexes its own
     * `chapterList`, and the two lists were the same list. Filtering and sorting
     * ended that. An index from the drawn list now means a different chapter on
     * the other side, and nothing would report it — the wrong chapter opens, the
     * reader's Prev/Next walk the wrong order, and read state lands on the wrong
     * row. Same shape as the 0.104 `headRows` bug, in a second place.
     *
     * So the id crosses the boundary and `YomuApp` resolves it. The full list
     * stays canonical; this screen only decides what to *draw*.
     */
    onOpen: (String) -> Unit,
    onLibraryChanged: () -> Unit,
    /** Runs [String] as a search of this series' own source. */
    onSearchTag: (String) -> Unit,
    /** Runs [String] across every searchable source. */
    onGlobalSearchTag: (String) -> Unit,
    /** Opens the target picker to move this (library) series to another source. */
    onMigrate: () -> Unit,
    onSolveChallenge: (() -> Unit)?,
    /** Re-fetches the chapter list without disturbing where Back goes. */
    onRefresh: () -> Unit,
    /** The series' page on its source's site, or null when there isn't one. */
    seriesUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // The clipboard moved into GenreChips with the chips themselves.
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
    var coverOpen by remember(series.id) { mutableStateOf(false) }
    var showChapterOptions by remember { mutableStateOf(false) }
    var showDownloadMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    // Bumped when the sheet writes a pref, so the derived list below recomputes.
    // The prefs are the store; this is only the signal that they moved.
    var optionsTick by remember { mutableIntStateOf(0) }

    /**
     * What the list below draws — filtered and sorted. **Not** what anything
     * indexes: see `onOpen`.
     *
     * Keyed on both ticks because the filters read read-state and disk, so
     * finishing a chapter or a download changes which rows belong here.
     */
    val visible = remember(chapters, readTick, downloadTick, optionsTick, sourceId) {
        visibleChapters(context, chapters, sourceId)
    }
    val chapterDisplay = remember(optionsTick) { ChapterPrefs.display(context) }
    val filtersActive = remember(optionsTick) { ChapterPrefs.anyFilterActive(context) }
    val downloadedCount = remember(chapters, downloadTick) {
        chapters.count { Downloads.isComplete(context, it.id) }
    }

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

    // Hoisted above the Box because the top bar and the list both read it. Built
    // inline at the LazyColumn until 0.109, which was fine while nothing else
    // needed it — construct it twice and the bar gets a state that never
    // scrolls, and the symptom is a bar that simply never fades in, which reads
    // as the alpha arithmetic being wrong rather than as two objects.
    val listState = rememberRestoredListState(scroll, "series", series.id)

    // dp converted once, out here: `firstVisibleItemScrollOffset` is in pixels,
    // so a raw pixel constant would fade over a third of the distance on a
    // high-density phone that it does on a low-density one.
    val fadeOverPx = with(LocalDensity.current) { TOP_BAR_FADE_OVER.toPx() }

    /**
     * How opaque the top bar is, from how far the header has scrolled.
     *
     * The bar sits *over* the cover backdrop rather than above it, so at rest it
     * is invisible and only the back arrow shows against the art — which is what
     * the screen looked like before it had a bar at all. It fades in as the
     * cover leaves, so the title arrives exactly when the thing it names is
     * gone. Modelled on SY's `MangaToolbar`, which takes the same two alphas
     * from its own scroll state.
     *
     * `derivedStateOf`, not a plain read: `firstVisibleItemScrollOffset` changes
     * every frame of a drag, and reading it directly would recompose the whole
     * screen — a 171-row chapter list included — on every pixel.
     *
     * [TOP_BAR_FADE_OVER] is deliberately shorter than the header: the bar wants
     * to be solid before the chapter rows reach it, not when the header ends.
     */
    val barAlpha by remember(listState, fadeOverPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / fadeOverPx).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Pull down to re-fetch the chapter list — the same `onRefresh` the
        // Options menu calls, so there is one refresh path rather than two.
        //
        // `isRefreshing` is the app-wide `loading` flag, gated on already having
        // chapters. That gate is doing real work: without it the indicator would
        // appear on every ordinary open, because opening a series sets the same
        // flag. It is also why this is not simply `loading` — that flag is
        // written by six launch blocks in `YomuApp`, and 0.89 is the release
        // that had to stop the reader sharing it. Here the blast radius is a
        // spinner rather than a page of failures, so it is not worth a second
        // flag; if it ever spins when it shouldn't, this is the line.
        //
        // The LazyColumn body below is deliberately NOT re-indented under this
        // wrapper: Kotlin doesn't care, and re-indenting 490 lines would bury a
        // four-line change in a diff nobody could read.
        PullToRefreshBox(
            isRefreshing = loading && chapters.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            state = listState,
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
                        // Where the back button used to sit. It is in the top bar
                        // now, which is drawn over this Box rather than above it,
                        // so the space still has to be reserved or the cover row
                        // slides under the bar.
                        Spacer(Modifier.height(TOP_BAR_HEIGHT))

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
                                    else Icons.Default.Download,
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
            } else if (series.genres.isNotEmpty()) {
                item {
                    // The chevron used to live only inside the description
                    // block, so a series with tags and NO description had no way
                    // to expand them — a control that could not reach a state,
                    // which is the same shape as 0.154's pause loop. Rendered
                    // here instead, above the tags it governs.
                    Icon(
                        if (descriptionExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (descriptionExpanded) {
                            "Collapse tags"
                        } else {
                            "Expand tags"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { descriptionExpanded = !descriptionExpanded }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            if (series.genres.isNotEmpty()) {
                item {
                    // COLLAPSED: a scrolling row, so a long tag list does not
                    // push the chapter list off the screen. EXPANDED: a wrapping
                    // one, showing every tag at once — which is the whole point
                    // of the chevron, and was the report: tags past the right
                    // edge were reachable only by a horizontal drag nothing
                    // advertised.
                    //
                    // FlowRow is still experimental on this Compose version, which
                    // is why the collapsed row does not use it. Opted into rather
                    // than worked around, because chip widths vary and chunking
                    // into fixed rows leaves ragged gaps.
                    val tagModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                    if (descriptionExpanded) {
                        FlowRow(
                            modifier = tagModifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GenreChips(
                                genres = series.genres,
                                sourceName = sourceName,
                                onSearchTag = onSearchTag,
                                onGlobalSearchTag = onGlobalSearchTag
                            )
                        }
                    } else {
                        Row(
                            modifier = tagModifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GenreChips(
                                genres = series.genres,
                                sourceName = sourceName,
                                onSearchTag = onSearchTag,
                                onGlobalSearchTag = onGlobalSearchTag
                            )
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
                if (chaptersFetched || chapters.isNotEmpty()) Text(
                    // Says so when rows are hidden. A filtered list that just
                    // reports a smaller number reads as chapters having gone
                    // missing, which is the report this would otherwise produce.
                    //
                    // And zero is words, not a number. "0 chapters" reads as a
                    // count this app measured, which it did not: an extension
                    // whose selector matched nothing returns the same empty list
                    // as a series that genuinely has none, because Jsoup's
                    // select() yields an empty set rather than throwing. Saying
                    // the source returned nothing claims only what is known.
                    // The honest half — telling those two apart at all — needs
                    // the source layer to record that a fetch completed, and is
                    // still open on its own card.
                    when {
                        // Only once there is an answer. Before that the
                        // progress indicator above is the honest thing on
                        // screen, and a sentence claiming a result would not be.
                        chapters.isEmpty() -> "This source returned no chapters"
                        visible.size != chapters.size ->
                            "${visible.size} of ${chapters.size} chapters"
                        chapters.size == 1 -> "1 chapter"
                        else -> "${chapters.size} chapters"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            itemsIndexed(visible) { _, ch ->
                val key = chapterKeyOf(sourceId, ch)
                val read = remember(key, readTick) { ReadState.isRead(context, key) }
                val resume = remember(key, readTick) { savedPage(context, key) }
                val bookmarked = remember(key, readTick) { Bookmarks.isBookmarked(context, key) }
                // `me.saket.swipe`, not Material3's SwipeToDismissBox — which
                // this shipped on twice and which was unreliable both times.
                //
                // The reason is structural rather than a threshold to tune.
                // SwipeToDismissBox exists to *remove* a row, so using it as an
                // action means refusing its own state change on every swipe and
                // hoping it settles back cleanly. This library is built for the
                // other thing: the row springs back by design, the action fires
                // once at the threshold, and the icon tracks the finger.
                //
                // Mihon and TachiyomiSY both use it for exactly this row, which
                // is where the smoothness being compared against comes from.
                val toggleRead = SwipeAction(
                    onSwipe = { if (!selecting) onSetRead(listOf(ch), !read) },
                    icon = {
                        Icon(
                            // Reads as the outcome: a tick to finish an unread
                            // chapter, a cross to undo a finished one.
                            if (read) Icons.Default.Clear else Icons.Default.Check,
                            contentDescription = if (read) "Mark unread" else "Mark read",
                            modifier = Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    // Tells the library this swipe undoes something, which is
                    // what drives its ripple running the other way.
                    isUndo = read
                )
                // Right-to-left is its own action now. Both directions used to
                // mark read, so anyone in the habit of swiping left for that
                // will bookmark instead — worth a line in the release note, and
                // the reason the two carry different container colours rather
                // than only different glyphs.
                //
                // Mihon makes both directions configurable and defaults them to
                // exactly this pair. Not copying the setting: a preference for
                // which of two actions sits on which side is a settings row and
                // a store for a choice nobody has asked to make yet.
                val toggleBookmark = SwipeAction(
                    onSwipe = { if (!selecting) onSetBookmarked(listOf(ch), !bookmarked) },
                    icon = {
                        Icon(
                            // Outcome again, matching the read swipe: a filled
                            // bookmark when the swipe will add one, an outline
                            // when it will take it away.
                            if (bookmarked) Icons.Default.BookmarkBorder
                            else Icons.Default.Bookmark,
                            contentDescription =
                                if (bookmarked) "Remove bookmark" else "Bookmark",
                            modifier = Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    },
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    isUndo = bookmarked
                )
                SwipeableActionsBox(
                    startActions = listOf(toggleRead),
                    endActions = listOf(toggleBookmark),
                    // Deliberately generous. The default 40dp is what made the
                    // Material3 version fire on sideways drift while scrolling
                    // a long chapter list.
                    swipeThreshold = 96.dp,
                    modifier = Modifier.clipToBounds()
                ) {
                ListItem(
                    headlineContent = {
                        Text(
                            chapterLabel(ch, chapterDisplay),
                            color = if (read) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = READ_DIM)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    supportingContent = {
                        // Only walk the folder for a chapter that's actually on
                        // disk. sizeOf memoises per chapter id and the walk is
                        // cheap, but isComplete gates it so a 171-row series
                        // isn't statting 171 absent folders on every recompose.
                        // Keyed on downloadTick so deleting or finishing a
                        // download updates the line — the same tick the trailing
                        // control reads.
                        val sizeLabel = remember(ch.id, downloadTick) {
                            if (Downloads.isComplete(context, ch.id)) {
                                formatBytes(Downloads.sizeOf(context, ch.id))
                            } else null
                        }
                        val bits = listOfNotNull(
                            formatChapterDate(ch.dateUploaded),
                            ch.scanlator,
                            // Size sits with the other chapter facts rather than
                            // by the download control: it describes the chapter,
                            // like its date, and the trailing area is already the
                            // delete target. Present only when downloaded, so an
                            // undownloaded row doesn't carry an empty slot.
                            sizeLabel,
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
                            // Trailing, not leading. A leading icon on only the
                            // bookmarked rows would leave every other title
                            // starting a step further left, and a list whose
                            // text doesn't line up reads as broken rendering
                            // rather than as a marker.
                            //
                            // A marker, not a button: the row already opens the
                            // chapter and the download control is beside it, and
                            // a third tap target on a 171-row list is why the
                            // read control was taken off these rows. Toggling
                            // lives in the long-press bar.
                            if (bookmarked) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = "Bookmarked",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            if (canDownload) {
                                // A percent, not "12 of 36", purely on width:
                                // this is a trailing badge on a chapter row, not
                                // the download queue. The queue is the screen
                                // that has to be precise about which state it is
                                // in; here the ellipsis versus a number is
                                // enough, and null percent covers both "no page
                                // list yet" and "it was empty".
                                val percent = downloadProgress[ch.id]?.percent
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
                                            Icons.Default.Download,
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
                            else onOpen(ch.id)
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
        } // PullToRefreshBox

        // A series can carry a four-figure chapter list, which is the longest
        // scroll in the app after the library itself.
        //
        // This used to pass `visible.size + 3` — the chapters plus the cover
        // header, the count block and the trailing spacer. It went wrong when
        // 0.157 added two more lazy items to this screen (the tag row, and the
        // chevron for a series with tags and no description) and left the `+ 3`
        // alone, so the handle stopped two chapters short and nothing in the
        // code looked wrong. The count comes off the list itself now.
        ListScrollHandle(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        if (selecting) {
            ChapterSelectionBar(
                count = selectedChapters.size,
                canDownload = canDownload,
                // What is on screen, not what exists. Selecting rows a filter
                // is hiding and then deleting them is not what the button looks
                // like it does.
                onSelectAll = { selectedIds = visible.map { it.id }.toSet() },
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
                // One button, and what it does is decided by the selection: if
                // anything in it is not bookmarked, bookmark everything;
                // otherwise clear them all. A per-chapter toggle over a mixed
                // selection would flip half of them the wrong way, which is the
                // rule `onSetRead` already follows for read state.
                bookmarkAdds = selectedChapters.any {
                    !Bookmarks.isBookmarked(context, chapterKeyOf(sourceId, it))
                },
                onBookmark = { adding ->
                    onSetBookmarked(selectedChapters, adding)
                    selectedIds = emptySet()
                },
                // Asks first, and the selection is kept until it's answered —
                // the dialog needs it, and cancelling should leave the bar
                // exactly as it was.
                onDelete = { confirmDeleteSelection = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Last in the Box, so it draws over the list rather than under it. Not
        // Scaffold's `topBar` slot: that insets its content below the bar, and
        // the whole point here is that the cover art runs *behind* a transparent
        // bar. Scaffold would also have meant moving the FAB and the selection
        // bar, both of which align against this Box.
        TopAppBar(
            title = {
                Text(
                    series.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(barAlpha)
                )
            },
            navigationIcon = { BackButton(onBack) },
            actions = {
                if (canDownload && chapters.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showDownloadMenu = true }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download chapters"
                            )
                        }
                        DropdownMenu(
                            expanded = showDownloadMenu,
                            onDismissRequest = { showDownloadMenu = false }
                        ) {
                            DownloadChoice.entries.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.label) },
                                    onClick = {
                                        showDownloadMenu = false
                                        // Over `visible`, so the menu follows
                                        // the sort and filter on screen — the
                                        // same list Select all works on, for
                                        // the same reason.
                                        downloadTargets(context, visible, sourceId, choice)
                                            .forEach(onDownload)
                                    }
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { showChapterOptions = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter, sort and display chapters",
                        // Tinted while a filter is on, the way SY tints its own.
                        // Without it a filtered list is indistinguishable from a
                        // series that simply has fewer chapters than you thought.
                        tint = if (filtersActive) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current
                    )
                }
                Box {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                showOptionsMenu = false
                                onRefresh()
                            }
                        )
                        // Scans ONE chapter — the one at the top of the list as
                        // currently sorted and filtered, which is the one being
                        // looked at. Scanning all of a 200-chapter series would
                        // be 200 requests to answer a question nobody asked.
                        //
                        // Keyed off `visible`, not `chapters`: with a filter on,
                        // the raw list's first entry may not be on screen at
                        // all, and scanning something invisible is how a feature
                        // reports about a page nobody asked about.
                        val scanTarget = visible.firstOrNull()
                        if (scanTarget != null) {
                            DropdownMenuItem(
                                text = { Text("Find videos") },
                                onClick = {
                                    showOptionsMenu = false
                                    onFindVideos(scanTarget)
                                }
                            )
                        }
                        // Only in the library: categories are a library concept
                        // and the dialog writes an assignment for a series that
                        // isn't saved, which nothing would ever read.
                        if (inLibrary) {
                            DropdownMenuItem(
                                text = { Text("Edit categories") },
                                onClick = {
                                    showOptionsMenu = false
                                    showCategories = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Migrate to another source") },
                                onClick = {
                                    showOptionsMenu = false
                                    onMigrate()
                                }
                            )
                        }
                        // Absent rather than disabled when there is no url: a
                        // greyed row invites a tap and explains nothing. Local
                        // folders and any source whose handle didn't survive
                        // simply have nothing to share.
                        if (!seriesUrl.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    showOptionsMenu = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, series.title)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${series.title}\n$seriesUrl"
                                        )
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, "Share series")
                                    )
                                }
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                // Alpha on the container, not on the whole bar: fading the bar
                // itself would take the back arrow with it, and the arrow has to
                // stay hit-testable and visible against the art from the first
                // frame.
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = barAlpha),
                scrolledContainerColor =
                    MaterialTheme.colorScheme.surface.copy(alpha = barAlpha)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        SeriesResumeFab(
            visible = chapters.isNotEmpty() && !selecting,
            chapters = chapters,
            resumeIndex = resumeIndex,
            anyProgress = anyProgress,
            onOpen = onOpen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    SeriesDeleteDialogs(
        chapter = confirmDeleteChapter,
        onDismissChapter = { confirmDeleteChapter = null },
        onDeleteChapter = {
            onDeleteChapter(it)
            confirmDeleteChapter = null
        },
        selectionOpen = confirmDeleteSelection,
        selectedChapters = selectedChapters,
        downloadTick = downloadTick,
        onDismissSelection = { confirmDeleteSelection = false },
        onDeleteSelection = {
            selectedChapters.forEach(onDeleteChapter)
            selectedIds = emptySet()
            confirmDeleteSelection = false
        },
    )

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

    if (showChapterOptions) {
        ChapterOptionsSheet(
            onDismiss = { showChapterOptions = false },
            onChanged = { optionsTick++ }
        )
    }

    if (coverOpen && series.cover != null) {
        CoverViewer(cover = series.cover, onDismiss = { coverOpen = false })
    }
}
