package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import eu.kanade.tachiyomi.source.online.HttpSource
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
/** Where the currently open series was reached from; decides where back goes. */
internal enum class SeriesOrigin { BROWSE, LIBRARY, HISTORY, GLOBAL_SEARCH, DOWNLOADS }

internal class GlobalResult(val source: Source, val series: List<Series>)

/** Everything needed to jump straight back into a chapter from a history row. */
internal class ResumeTarget(
    val source: Source,
    val series: Series,
    val chapters: List<Chapter>,
    val index: Int
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
        ensureNotificationPermission()
        // A queue left behind by a killed process resumes here rather than
        // waiting for the user to press anything. Doing it from a starting
        // Activity is also what keeps the foreground-service start legal on
        // Android 12+, where a background start would throw.
        if (DownloadQueue.items.isNotEmpty() && !DownloadQueue.paused) {
            DownloadService.start(this)
        }
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

    /**
     * Asks for POST_NOTIFICATIONS on Android 13+.
     *
     * The download service runs either way — a foreground service is still
     * allowed to start without it, the notification just never appears. So this
     * is asked for once and never insisted on: refusing costs the progress bar
     * and the pause/cancel actions, not the downloads.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        runCatching { requestPermissions(arrayOf(permission), 1) }
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
    var pages by remember { mutableStateOf<List<File?>>(emptyList()) }

    // global search state — hoisted here (not inside the screen) so results survive
    // navigating into a series and coming back
    var globalSearchOpen by remember { mutableStateOf(false) }
    var globalQuery by remember { mutableStateOf("") }
    var globalResults by remember { mutableStateOf<List<GlobalResult>>(emptyList()) }
    var globalRunning by remember { mutableStateOf(false) }
    var globalDone by remember { mutableIntStateOf(0) }
    var globalTotal by remember { mutableIntStateOf(0) }
    var globalPinnedOnly by remember { mutableStateOf(SourcePrefs.pinnedOnlySearch(context)) }
    // Display filter, not a scope: empty sources are kept in globalResults so
    // this can show or hide them without re-running the search.
    var globalHasResultsOnly by remember { mutableStateOf(true) }

    // How the current series was reached. Opening from Library or History has to
    // adopt its source to load chapters and pages, which would otherwise strand
    // the user on the per-source browse screen (branch 4 of the routing chain)
    // when they back out — a screen they never asked for and which has no results
    // behind it. This says where "back" should actually go.
    var seriesOrigin by remember { mutableStateOf(SeriesOrigin.BROWSE) }

    // Live download state now lives in DownloadQueue, which the service writes to
    // from its own process-scoped worker — the whole point being that a download
    // outlives this composable. What stays here is the local tick for filesystem
    // reads this screen causes itself, like deleting a series' downloads; it's
    // added to DownloadQueue.tick so either can invalidate a `remember`.
    var downloadTick by remember { mutableIntStateOf(0) }
    var downloadsOpen by remember { mutableStateOf(false) }
    var globalJob by remember { mutableStateOf<Job?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Non-null while the user is answering a Cloudflare challenge by hand. No UA
    // travels with it: the WebView presents its own and records what passed, so
    // the caller has nothing to decide.
    var challengeUrl by remember { mutableStateOf<String?>(null) }

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
                    val hidden = SourcePrefs.hiddenSources(context)
                    val langs = SourcePrefs.enabledLangs(context)
                    val searchable = (locals + extensionSources)
                        .filter { it.supportsSearch }
                        .filter {
                            SourcePrefs.isVisible(it.id, it.lang.ifBlank { "Other" }, hidden, langs)
                        }
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
                    // Empty ones are kept, not dropped: the "Has results" chip is
                    // what decides whether they're shown.
                    globalResults = globalResults + chunk.mapIndexed { i, src ->
                        GlobalResult(src, batch[i])
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

    /**
     * Chapters for a stored series, falling back to the offline cache.
     *
     * A successful fetch refreshes the cache. A failed one is only an error if
     * there's nothing cached — otherwise the last known list is better than a
     * dead end, and any chapter already downloaded is fully readable from it.
     */
    suspend fun chaptersWithFallback(src: Source, series: Series): List<Chapter> =
        runCatching { src.listChapters(series) }
            .onSuccess { ChapterCache.save(context, series.id, it) }
            .getOrElse { err ->
                val cached = ChapterCache.load(context, series.id)
                    .map { src.rehydrateChapter(it) }
                if (cached.isNotEmpty()) cached
                else throw IllegalStateException(
                    "Couldn't load the chapter list \u2014 ${err.message}"
                )
            }

    /**
     * Fills in author/description/genres/status in the background.
     *
     * Deliberately fire-and-forget: on most sources this is a second network
     * request, and it must never delay or block the chapter list. A failure just
     * means the series screen shows less. The id check stops a slow response
     * overwriting a series the user has since navigated away from.
     */
    fun enrichSeries(src: Source, series: Series) {
        scope.launch {
            val enriched = runCatching {
                withContext(Dispatchers.IO) { src.loadDetails(series) }
            }.getOrNull()
            if (enriched != null && activeSeries?.id == series.id) {
                activeSeries = enriched
            }
        }
    }

    fun openSeries(series: Series) {
        val src = activeSource ?: return
        seriesOrigin = SeriesOrigin.BROWSE
        activeSeries = series
        chapterList = emptyList()
        errorMessage = null
        enrichSeries(src, series)
        scope.launch {
            isLoading = true
            try {
                chapterList = withContext(Dispatchers.IO) {
                    src.listChapters(series).also { ChapterCache.save(context, series.id, it) }
                }
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
        // After openSeries, which sets it to BROWSE unconditionally.
        seriesOrigin = SeriesOrigin.GLOBAL_SEARCH
    }

    /** "See all" on a global search row: leave the results and browse that source. */
    fun openGlobalSource(source: Source) {
        cancelGlobalSearch()
        globalSearchOpen = false
        openSource(source, globalQuery)
    }

    /**
     * Opens the reader as soon as the page count is known, then fills pages in as
     * they download, rather than holding the screen until the whole chapter is on
     * disk. `isLoading` stays true for the duration, which is what tells the
     * reader that a still-blank page is pending rather than broken.
     */
    fun openChapter(index: Int) {
        val src = activeSource ?: return
        val chapter = chapterList.getOrNull(index) ?: return
        errorMessage = null
        pages = emptyList()
        scope.launch {
            isLoading = true
            try {
                src.loadPagesProgressively(chapter, persist = false) { partial ->
                    // Hop to main: the adapter publishes from its IO context.
                    withContext(Dispatchers.Main) {
                        pages = partial
                        if (partial.isNotEmpty() && activeChapterIdx != index) {
                            activeChapterIdx = index
                        }
                    }
                }
                if (pages.isEmpty()) errorMessage = "This chapter has no pages"
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not open this chapter"
            }
            isLoading = false
        }
    }

    /**
     * Hands chapters to the download service.
     *
     * Nothing is fetched here any more. This used to run in `scope`, the
     * composable's coroutine scope, which meant a download died with the Activity
     * — swiping the app away mid-chapter stopped it. Now the work is queued and
     * [DownloadService] drains it from a process-scoped worker behind a
     * foreground notification, so it survives leaving the app entirely.
     *
     * Called from a visible screen on a user tap, which is what makes the
     * foreground-service start legal on Android 12+.
     */
    fun queueDownloads(src: Source, chapters: List<Chapter>) {
        val series = activeSeries
        val seriesTitle = series?.title ?: ""
        // Carried so DownloadIndex can file the finished chapter under its
        // series without a second lookup — the download path itself needs
        // neither of these.
        val seriesId = series?.id ?: ""
        val cover = when (val c = series?.cover) {
            is File -> c.absolutePath
            is String -> c
            else -> ""
        }
        val added = DownloadQueue.enqueue(
            context,
            chapters.map { chapter ->
                DownloadItem(
                    sourceId = src.id,
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    seriesTitle = seriesTitle,
                    seriesId = seriesId,
                    cover = cover
                )
            }
        )
        // enqueue skips what's already downloaded or already queued; if it
        // skipped everything there's no reason to poke the service.
        if (added > 0) {
            if (DownloadQueue.paused) DownloadQueue.setPaused(context, false)
            DownloadService.start(context)
        }
    }

    fun downloadChapter(src: Source, chapter: Chapter) = queueDownloads(src, listOf(chapter))

    /** Queues every not-yet-downloaded chapter, oldest first. */
    fun downloadAll(src: Source, chapters: List<Chapter>) = queueDownloads(src, chapters)

    fun cancelDownloads() {
        // Clearing the queue isn't enough on its own: the chapter already in
        // flight is held by the service, so it has to be told. Checked first so
        // an empty queue doesn't start the service purely to stop it again.
        val wasRunning = DownloadQueue.items.isNotEmpty()
        DownloadQueue.clear(context)
        if (wasRunning) DownloadService.start(context, DownloadService.ACTION_CANCEL_ALL)
        downloadTick++
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
                    // Each stage names itself in the error: "details" and
                    // "chapters" are separate requests in most extensions, and
                    // knowing which one failed is the whole diagnosis.
                    val fetched = runCatching { src.restoreSeries(entry.seriesId, entry.title) }
                        .getOrElse {
                            throw IllegalStateException(
                                "Couldn't load series details \u2014 ${it.message}"
                            )
                        }
                        ?: throw IllegalStateException("That series is no longer available from its source")
                    val series = fetched.copy(
                        title = fetched.title.ifBlank { entry.title },
                        cover = fetched.cover ?: entry.cover.ifBlank { null }
                    )
                    Triple(src, series, chaptersWithFallback(src, series))
                }
                seriesOrigin = SeriesOrigin.LIBRARY
                activeSource = result.first
                activeSourceId = result.first.id
                activeSeries = result.second
                chapterList = result.third
                enrichSeries(result.first, result.second)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not open this series"
            }
            isLoading = false
        }
    }

    /**
     * Opens a series from the Downloads tab, offline first.
     *
     * Deliberately not routed through [openFromLibrary]: that starts with
     * `restoreSeries`, a network request, and the whole promise of this screen is
     * that it works in airplane mode. So the cached chapter list is consulted
     * first and the details fetch is allowed to fail into a title-and-cover-only
     * series — enough for `SeriesScreen` to render and for every downloaded
     * chapter to open, since the page store is checked before the handle is.
     */
    fun openFromDownloads(entry: DownloadedSeries) {
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val src = SourceManager.listAllSources(context)
                        .firstOrNull { it.id == entry.sourceId }
                        ?: throw IllegalStateException("That source is no longer installed")

                    val fetched = runCatching { src.restoreSeries(entry.seriesId, entry.title) }
                        .getOrNull()
                    val series = fetched?.copy(
                        title = fetched.title.ifBlank { entry.title },
                        cover = fetched.cover ?: entry.cover.ifBlank { null }
                    ) ?: Series(
                        id = entry.seriesId,
                        title = entry.title,
                        cover = entry.cover.ifBlank { null }
                    )

                    // Only ask the source for chapters if the details fetch worked
                    // — a handle-less series can't list them, and would come back
                    // empty rather than falling through to the cache.
                    val chapters =
                        if (fetched != null) chaptersWithFallback(src, series)
                        else ChapterCache.load(context, entry.seriesId)
                            .map { src.rehydrateChapter(it) }

                    if (chapters.isEmpty()) {
                        throw IllegalStateException(
                            "No chapter list cached for this series \u2014 open it once online"
                        )
                    }
                    Triple(src, series, chapters)
                }
                seriesOrigin = SeriesOrigin.DOWNLOADS
                activeSource = result.first
                activeSourceId = result.first.id
                activeSeries = result.second
                chapterList = result.third
                enrichSeries(result.first, result.second)
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
                    val fetched = runCatching { src.restoreSeries(entry.seriesId, entry.title) }
                        .getOrElse {
                            throw IllegalStateException(
                                "Couldn't load series details \u2014 ${it.message}"
                            )
                        }
                        ?: throw IllegalStateException("That series is no longer in the library")
                    val series = fetched.copy(
                        title = fetched.title.ifBlank { entry.title },
                        cover = fetched.cover ?: entry.coverPath.ifBlank { null }
                    )
                    val chapters = chaptersWithFallback(src, series)
                    val idx = chapters.indexOfFirst {
                        chapterKeyOf(entry.sourceId, it) == entry.chapterKey
                    }
                    if (idx < 0) throw IllegalStateException("That chapter is gone")
                    ResumeTarget(src, series, chapters, idx)
                }
                seriesOrigin = SeriesOrigin.HISTORY
                activeSource = target.source
                activeSourceId = target.source.id
                activeSeries = target.series
                chapterList = target.chapters
                enrichSeries(target.source, target.series)
                // Hands off to openChapter so resuming streams its pages the same
                // way opening one does, instead of blocking on the whole chapter.
                openChapter(target.index)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not resume"
            }
            isLoading = false
        }
    }

    // ---- routing ----

    val chapterIdx = activeChapterIdx
    val readerChapter = chapterIdx?.let { chapterList.getOrNull(it) }

    val challenge = challengeUrl
    if (challenge != null) {
        // Sits above every other branch, and safely so: it's gated on state that
        // is null in every other flow, and clearing that state drops back onto
        // whatever was underneath with nothing else touched. No branch below has
        // to know this one exists — which is the only reason it was safe to put
        // anything at the top of this chain.
        ChallengeWebViewScreen(
            url = challenge,
            onSolved = {
                challengeUrl = null
                // Re-run whatever was on screen. The clearance cookie is in the
                // store OkHttp already reads, so this is an ordinary retry.
                activeSource?.let { openSource(it, browseQuery) }
            },
            onBack = { challengeUrl = null }
        )
    } else if (chapterIdx != null && readerChapter != null && pages.isNotEmpty()) {
        val srcId = activeSourceId ?: ""
        val series = activeSeries
        val chKey = chapterKeyOf(srcId, readerChapter)
        val total = pages.size

        // key() rebuilds the pager state when the chapter changes
        key(chKey) {
            ReaderScreen(
                pages = pages,
                stillLoading = isLoading,
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
            sourceName = activeSource?.name ?: "",
            canDownload = activeSource?.supportsDownload == true,
            downloadProgress = DownloadQueue.progress,
            // Either side can invalidate the on-disk reads in the chapter list:
            // the service finishing a chapter, or this screen deleting them.
            downloadTick = downloadTick + DownloadQueue.tick,
            downloadingAll = DownloadQueue.items.isNotEmpty(),
            onDownload = { ch -> activeSource?.let { downloadChapter(it, ch) } },
            onDownloadAll = { activeSource?.let { downloadAll(it, chapterList) } },
            onCancelDownloads = { cancelDownloads() },
            onDeleteDownloads = {
                chapterList.forEach { Downloads.delete(context, it.id) }
                downloadTick++
            },
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
                // Only a series reached by browsing has a source listing to go
                // back to. Everything else drops the adopted source so the chain
                // falls through to the tab the user actually came from.
                if (seriesOrigin != SeriesOrigin.BROWSE) {
                    activeSource = null
                    activeSourceId = null
                    seriesList = null
                }
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
            hasResultsOnly = globalHasResultsOnly,
            onToggleHasResultsOnly = { globalHasResultsOnly = it },
            onSearch = { runGlobalSearch(it) },
            onCancel = { cancelGlobalSearch() },
            onOpenSource = { openGlobalSource(it) },
            onOpenSeries = { src, s -> openGlobalResult(src, s) },
            onBack = {
                cancelGlobalSearch()
                globalSearchOpen = false
                // Opening a result adopted that result's source. Leaving search
                // has to give it back, or the chain lands on a browse screen with
                // nothing in it.
                activeSource = null
                activeSourceId = null
                seriesList = null
            }
        )
    } else if (activeSource != null) {
        // Only extension sources backed by an HttpSource have a site to open;
        // for anything else the button is absent rather than broken.
        val site = activeSource?.siteUrl()
        // `fun()` rather than a lambda: a brace directly after `else` opens a
        // block, so a lambda there has to be wrapped in a second pair and reads
        // like a typo. An anonymous function is the same value with no ambiguity.
        val startChallenge: (() -> Unit)? = if (site == null) null else fun() {
            challengeUrl = site
        }
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
            },
            onSolveChallenge = startChallenge
        )
    } else if (downloadsOpen) {
        // Last branch before the tabs: it's only ever opened from More, which is
        // itself a tab, so nothing deeper can be underneath it. Backing out lands
        // on the tab bar, which is where it was reached from.
        DownloadQueueScreen(onBack = { downloadsOpen = false })
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
                        label = { Text("Downloads") },
                        icon = { Text("⬇️") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
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
                    3 -> DownloadsTab(
                        downloadTick = downloadTick + DownloadQueue.tick,
                        onOpen = { openFromDownloads(it) },
                        onOpenQueue = { downloadsOpen = true }
                    )
                    4 -> MoreTab(onOpenDownloads = { downloadsOpen = true })
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

/**
 * The source's website, for opening in a WebView. Null when there isn't one:
 * local folder sources, and any extension that isn't an `HttpSource`.
 *
 * Reached through `catalogueSource` rather than added to this app's [Source]
 * interface, because it's an implementation detail of exactly one source type
 * and nothing else in the app has any use for it.
 */
private fun Source.siteUrl(): String? =
    ((this as? TachiyomiSourceAdapter)?.catalogueSource as? HttpSource)
        ?.baseUrl
        ?.takeIf { it.isNotBlank() }
