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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val isAnimeSource = sourceId.startsWith("aniyomi:")
    var playbackStateTick by remember(series.id) { mutableIntStateOf(0) }
    DisposableEffect(context, series.id) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) playbackStateTick++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val effectiveReadTick = readTick + playbackStateTick
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
    val resumeIndex = remember(chapters, effectiveReadTick, sourceId) {
        val read = BooleanArray(chapters.size)
        var lastTouched = -1
        chapters.forEachIndexed { index, chapter ->
            val key = chapterKeyOf(sourceId, chapter)
            read[index] = ReadState.isRead(context, key)
            val hasPartialProgress = if (isAnimeSource) {
                VideoPlaybackProgress.position(context, key) > 0L
            } else {
                savedPage(context, key) > 0
            }
            if (read[index] || hasPartialProgress) lastTouched = index
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
    val visible = remember(chapters, effectiveReadTick, downloadTick, optionsTick, sourceId) {
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
    LaunchedEffect(chapters, effectiveReadTick, inLibrary, sourceId) {
        if (!inLibrary || chapters.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            SeriesIndex.record(context, sourceId, series.id, chapters)
        }
    }

    // Seeded before the list below composes, not in an effect: the LazyColumn
    // builds its state as it composes, so a position belonging to a different
    // series has to be gone by then rather than one frame later.
    scroll.sync(series.id)

    val anyProgress = remember(chapters, effectiveReadTick, sourceId) {
        chapters.any {
            val k = chapterKeyOf(sourceId, it)
            ReadState.isRead(context, k) || if (isAnimeSource) {
                VideoPlaybackProgress.position(context, k) > 0L
            } else {
                savedPage(context, k) > 0
            }
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
                SeriesHero(
                    series = series,
                    sourceName = sourceName,
                    inLibrary = inLibrary,
                    canDownload = canDownload,
                    hasChapters = chapters.isNotEmpty(),
                    downloadingAll = downloadingAll,
                    downloadedCount = downloadedCount,
                    onOpenCover = { coverOpen = true },
                    onGlobalSearch = onGlobalSearchTag,
                    onLibraryAction = {
                        if (inLibrary) {
                            Library.remove(context, series.id)
                            inLibrary = false
                            onLibraryChanged()
                        } else {
                            showAddToLibrary = true
                        }
                    },
                    onCategories = { showCategories = true },
                    onToggleAllDownloads = {
                        if (downloadingAll) onCancelDownloads() else onDownloadAll()
                    },
                    onDeleteDownloads = onDeleteDownloads,
                )
            }

            item {
                SeriesDescriptionAndGenres(
                    series = series,
                    expanded = descriptionExpanded,
                    onToggleExpanded = {
                        descriptionExpanded = !descriptionExpanded
                    },
                    sourceName = sourceName,
                    onSearchTag = onSearchTag,
                    onGlobalSearchTag = onGlobalSearchTag,
                )
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
                        chapters.isEmpty() ->
                            if (isAnimeSource) "This source returned no episodes"
                            else "This source returned no chapters"
                        visible.size != chapters.size ->
                            if (isAnimeSource) {
                                "${visible.size} of ${chapters.size} episodes"
                            } else {
                                "${visible.size} of ${chapters.size} chapters"
                            }
                        chapters.size == 1 ->
                            if (isAnimeSource) "1 episode" else "1 chapter"
                        else ->
                            if (isAnimeSource) "${chapters.size} episodes"
                            else "${chapters.size} chapters"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            itemsIndexed(visible) { _, ch ->
                SeriesChapterRow(
                    chapter = ch,
                    sourceId = sourceId,
                    readTick = effectiveReadTick,
                    isAnime = isAnimeSource,
                    downloadTick = downloadTick,
                    progress = downloadProgress[ch.id],
                    canDownload = canDownload,
                    selecting = selecting,
                    selected = ch.id in selectedIds,
                    chapterDisplay = chapterDisplay,
                    onSetRead = { read ->
                        onSetRead(listOf(ch), read)
                    },
                    onSetBookmarked = { bookmarked ->
                        onSetBookmarked(listOf(ch), bookmarked)
                    },
                    onOpen = { onOpen(ch.id) },
                    onToggleSelected = {
                        selectedIds = selectedIds.toggle(ch.id)
                    },
                    onDeleteChapter = {
                        confirmDeleteChapter = ch
                    },
                    onDownload = { onDownload(ch) },
                )
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

                SeriesTopBar(
            title = series.title,
            canDownload = canDownload,
            chapters = chapters,
            visibleChapters = visible,
            sourceId = sourceId,
            onDownload = onDownload,
            filtersActive = filtersActive,
            onOpenChapterOptions = { showChapterOptions = true },
            onRefresh = onRefresh,
            onFindVideos = onFindVideos,
            inLibrary = inLibrary,
            onEditCategories = { showCategories = true },
            onMigrate = onMigrate,
            seriesUrl = seriesUrl,
            onBack = onBack,
            barAlpha = barAlpha,
            modifier = Modifier.align(Alignment.TopCenter),
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
