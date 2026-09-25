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
    val isAnimeSource = sourceId.isAnimeExtensionSourceId()
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

    val resumeIndex = remember(chapters, effectiveReadTick, sourceId) {
        seriesResumeIndex(
            context = context,
            chapters = chapters,
            sourceId = sourceId,
            isAnimeSource = isAnimeSource,
        )
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
        seriesHasAnyProgress(
            context = context,
            chapters = chapters,
            sourceId = sourceId,
            isAnimeSource = isAnimeSource,
        )
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

    SeriesContent(
        series = series,
        sourceId = sourceId,
        sourceName = sourceName,
        chapters = chapters,
        visibleChapters = visible,
        chaptersFetched = chaptersFetched,
        loading = loading,
        error = error,
        isAnimeSource = isAnimeSource,
        inLibrary = inLibrary,
        canDownload = canDownload,
        downloadingAll = downloadingAll,
        downloadedCount = downloadedCount,
        downloadTick = downloadTick,
        downloadProgress = downloadProgress,
        effectiveReadTick = effectiveReadTick,
        chapterDisplay = chapterDisplay,
        selecting = selecting,
        selectedIds = selectedIds,
        selectedChapters = selectedChapters,
        filtersActive = filtersActive,
        resumeIndex = resumeIndex,
        anyProgress = anyProgress,
        listState = listState,
        barAlpha = barAlpha,
        seriesUrl = seriesUrl,
        onOpenCover = { coverOpen = true },
        onGlobalSearchTag = onGlobalSearchTag,
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
        descriptionExpanded = descriptionExpanded,
        onToggleDescriptionExpanded = {
            descriptionExpanded = !descriptionExpanded
        },
        onSearchTag = onSearchTag,
        onSolveChallenge = onSolveChallenge,
        onRefresh = onRefresh,
        onSetRead = onSetRead,
        onSetBookmarked = onSetBookmarked,
        onOpen = onOpen,
        onToggleSelected = { chapterId ->
            selectedIds = selectedIds.toggle(chapterId)
        },
        onRequestDeleteChapter = { confirmDeleteChapter = it },
        onDownload = onDownload,
        onSelectAll = { selectedIds = it },
        onClearSelection = { selectedIds = emptySet() },
        onRequestDeleteSelection = { confirmDeleteSelection = true },
        onOpenChapterOptions = { showChapterOptions = true },
        onFindVideos = onFindVideos,
        onMigrate = onMigrate,
        onBack = onBack,
    )

    SeriesAuxiliaryDialogs(
        series = series,
        sourceId = sourceId,
        confirmDeleteChapter = confirmDeleteChapter,
        onDismissDeleteChapter = { confirmDeleteChapter = null },
        onDeleteChapter = {
            onDeleteChapter(it)
            confirmDeleteChapter = null
        },
        confirmDeleteSelection = confirmDeleteSelection,
        selectedChapters = selectedChapters,
        downloadTick = downloadTick,
        onDismissDeleteSelection = { confirmDeleteSelection = false },
        onDeleteSelection = {
            selectedChapters.forEach(onDeleteChapter)
            selectedIds = emptySet()
            confirmDeleteSelection = false
        },
        showCategories = showCategories,
        onDismissCategories = { showCategories = false },
        showAddToLibrary = showAddToLibrary,
        onDismissAddToLibrary = { showAddToLibrary = false },
        onSavedToLibrary = {
            showAddToLibrary = false
            inLibrary = true
            onLibraryChanged()
        },
        showChapterOptions = showChapterOptions,
        onDismissChapterOptions = { showChapterOptions = false },
        onChapterOptionsChanged = { optionsTick++ },
        coverOpen = coverOpen,
        onDismissCover = { coverOpen = false },
    )

}
