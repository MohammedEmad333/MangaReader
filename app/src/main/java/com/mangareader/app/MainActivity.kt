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
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- prefs: the same store every other object in this package uses ----------

internal fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

/** Stable per-chapter key: source id + chapter id. Drives resume, read flags and history. */
internal fun chapterKeyOf(sourceId: String, chapter: Chapter): String = "$sourceId|${chapter.id}"

internal fun savedPage(context: Context, key: String): Int =
    prefs(context).getInt("pos:$key", 0)

internal fun savePage(context: Context, key: String, page: Int) {
    prefs(context).edit().putInt("pos:$key", page).apply()
}

internal fun isIncognito(context: Context): Boolean =
    prefs(context).getBoolean("incognito", false)

// ---------- global search tuning ----------

/** How many sources are queried at once. Kept low: every one is a live network call. */
internal const val GLOBAL_SEARCH_CONCURRENCY = 6

/** Per-source cap on the row of results, so one chatty source can't dominate. */
internal const val GLOBAL_SEARCH_PER_SOURCE = 12

/** One source's slice of a global search. Sources that error out are dropped. */
internal class GlobalResult(val source: Source, val series: List<Series>)

/** Everything needed to jump straight back into a chapter from a history row. */
internal class ResumeTarget(
    val source: Source,
    val series: Series,
    val chapters: List<Chapter>,
    val index: Int,
    val pages: List<File>
)

/**
 * Walks the exact same path ExtensionManager.loadInstalledSources takes, but reports
 * every step instead of swallowing failures into printStackTrace(). Diagnostic only.
 */

internal fun diagnoseExtensions(context: Context): String =
    ExtensionLoader.diagnose(context)

// ---------- activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SourceManager.migrateLegacy(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    YomuApp()
                }
            }
        }
    }
}

// ---------- root ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(0) }
    var configs by remember { mutableStateOf(SourceManager.list(context)) }
    var extensionSources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var history by remember { mutableStateOf(History.list(context)) }

    var showSourceDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<SourceConfig?>(null) }

    // navigation state
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    var activeSource by remember { mutableStateOf<Source?>(null) }
    var seriesList by remember { mutableStateOf<List<Series>?>(null) }
    var browsePage by remember { mutableIntStateOf(1) }
    var browseHasNext by remember { mutableStateOf(false) }
    var browseQuery by remember { mutableStateOf("") }
    var loadingMore by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<Series?>(null) }
    var chapterList by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var activeChapterIdx by remember { mutableStateOf<Int?>(null) }
    var pages by remember { mutableStateOf<List<File>>(emptyList()) }

    // global search state — hoisted here (not inside the screen) so results survive
    // navigating into a series and coming back
    var globalSearchOpen by remember { mutableStateOf(false) }
    var globalQuery by remember { mutableStateOf("") }
    var globalResults by remember { mutableStateOf<List<GlobalResult>>(emptyList()) }
    var globalRunning by remember { mutableStateOf(false) }
    var globalDone by remember { mutableIntStateOf(0) }
    var globalTotal by remember { mutableIntStateOf(0) }
    var globalPinnedOnly by remember { mutableStateOf(SourcePrefs.pinnedOnlySearch(context)) }
    var globalJob by remember { mutableStateOf<Job?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // bumped whenever a read flag / resume position changes, to re-read prefs in lists
    var readTick by remember { mutableIntStateOf(0) }

    // bumped whenever the library changes, to re-read it in LibraryTab
    var libraryTick by remember { mutableIntStateOf(0) }

    // Re-scan installed extensions every time the app comes back to the foreground,
    // so returning from the system installer picks up the new package. Fires on
    // first launch too, which is why this replaces the old one-shot LaunchedEffect.
    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    extensionSources = withContext(Dispatchers.IO) {
                        runCatching {
                            SourceManager.listAllSources(context)
                                .filter { it.id.startsWith("tachi:") }
                        }.getOrDefault(emptyList())
                    }
                }
            }
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    /** Loads page 1 of a source, either the catalogue or a search. */
    fun openSource(source: Source, query: String = "") {
        activeSourceId = source.id
        activeSource = source
        // Feeds the "Last used" section at the top of the Sources list.
        SourcePrefs.setLastUsed(context, source.id)
        seriesList = null
        browsePage = 1
        browseHasNext = false
        browseQuery = query
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val page = withContext(Dispatchers.IO) {
                    if (query.isBlank()) source.browseSeries(1)
                    else source.searchSeries(query, 1)
                }
                seriesList = page.series
                browseHasNext = page.hasNext
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not scan this source"
                seriesList = emptyList()
            }
            isLoading = false
        }
    }

    /** Appends the next page to the current browse/search results. */
    fun loadMoreSeries() {
        val source = activeSource ?: return
        if (loadingMore || !browseHasNext) return
        scope.launch {
            loadingMore = true
            val next = browsePage + 1
            try {
                val page = withContext(Dispatchers.IO) {
                    if (browseQuery.isBlank()) source.browseSeries(next)
                    else source.searchSeries(browseQuery, next)
                }
                seriesList = (seriesList ?: emptyList()) + page.series
                browsePage = next
                browseHasNext = page.hasNext
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not load more"
                browseHasNext = false
            }
            loadingMore = false
        }
    }

    /**
     * Queries searchable sources for [query], a batch of
     * GLOBAL_SEARCH_CONCURRENCY at a time, publishing each batch as it lands so
     * results appear progressively instead of after the slowest source.
     *
     * With [globalPinnedOnly] set, the fan-out is limited to pinned sources —
     * the difference between querying 95 sites and querying the handful actually
     * used. It falls back to everything when no pinned source can search, so the
     * toggle can never produce a silently empty result.
     */
    fun runGlobalSearch(query: String) {
        globalJob?.cancel()
        globalQuery = query
        globalResults = emptyList()
        globalDone = 0
        globalTotal = 0
        if (query.isBlank()) {
            globalRunning = false
            globalJob = null
            return
        }
        globalRunning = true
        globalJob = scope.launch {
            try {
                val targets = withContext(Dispatchers.IO) {
                    val locals = configs.mapNotNull {
                        runCatching { SourceManager.build(context, it) }.getOrNull()
                    }
                    val searchable = (locals + extensionSources).filter { it.supportsSearch }
                    if (globalPinnedOnly) {
                        val pinned = SourcePrefs.pinned(context)
                        val subset = searchable.filter { it.id in pinned }
                        if (subset.isNotEmpty()) subset else searchable
                    } else {
                        searchable
                    }
                }
                globalTotal = targets.size
                targets.chunked(GLOBAL_SEARCH_CONCURRENCY).forEach { chunk ->
                    val batch = withContext(Dispatchers.IO) {
                        chunk.map { src ->
                            async {
                                runCatching {
                                    src.searchSeries(query, 1).series
                                        .take(GLOBAL_SEARCH_PER_SOURCE)
                                }.getOrDefault(emptyList())
                            }
                        }.awaitAll()
                    }
                    globalResults = globalResults + chunk.mapIndexedNotNull { i, src ->
                        val hits = batch[i]
                        if (hits.isEmpty()) null else GlobalResult(src, hits)
                    }
                    globalDone += chunk.size
                }
            } finally {
                globalRunning = false
            }
        }
    }

    fun cancelGlobalSearch() {
        globalJob?.cancel()
        globalJob = null
        globalRunning = false
    }

    /** Flips the pinned-only filter and re-runs the current query under it. */
    fun setGlobalPinnedOnly(value: Boolean) {
        globalPinnedOnly = value
        SourcePrefs.setPinnedOnlySearch(context, value)
        if (globalQuery.isNotBlank()) runGlobalSearch(globalQuery)
    }

    fun openSourceConfig(config: SourceConfig) {
        val built = SourceManager.build(context, config)
        if (built == null) {
            errorMessage = "\"${config.label}\" isn't configured yet"
            return
        }
        openSource(built)
    }

    fun openSeries(series: Series) {
        val src = activeSource ?: return
        activeSeries = series
        chapterList = emptyList()
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                chapterList = withContext(Dispatchers.IO) { src.listChapters(series) }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not list chapters"
            }
            isLoading = false
        }
    }

    /** Tapping a cover in global search: adopt that source, then open the series. */
    fun openGlobalResult(source: Source, series: Series) {
        activeSource = source
        activeSourceId = source.id
        seriesList = null
        browsePage = 1
        browseHasNext = false
        browseQuery = globalQuery
        openSeries(series)
    }

    /** "See all" on a global search row: leave the results and browse that source. */
    fun openGlobalSource(source: Source) {
        cancelGlobalSearch()
        globalSearchOpen = false
        openSource(source, globalQuery)
    }

    fun openChapter(index: Int) {
        val src = activeSource ?: return
        val chapter = chapterList.getOrNull(index) ?: return
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val loaded = withContext(Dispatchers.IO) { src.loadPages(chapter) }
                pages = loaded
                activeChapterIdx = index
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not open this chapter"
            }
            isLoading = false
        }
    }

    /** Reopen a saved series: resolve its source, then re-fetch its chapter list. */
    fun openFromLibrary(entry: LibraryEntry) {
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val src = SourceManager.listAllSources(context)
                        .firstOrNull { it.id == entry.sourceId }
                        ?: throw IllegalStateException("That source is no longer installed")
                    val fetched = src.getSeries(entry.seriesId)
                        ?: throw IllegalStateException("That series is no longer available from its source")
                    // A source whose details request failed can come back with no
                    // title. The library already stores the name it was saved under,
                    // which beats showing a blank header.
                    val series = fetched.copy(title = fetched.title.ifBlank { entry.title })
                    Triple(src, series, src.listChapters(series))
                }
                activeSource = result.first
                activeSourceId = result.first.id
                activeSeries = result.second
                chapterList = result.third
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not open this series"
            }
            isLoading = false
        }
    }

    fun openFromHistory(entry: HistoryEntry) {
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val target = withContext(Dispatchers.IO) {
                    val src = SourceManager.listAllSources(context)
                        .firstOrNull { it.id == entry.sourceId }
                        ?: throw IllegalStateException("That source no longer exists")
                    val fetched = src.getSeries(entry.seriesId)
                        ?: throw IllegalStateException("That series is no longer in the library")
                    val series = fetched.copy(title = fetched.title.ifBlank { entry.title })
                    val chapters = src.listChapters(series)
                    val idx = chapters.indexOfFirst {
                        chapterKeyOf(entry.sourceId, it) == entry.chapterKey
                    }
                    if (idx < 0) throw IllegalStateException("That chapter is gone")
                    ResumeTarget(src, series, chapters, idx, src.loadPages(chapters[idx]))
                }
                activeSource = target.source
                activeSourceId = target.source.id
                activeSeries = target.series
                chapterList = target.chapters
                pages = target.pages
                activeChapterIdx = target.index
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not resume"
            }
            isLoading = false
        }
    }

    // ---- routing ----

    val chapterIdx = activeChapterIdx
    val readerChapter = chapterIdx?.let { chapterList.getOrNull(it) }

    if (chapterIdx != null && readerChapter != null && pages.isNotEmpty()) {
        val srcId = activeSourceId ?: ""
        val series = activeSeries
        val chKey = chapterKeyOf(srcId, readerChapter)
        val total = pages.size

        // key() rebuilds the pager state when the chapter changes
        key(chKey) {
            ReaderScreen(
                pages = pages,
                initialPage = savedPage(context, chKey).coerceIn(0, total - 1),
                hasPrev = chapterIdx > 0,
                hasNext = chapterIdx < chapterList.size - 1,
                onPrev = { openChapter(chapterIdx - 1) },
                onNext = { openChapter(chapterIdx + 1) },
                onProgress = { page ->
                    savePage(context, chKey, page)
                    if (page >= total - 1) ReadState.setRead(context, chKey, true)
                    if (!isIncognito(context)) {
                        History.touch(
                            context,
                            HistoryEntry(
                                chapterKey = chKey,
                                title = listOfNotNull(series?.title, readerChapter.name)
                                    .joinToString(" · "),
                                sourceId = srcId,
                                seriesId = series?.id ?: "",
                                coverPath = when (val c = series?.cover) {
                                    is java.io.File -> c.absolutePath
                                    is String -> c
                                    else -> ""
                                },
                                page = page,
                                total = total,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onClose = {
                    activeChapterIdx = null
                    pages = emptyList()
                    history = History.list(context)
                    readTick++
                }
            )
        }
    } else if (activeSeries != null) {
        SeriesScreen(
            series = activeSeries!!,
            chapters = chapterList,
            sourceId = activeSourceId ?: "",
            loading = isLoading,
            error = errorMessage,
            readTick = readTick,
            onOpen = { openChapter(it) },
            onToggleRead = { chapter ->
                val k = chapterKeyOf(activeSourceId ?: "", chapter)
                ReadState.setRead(context, k, !ReadState.isRead(context, k))
                readTick++
            },
            onLibraryChanged = { libraryTick++ },
            onBack = {
                activeSeries = null
                chapterList = emptyList()
                errorMessage = null
            }
        )
    } else if (globalSearchOpen) {
        // Sits below SeriesScreen in this chain on purpose: opening a hit shows the
        // series, and backing out of it lands on the results again.
        GlobalSearchScreen(
            query = globalQuery,
            results = globalResults,
            running = globalRunning,
            done = globalDone,
            total = globalTotal,
            pinnedOnly = globalPinnedOnly,
            onTogglePinnedOnly = { setGlobalPinnedOnly(it) },
            onSearch = { runGlobalSearch(it) },
            onCancel = { cancelGlobalSearch() },
            onOpenSource = { openGlobalSource(it) },
            onOpenSeries = { src, s -> openGlobalResult(src, s) },
            onBack = {
                cancelGlobalSearch()
                globalSearchOpen = false
            }
        )
    } else if (activeSource != null) {
        LibraryScreen(
            title = activeSource!!.name,
            series = seriesList,
            loading = isLoading,
            error = errorMessage,
            supportsSearch = activeSource!!.supportsSearch,
            query = browseQuery,
            hasNext = browseHasNext,
            loadingMore = loadingMore,
            onSearch = { q -> activeSource?.let { openSource(it, q) } },
            onLoadMore = { loadMoreSeries() },
            onRescan = { activeSource?.let { openSource(it, browseQuery) } },
            onOpen = { openSeries(it) },
            onBack = {
                activeSource = null
                activeSourceId = null
                seriesList = null
                errorMessage = null
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        label = { Text("Library") },
                        icon = { Text("📚") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        label = { Text("Browse") },
                        icon = { Text("🧭") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            currentTab = 2
                            history = History.list(context)
                        },
                        label = { Text("History") },
                        icon = { Text("🕒") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        label = { Text("More") },
                        icon = { Text("⚙️") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    0 -> LibraryTab(
                        libraryTick = libraryTick,
                        error = errorMessage,
                        onOpen = { openFromLibrary(it) },
                        onRemove = {
                            Library.remove(context, it.seriesId)
                            libraryTick++
                        }
                    )
                    1 -> BrowseTab(
                        configs = configs,
                        extensions = extensionSources,
                        onGlobalSearch = {
                            globalSearchOpen = true
                            if (globalQuery.isNotBlank() &&
                                globalResults.isEmpty() &&
                                !globalRunning
                            ) {
                                runGlobalSearch(globalQuery)
                            }
                        },
                        onAdd = {
                            editingConfig = SourceConfig(SourceManager.newId(), "local", "")
                            showSourceDialog = true
                        },
                        onOpenConfig = { openSourceConfig(it) },
                        onOpenExtension = { openSource(it) },
                        onEdit = {
                            editingConfig = it
                            showSourceDialog = true
                        },
                        onDelete = {
                            SourceManager.remove(context, it.id)
                            configs = SourceManager.list(context)
                        },
                        onExtensionsChanged = {
                            scope.launch {
                                extensionSources = withContext(Dispatchers.IO) {
                                    runCatching {
                                        SourceManager.listAllSources(context)
                                            .filter { it.id.startsWith("tachi:") }
                                    }.getOrDefault(emptyList())
                                }
                            }
                        }
                    )
                    2 -> HistoryScreen(
                        history = history,
                        loading = isLoading,
                        error = errorMessage,
                        onOpen = { openFromHistory(it) },
                        onDelete = {
                            History.remove(context, it.chapterKey)
                            history = History.list(context)
                        },
                        onClearAll = {
                            History.list(context).forEach { History.remove(context, it.chapterKey) }
                            history = History.list(context)
                        }
                    )
                    3 -> MoreTab()
                }
            }
        }
    }

    val editing = editingConfig
    if (showSourceDialog && editing != null) {
        SourceDialog(
            value = editing,
            onChange = { editingConfig = it },
            onDismiss = {
                showSourceDialog = false
                editingConfig = null
            },
            onSave = { saved ->
                SourceManager.upsert(context, saved)
                configs = SourceManager.list(context)
                showSourceDialog = false
                editingConfig = null
            }
        )
    }
}

// ---------- shared ----------
