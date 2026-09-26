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
    onDownloadBatch: (List<Chapter>) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownloads: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onDeleteChapters: (List<Chapter>) -> Unit,
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
    /** Opens the Library tab with [String] in its search field. */
    onLibrarySearchTag: (String) -> Unit,
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
    val scope = rememberCoroutineScope()
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
    var confirmDeleteAllDownloads by remember(series.id) { mutableStateOf(false) }
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
    var libraryBusy by remember(series.id) { mutableStateOf(false) }

    var coverOpen by remember(series.id) { mutableStateOf(false) }
    var showChapterOptions by remember { mutableStateOf(false) }
    // Bumped when the sheet writes a pref, so the derived list below recomputes.
    // The prefs are the store; this is only the signal that they moved.
    var optionsTick by remember { mutableIntStateOf(0) }

    val derived = rememberSeriesDerivedState(
        context = context,
        series = series,
        chapters = chapters,
        effectiveReadTick = effectiveReadTick,
        sourceId = sourceId,
        isAnimeSource = isAnimeSource,
        downloadTick = downloadTick,
        optionsTick = optionsTick,
        inLibrary = inLibrary,
        scroll = scroll,
    )
    val resumeIndex = derived.resumeIndex
    val visible = derived.visible
    val chapterDisplay = derived.chapterDisplay
    val filtersActive = derived.filtersActive
    val downloadedCount = derived.downloadedCount
    val anyProgress = derived.anyProgress
    val chapterStateReady = derived.chapterStateReady
    val listState = derived.listState
    val barAlpha = derived.barAlpha

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
        chapterStateReady = chapterStateReady,
        listState = listState,
        barAlpha = barAlpha,
        seriesUrl = seriesUrl,
        onOpenCover = { coverOpen = true },
        onGlobalSearchTag = onGlobalSearchTag,
        onLibraryAction = {
            if (!libraryBusy) {
                if (inLibrary) {
                    val appContext = context.applicationContext
                    libraryBusy = true
                    scope.launch {
                        val removed = runCatching {
                            withContext(Dispatchers.IO) {
                                Library.remove(appContext, series.id)
                            }
                        }.isSuccess
                        libraryBusy = false
                        if (removed) {
                            inLibrary = false
                            onLibraryChanged()
                        }
                    }
                } else {
                    showAddToLibrary = true
                }
            }
        },
        onCategories = { showCategories = true },
        onToggleAllDownloads = {
            if (downloadingAll) onCancelDownloads() else onDownloadAll()
        },
        onDeleteDownloads = { confirmDeleteAllDownloads = true },
        descriptionExpanded = descriptionExpanded,
        onToggleDescriptionExpanded = {
            descriptionExpanded = !descriptionExpanded
        },
        onSearchTag = onSearchTag,
        onLibrarySearchTag = onLibrarySearchTag,
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
        onDownloadBatch = onDownloadBatch,
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
        chapters = chapters,
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
            onDeleteChapters(selectedChapters)
            selectedIds = emptySet()
            confirmDeleteSelection = false
        },
        confirmDeleteAllDownloads = confirmDeleteAllDownloads,
        downloadedCount = downloadedCount,
        onDismissDeleteAllDownloads = { confirmDeleteAllDownloads = false },
        onDeleteAllDownloads = {
            onDeleteDownloads()
            confirmDeleteAllDownloads = false
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
