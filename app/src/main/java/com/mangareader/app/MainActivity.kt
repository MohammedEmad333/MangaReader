package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.CancellationException
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

/**
 * Forgets where a chapter was left off.
 *
 * Removed rather than written as 0, so `savedPage(...) > 0` keeps meaning
 * "started" — the distinction the Start/Resume button reads, and the reason
 * marking a chapter unread has to come through here rather than only clearing
 * the read flag.
 */
internal fun clearPage(context: Context, key: String) {
    prefs(context).edit().remove("pos:$key").apply()
}

/** [savePage] for a whole batch, in one edit. Used by the Tachiyomi import. */
internal fun savePageBulk(context: Context, pages: Map<String, Int>) {
    if (pages.isEmpty()) return
    val editor = prefs(context).edit()
    pages.forEach { (key, page) -> editor.putInt("pos:$key", page) }
    editor.apply()
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

/**
 * The message to show when a call into an extension fails.
 *
 * **Every source call catches [Throwable], not [Exception], and this is why.**
 * An extension is a separately-compiled APK loaded through a `PathClassLoader`
 * against a *vendored* copy of the Tachiyomi source API. When the extension was
 * built against a newer API than this app ships, the mismatch does not arrive as
 * an exception — it arrives as a [LinkageError]: `NoClassDefFoundError` for a
 * model class that doesn't exist here, `NoSuchMethodError` for a method that
 * does exist but changed shape, `AbstractMethodError` for an interface that grew
 * a member. Those are `Error`, not `Exception`, so `catch (e: Exception)` lets
 * them straight through and **the app closes**.
 *
 * That is not hypothetical. An updated Elite Babes took the app down on every
 * open, and the only symptom was a process that vanished — no message, nothing
 * to read, and nothing to distinguish it from a source that was simply broken.
 *
 * Two things are deliberately still rethrown:
 *
 * - [CancellationException], because a cancelled coroutine has to finish
 *   cancelled. Closing the reader mid-load cancels a page fetch, and reporting
 *   that as a failed chapter is a bug this codebase has already had once.
 * - [VirtualMachineError] — out of memory, stack overflow. The process is
 *   already in trouble and dressing it up as "this source didn't work" hides a
 *   real problem behind a plausible-looking one.
 *
 * A version gate cannot replace this. `ExtensionLoader` does check the lib
 * version, but it reads what the extension *claims*, so it can only refuse
 * extensions that declare themselves out of range — not ones that declare a
 * version this app says it supports and then reach for something it doesn't
 * have. The honest gate is the failure itself, named and shown.
 */
internal fun sourceFailureMessage(t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    if (t is VirtualMachineError) throw t
    if (t is LinkageError) {
        // Named rather than summarised: the class or method in the message is
        // the exact piece of API the vendored source-api is missing, which is
        // the one fact needed to decide whether to implement it or to stop
        // claiming support for that lib version.
        return "This extension was built against a newer source API than this " +
            "app provides \u2014 ${t.javaClass.simpleName}: " +
            "${t.message ?: "missing symbol"}"
    }
    // Name the type when there is no message. A bare "Could not list chapters"
    // is indistinguishable from a source that legitimately has none, and it is
    // what an exception carrying a null message produces — NoSuchElementException
    // out of an empty stream, an NPE, a ClassCastException. Those are different
    // bugs and they were all rendering as the same sentence.
    //
    // The cause is included when there is one, because the outer type is often
    // a wrapper and the inner one is the answer.
    val cause = t.cause?.takeIf { it !== t }?.javaClass?.simpleName
    val type = t.javaClass.simpleName + if (cause != null) " \u2190 $cause" else ""
    return t.message?.let { "$it ($type)" } ?: "$fallback \u2014 $type"
}

// ---------- activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Back to the plain theme before the window is built. The manifest
        // declares Theme.Yomu.Splash on this Activity so the app icon is on
        // screen from the moment the process starts rather than a blank
        // rectangle; leaving it in place would keep that icon as the window
        // background behind the whole app for the rest of the session.
        //
        // Before super.onCreate, which is where the window gets its theme. On
        // API 31+ the system draws its own splash from the same two attributes
        // (see res/values-v31/themes.xml) and this call is simply harmless.
        setTheme(R.style.Theme_Yomu)
        super.onCreate(savedInstanceState)
        // The first thing in the process to touch SharedPreferences, so it is
        // the one that pays for loading and parsing the whole 2.5 MB
        // manga_reader.xml — every one of the twelve stores that share the file
        // is loaded by whoever asks first.
        //
        // The marks inside Library and SeriesIndex cannot see this. They run
        // during composition, by which time the file is already in memory, so
        // both report 0 ms for "Prefs first read" — recording a cost that has
        // *already been paid*, not one that was free. That made row 1 of the
        // 0.71 report's reading table unable to fire as written
        // (`SESSION_HANDOFF_0.71_RESULT.md` §4). This is the mark that can.
        StartupTimings.once("Prefs load (onCreate)") { SourceManager.migrateLegacy(this) }
        ensureNotificationPermission()
        // Both read SharedPreferences, so they have to happen before the first
        // composition rather than inside it: the theme decides the colour scheme
        // the whole tree is built with, and FLAG_SECURE has to be on the window
        // before it is ever drawn to keep the app out of the recents thumbnail.
        AppTheme.load(this)
        AppTheme.applySecureScreen(this, AppTheme.secureScreen(this))
        // A queue left behind by a killed process resumes here rather than
        // waiting for the user to press anything. Doing it from a starting
        // Activity is also what keeps the foreground-service start legal on
        // Android 12+, where a background start would throw.
        if (DownloadQueue.items.isNotEmpty() && !DownloadQueue.paused) {
            DownloadService.start(this)
        }
        setContent {
            MaterialTheme(colorScheme = yomuColorScheme()) {
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

/** Index of the Downloads tab in the bottom bar. Named because 3 says nothing. */
private const val DOWNLOADS_TAB = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(0) }


    // Which library category is showing. Hoisted out of LibraryTab because the
    // routing chain below replaces that whole branch when a series opens, which
    // destroyed the state and dumped the user back on the first tab every time
    // they backed out. rememberSaveable so it also survives a config change.
    // Seeded from prefs, not from null. `rememberSaveable` alone only restores
    // from the Activity's saved bundle, which a cold start from the launcher
    // doesn't have — so the tab survived rotation and was lost every time the
    // app was actually reopened, which is the case anyone notices.
    var libraryCategory by rememberSaveable {
        mutableStateOf<String?>(LibraryPrefs.lastCategory(context))
    }

    // The library's search, hoisted for exactly the same reason and with the
    // same symptom when it wasn't: typing a query, opening a result and backing
    // out landed on an unfiltered library with an empty field.
    var librarySearch by rememberSaveable { mutableStateOf("") }
    var librarySearchOpen by rememberSaveable { mutableStateOf(false) }

    // Where each grid was left. Same problem again — the routing chain replaces
    // whichever branch is showing, so a LazyGridState inside it doesn't survive
    // opening a series — and, because these are ordinary objects rather than
    // state, reading or writing one doesn't invalidate anything.
    val libraryScroll = remember { ScrollMemory() }
    val browseScroll = remember { ScrollMemory() }

    // The series screen needs its own rather than sharing one of the above:
    // ScrollMemory keeps a single signature for everything it holds, so a
    // library re-sort would throw away the chapter-list position and opening a
    // different series would throw away the library's. Its signature is the
    // series id, which is what makes backing out of a chapter restore the
    // position while opening a different series starts at the top.
    val seriesScroll = remember { ScrollMemory() }

    // And the Sources list its own again, for the same reason: its signature is
    // the pin/hidden/language set, which has nothing to do with either of the
    // above and would clear them on every pin toggle if shared.
    val sourcesScroll = remember { ScrollMemory() }

    // Read once per process. Empty on a fresh install and on a launch that
    // isn't an update, so this is normally a single getInt.
    val releaseNotes = remember { WhatsNew.pending(context) }
    var whatsNewOpen by remember { mutableStateOf(releaseNotes.isNotEmpty()) }
    LaunchedEffect(releaseNotes) {
        // An update whose versionCode has no changelog entry still has to move
        // the marker, or the next update replays this one as well.
        if (releaseNotes.isEmpty()) WhatsNew.markSeen(context)
    }

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
    var browseMode by remember { mutableStateOf(BrowseMode.POPULAR) }
    var filtersOpen by remember { mutableStateOf(false) }
    var probeOpen by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<Series?>(null) }
    var chapterList by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var activeChapterIdx by remember { mutableStateOf<Int?>(null) }
    var pages by remember { mutableStateOf<List<File?>>(emptyList()) }

    /**
     * Whether the *current* page load is still running.
     *
     * Separate from `isLoading`, which five other operations also write, because
     * this one answers a question only the reader asks: is a blank page still
     * coming, or did it fail? A flag shared with series opening and library
     * restoring cannot answer that, and a wrong answer here paints every page
     * that hasn't arrived yet as broken.
     */
    var pagesLoading by remember { mutableStateOf(false) }

    /**
     * Identifies the newest page load, so an older one can't clear the flag.
     *
     * Cancelling a job does not unwind it synchronously — `finally` runs whenever
     * the coroutine next resumes, which is routinely *after* its replacement has
     * started and set the flag. Comparing tokens is what makes the clear belong
     * to the load that set it. Not a `mutableStateOf`: nothing composes on it.
     */
    val pageLoadSeq = remember { intArrayOf(0) }

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

    // The series a tag search was launched from, if any.
    //
    // A tag search has to clear `activeSeries` to be visible at all — branch 3
    // sits above both search branches, so a live series hides them. That makes
    // the search a one-way trip unless the series is kept somewhere, and
    // without this it was: back fell through to the bottom nav and landed on
    // whichever tab was selected, usually Library. A tag is a detour from a
    // series, so back belongs on that series.
    //
    // Cleared by [openSeries], because opening something from the results is
    // navigating onward rather than detouring, and back from *there* should
    // return to the results.
    var tagSearchReturn by remember { mutableStateOf<Series?>(null) }

    // Live download state now lives in DownloadQueue, which the service writes to
    // from its own process-scoped worker — the whole point being that a download
    // outlives this composable. What stays here is the local tick for filesystem
    // reads this screen causes itself, like deleting a series' downloads; it's
    // added to DownloadQueue.tick so either can invalidate a `remember`.
    // The in-flight page load. Held so closing the reader can stop it — see
    // openChapter for why leaving it running reopened the chapter.
    var pageJob by remember { mutableStateOf<Job?>(null) }
    var downloadTick by remember { mutableIntStateOf(0) }
    var downloadsOpen by remember { mutableStateOf(false) }

    // Tapping the download notification lands on the QUEUE rather than on
    // wherever the app was last left.
    //
    // TWO PATHS, because there are two ways the tap arrives. Cold start: the
    // extra is on the Activity's launch intent and the LaunchedEffect below
    // reads it once. Already running: the notification uses CLEAR_TOP, which
    // delivers through onNewIntent, and a value read once at composition would
    // be the intent the app STARTED with — so the listener is required, not a
    // nicety.
    //
    // The extra is REMOVED once acted on. Left in place, the launch intent keeps
    // saying "open the queue" and a rotation would drag the user back to it.
    val activity = context as? ComponentActivity
    fun consumeQueueRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(DownloadService.EXTRA_OPEN_QUEUE, false) != true) return
        intent.removeExtra(DownloadService.EXTRA_OPEN_QUEUE)
        currentTab = DOWNLOADS_TAB
        downloadsOpen = true
    }
    LaunchedEffect(Unit) { consumeQueueRequest(activity?.intent) }
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { consumeQueueRequest(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    // Only whether Settings is open, not which page of it. The section is local
    // state inside SettingsScreen, so this routing chain gains one boolean rather
    // than one arm per settings page.
    var settingsOpen by remember { mutableStateOf(false) }
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
    // `mode` defaults to POPULAR rather than to the current value on purpose:
    // opening a different source should start at its catalogue, and a stale
    // LATEST carried over from the last source would land on a listing the new
    // one may not even have. Callers that are *re-running* the same screen —
    // rescan, clearing a search, retrying after a solved challenge — pass the
    // current mode explicitly.
    fun openSource(source: Source, query: String = "", mode: BrowseMode = BrowseMode.POPULAR) {
        activeSourceId = source.id
        activeSource = source
        // Feeds the "Last used" section at the top of the Sources list.
        SourcePrefs.setLastUsed(context, source.id)
        seriesList = null
        browsePage = 1
        browseHasNext = false
        browseQuery = query
        browseMode = mode
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val page = withContext(Dispatchers.IO) {
                    when {
                        query.isNotBlank() -> source.searchSeries(query, 1)
                        mode == BrowseMode.LATEST -> source.latestSeries(1)
                        mode == BrowseMode.FILTER -> source.filteredSeries(1)
                        else -> source.browseSeries(1)
                    }
                }
                seriesList = page.series
                browseHasNext = page.hasNext
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not scan this source")
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
                    when {
                        browseQuery.isNotBlank() -> source.searchSeries(browseQuery, next)
                        browseMode == BrowseMode.LATEST -> source.latestSeries(next)
                        browseMode == BrowseMode.FILTER -> source.filteredSeries(next)
                        else -> source.browseSeries(next)
                    }
                }
                seriesList = (seriesList ?: emptyList()) + page.series
                browsePage = next
                browseHasNext = page.hasNext
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not load more")
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
                    val showNsfw = SourcePrefs.showNsfw(context)
                    val searchable = (locals + extensionSources)
                        .filter { it.supportsSearch }
                        .filter {
                            SourcePrefs.isVisible(
                                it.id, it.lang.ifBlank { "Other" }, it.isNsfw,
                                hidden, langs, showNsfw
                            )
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
                // An imported entry can arrive without a cover, because the
                // backup's was unusable here. This is the first moment the real
                // one is known. No-op once a cover is stored.
                withContext(Dispatchers.IO) {
                    Library.healCover(context, series.id, enriched.cover)
                }
            }
        }
    }

    fun openSeries(series: Series) {
        val src = activeSource ?: return
        seriesOrigin = SeriesOrigin.BROWSE
        // Opening a result ends the detour: back from this series goes to the
        // listing behind it, not to whatever the tag search started from.
        tagSearchReturn = null
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
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not list chapters")
            }
            isLoading = false
        }
    }

    /**
     * Re-fetches the open series' chapter list.
     *
     * Deliberately **not** `openSeries(activeSeries!!)`: that resets
     * `seriesOrigin` to BROWSE and clears `tagSearchReturn`, so refreshing a
     * series reached from the Library would send Back to an empty browse screen
     * — the adopted-source trap, arrived at from a new direction.
     *
     * `chapterList` is also left alone until the new one lands, so the list
     * stays on screen and readable while the request runs. Clearing it first is
     * what 0.102 had to undo in the reader for the same reason.
     */
    fun refreshChapters() {
        val src = activeSource ?: return
        val series = activeSeries ?: return
        errorMessage = null
        enrichSeries(src, series)
        scope.launch {
            isLoading = true
            try {
                chapterList = withContext(Dispatchers.IO) {
                    src.listChapters(series).also { ChapterCache.save(context, series.id, it) }
                }
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not list chapters")
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
        // `pages` is deliberately **not** cleared here.
        //
        // The reader's routing branch requires a non-empty page list, so
        // emptying it drops the whole screen back to the series list until the
        // next chapter's first publish lands — a visible flash of the wrong
        // screen on every Prev, Next and chapter-picker tap. Holding the
        // outgoing chapter's pages for that moment reads as the reader pausing,
        // which is what it is doing. The first publish below replaces them
        // wholesale, together with `activeChapterIdx`.
        //
        // The failure path clears them instead, so a chapter that can't be
        // opened still falls back to the series screen where the error shows.
        // Whichever chapter was loading, it isn't wanted any more: this is
        // either a different chapter or a reopen of the same one.
        pageJob?.cancel()
        // Where the reader is about to open, so the fetch can start there instead
        // of at page 1. Read here rather than in the reader because the order
        // requests go out in is settled before the first one is sent, and the
        // reader doesn't exist yet — it opens on the first publish. Same key the
        // reader's `initialPage` reads, so the two cannot disagree.
        val resumeAt = savedPage(context, chapterKeyOf(src.id, chapter))
        // Claimed before the job is launched, so the comparison in `finally`
        // never depends on when `pageJob` happens to be assigned.
        val token = ++pageLoadSeq[0]
        // Local to this load, so a stale job can't touch the reader after the
        // user has left it.
        var opened = false
        pageJob = scope.launch {
            pagesLoading = true
            isLoading = true
            try {
                src.loadPagesProgressively(chapter, persist = false, startAt = resumeAt) { partial ->
                    // Hop to main: the adapter publishes from its IO context.
                    withContext(Dispatchers.Main) {
                        pages = partial
                        // Only the *first* publish opens the reader. This used to
                        // re-assert activeChapterIdx whenever it didn't match,
                        // which reads as "make sure the reader is showing" and
                        // behaves as "put it back if the user closed it" — so
                        // backing out of a chapter mid-download reopened it, over
                        // and over, and only felt fixed once loading had finished.
                        if (!opened && partial.isNotEmpty()) {
                            opened = true
                            activeChapterIdx = index
                        }
                    }
                }
                if (pages.isEmpty()) errorMessage = "This chapter has no pages"
            } catch (e: CancellationException) {
                // Closing the reader cancels this. Rethrow so the coroutine ends
                // as cancelled rather than being reported as a failed chapter.
                throw e
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not open this chapter")
                // Drop out of the reader so the error is somewhere it can be
                // read. Without this the previous chapter stays on screen and
                // the failure is silent.
                pages = emptyList()
            } finally {
                // finally, not a trailing statement: cancellation skips the tail
                // of the block and would otherwise leave the spinner up forever.
                //
                // Guarded, because a cancelled load unwinds here *after* its
                // replacement has already started. Backing out of a chapter
                // mid-fetch and reopening it did exactly that: the old job put
                // the light out on the new one, so every page that hadn't landed
                // yet read "couldn't be loaded" until it did.
                if (token == pageLoadSeq[0]) {
                    pagesLoading = false
                    isLoading = false
                }
            }
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

    /**
     * Stops the downloads for one series, leaving the rest of the queue alone.
     *
     * This is what the series screen's Stop calls. It used to clear the whole
     * queue — so stopping one series silently discarded every other series
     * queued behind it. Cancelling everything is still reachable, from the
     * download queue screen's "Cancel all", which is where it belongs.
     *
     * The chapter being fetched right now is held by the service rather than by
     * the queue, so delisting it is not enough; it gets an explicit skip, and
     * only when it was one of ours. `ACTION_SKIP` re-checks the id against
     * `activeId` on the service side, so a chapter that finished in the gap
     * between these two lines is not cancelled by mistake.
     */
    fun cancelSeriesDownloads(seriesId: String) {
        val active = DownloadQueue.activeId
        val removed = DownloadQueue.removeSeries(context, seriesId)
        if (removed.isEmpty()) return
        if (active != null && active in removed) {
            DownloadService.start(context, DownloadService.ACTION_SKIP, active)
        }
        downloadTick++
    }

    /** Reopen a saved series: resolve its source, then re-fetch its chapter list. */
    fun openFromLibrary(entry: LibraryEntry) {
        errorMessage = null
        seriesOrigin = SeriesOrigin.LIBRARY
        // The screen opens on what the library already holds, before any network
        // work happens. Opening from browse has a Series in hand and makes one
        // request; opening from here made two — details, then chapters — with
        // nothing on screen until both had come back.
        //
        // This stub carries no handle, so it's for display only: listChapters
        // returns nothing without one, and everything that needs it waits for
        // the real Series fetched below. activeSource is read through `?.` on
        // the series screen, so the moment before it's resolved is safe too.
        activeSeries = Series(
            id = entry.seriesId,
            title = entry.title,
            cover = entry.cover.ifBlank { null }
        )
        chapterList = emptyList()
        scope.launch {
            isLoading = true
            try {
                val src = withContext(Dispatchers.IO) {
                    SourceManager.listAllSources(context).firstOrNull { it.id == entry.sourceId }
                } ?: throw IllegalStateException("That source is no longer installed")
                activeSource = src
                activeSourceId = src.id

                // Whatever was cached goes up while the requests are in flight.
                val cached = withContext(Dispatchers.IO) {
                    ChapterCache.load(context, entry.seriesId).map { src.rehydrateChapter(it) }
                }
                if (cached.isNotEmpty() && activeSeries?.id == entry.seriesId) {
                    chapterList = cached
                }

                val result = withContext(Dispatchers.IO) {
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
                    series to chaptersWithFallback(src, series)
                }
                // Not if the user has moved on to another series meanwhile.
                if (activeSeries?.id == entry.seriesId) {
                    activeSeries = result.first
                    chapterList = result.second
                    enrichSeries(src, result.first)
                }
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not open this series")
            }
            isLoading = false
        }
    }

    /**
     * Opens a series from the Downloads tab, offline first.
     *
     * Deliberately not routed through [openFromLibrary]: the whole promise of
     * this screen is that it works in airplane mode. The cached chapter list is
     * what's shown, and the details fetch is allowed to fail into a
     * title-and-cover-only series — enough for `SeriesScreen` to render and for
     * every downloaded chapter to open, since the page store is checked before
     * the handle is.
     *
     * The cache goes up before any request is made rather than after one fails,
     * which is the difference between "works offline" and "opens instantly".
     */
    fun openFromDownloads(entry: DownloadedSeries) {
        errorMessage = null
        seriesOrigin = SeriesOrigin.DOWNLOADS
        activeSeries = Series(
            id = entry.seriesId,
            title = entry.title,
            cover = entry.cover.ifBlank { null }
        )
        chapterList = emptyList()
        scope.launch {
            isLoading = true
            try {
                val src = withContext(Dispatchers.IO) {
                    SourceManager.listAllSources(context).firstOrNull { it.id == entry.sourceId }
                } ?: throw IllegalStateException("That source is no longer installed")
                activeSource = src
                activeSourceId = src.id

                val cached = withContext(Dispatchers.IO) {
                    ChapterCache.load(context, entry.seriesId).map { src.rehydrateChapter(it) }
                }
                if (cached.isNotEmpty() && activeSeries?.id == entry.seriesId) {
                    chapterList = cached
                }

                val result = withContext(Dispatchers.IO) {
                    val fetched = runCatching { src.restoreSeries(entry.seriesId, entry.title) }
                        .getOrNull()
                    // Only ask the source for chapters if the details fetch
                    // worked — a handle-less series can't list them, and would
                    // come back empty rather than leaving the cache in place.
                    if (fetched == null) null
                    else {
                        val series = fetched.copy(
                            title = fetched.title.ifBlank { entry.title },
                            cover = fetched.cover ?: entry.cover.ifBlank { null }
                        )
                        series to chaptersWithFallback(src, series)
                    }
                }

                if (result == null) {
                    // Offline, or the source is gone. The cache is all there is.
                    if (cached.isEmpty()) {
                        throw IllegalStateException(
                            "No chapter list cached for this series \u2014 open it once online"
                        )
                    }
                } else if (activeSeries?.id == entry.seriesId) {
                    activeSeries = result.first
                    chapterList = result.second
                    enrichSeries(src, result.first)
                }
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not open this series")
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
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not resume")
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
                //
                // Which screen matters now that the challenge is reachable from
                // the series screen too: re-running the browse from there would
                // solve the challenge and then throw away the series the user
                // was trying to open.
                val openSeriesAgain = activeSeries
                if (openSeriesAgain != null) {
                    // openSeries sets the origin to BROWSE unconditionally — see
                    // the note on openGlobalResult. This is a retry, not a fresh
                    // navigation, so back has to still go where it did before.
                    val origin = seriesOrigin
                    openSeries(openSeriesAgain)
                    seriesOrigin = origin
                } else {
                    activeSource?.let { openSource(it, browseQuery, browseMode) }
                }
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
                stillLoading = pagesLoading,
                initialPage = savedPage(context, chKey).coerceIn(0, total - 1),
                seriesTitle = series?.title ?: "",
                chapterName = readerChapter.name,
                chapters = chapterList,
                chapterIndex = chapterIdx,
                hasPrev = chapterIdx > 0,
                hasNext = chapterIdx < chapterList.size - 1,
                onPrev = { openChapter(chapterIdx - 1) },
                onNext = { openChapter(chapterIdx + 1) },
                onSelectChapter = { openChapter(it) },
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
                    // Stop the loader before clearing state. Without this the
                    // job outlives the screen and keeps publishing into it.
                    pageJob?.cancel()
                    pageJob = null
                    // Retire the token with the job. The cancelled load's
                    // `finally` is still to come and must not touch either flag.
                    pageLoadSeq[0]++
                    pagesLoading = false
                    activeChapterIdx = null
                    pages = emptyList()
                    history = History.list(context)
                    readTick++
                }
            )
        }
    } else if (activeSeries != null) {
        // `fun()` rather than a lambda for the same reason as the browse branch
        // below: a brace directly after `else` opens a block, so a lambda there
        // needs a second pair and reads like a typo.
        val seriesSite = activeSource?.siteUrl()
        val solveFromSeries: (() -> Unit)? = if (seriesSite == null) null else fun() {
            challengeUrl = seriesSite
        }
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
            // Scoped to this series, not "is the queue busy". It read
            // DownloadQueue.items.isNotEmpty(), so every series screen showed
            // Stop while any download anywhere was running — and tapping it
            // cancelled all of them.
            downloadingAll = DownloadQueue.hasSeries(activeSeries!!.id),
            onDownload = { ch -> activeSource?.let { downloadChapter(it, ch) } },
            onDownloadAll = { activeSource?.let { downloadAll(it, chapterList) } },
            onCancelDownloads = { cancelSeriesDownloads(activeSeries?.id ?: "") },
            onDeleteDownloads = {
                chapterList.forEach { Downloads.delete(context, it.id) }
                downloadTick++
            },
            // Same primitive as the bulk delete above, one chapter at a time.
            // Downloads.delete already prunes the emptied series and source
            // folders and drops the DownloadPaths entry; DownloadIndex needs no
            // call because list() filters on what's actually complete on disk.
            onDeleteChapter = { ch ->
                Downloads.delete(context, ch.id)
                downloadTick++
            },
            // Explicit value rather than a toggle: a bulk "mark read" over a
            // mixed selection has to end with everything read, and toggling each
            // would flip half of them the wrong way. One readTick bump for the
            // whole batch, not one per chapter.
            onSetRead = { list, value ->
                list.forEach {
                    ReadState.setRead(context, chapterKeyOf(activeSourceId ?: "", it), value)
                }
                readTick++
            },
            // Same batched shape as onSetRead, and the same readTick bump: the
            // chapter rows read their bookmark alongside their read flag, so one
            // signal repaints both rather than adding a second tick nothing else
            // would ever read.
            onSetBookmarked = { list, value ->
                Bookmarks.setBookmarkedBulk(
                    context,
                    list.map { chapterKeyOf(activeSourceId ?: "", it) },
                    value
                )
                readTick++
            },
            loading = isLoading,
            error = errorMessage,
            readTick = readTick,
            scroll = seriesScroll,
            // By id, not by index. The series screen draws a filtered and
            // sorted view now, so its positions are not this list's positions —
            // and openChapter indexes this one. Resolving here keeps chapterList
            // canonical and means the reader's Prev/Next, which are index
            // arithmetic, stay in source order and stay correct.
            //
            // A miss can only mean the list was refetched under the tap, so it
            // does nothing rather than opening chapter 0.
            onOpen = { chapterId ->
                val index = chapterList.indexOfFirst { it.id == chapterId }
                if (index >= 0) openChapter(index)
            },
            onRefresh = { refreshChapters() },
            // Resolved here rather than inside the screen: `Series.handle` is
            // the extension's own object and asking the source for a url is a
            // call across the adapter boundary, which is not a thing to do from
            // inside a composable.
            // Keyed on the **handle**, not just the id. `openFromLibrary` puts
            // a stub on screen first — title and cover, no handle (§4) — and
            // replaces it with the fetched series a moment later under the same
            // id. Keyed on the id alone this resolved against the stub, got
            // null because there was no `SManga` to ask, and never ran again:
            // Share was simply missing on every series opened from the Library.
            //
            // The general form: an id is stable across exactly the transition
            // that fills the object in, so it is the wrong key for anything
            // derived from the object's contents.
            seriesUrl = remember(activeSeries?.handle, activeSourceId) {
                activeSeries?.let { s -> activeSource?.seriesUrl(s) }
            },
            onLibraryChanged = { libraryTick++ },
            // Both leave the series behind deliberately: a tag search is a
            // request to go and look at other things, and the results have to
            // land on a branch below this one to be visible at all.
            onSearchTag = { tag ->
                activeSource?.let { src ->
                    // chapterList is deliberately left alone: it still belongs
                    // to this series, nothing below branch 3 reads it, and
                    // keeping it is what makes coming back instant.
                    tagSearchReturn = activeSeries
                    activeSeries = null
                    errorMessage = null
                    // Keeps the adopted source rather than dropping it the way
                    // onBack does for a non-BROWSE origin — searching *this*
                    // source is the whole request, so the browse screen it falls
                    // through to is the destination, not a stranding.
                    openSource(src, tag, browseMode)
                }
            },
            onGlobalSearchTag = { tag ->
                tagSearchReturn = activeSeries
                activeSeries = null
                errorMessage = null
                globalSearchOpen = true
                runGlobalSearch(tag)
            },
            onSolveChallenge = solveFromSeries,
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
            libraryTick = libraryTick,
            onBack = {
                cancelGlobalSearch()
                globalSearchOpen = false
                seriesList = null
                val cameFromTag = tagSearchReturn
                if (cameFromTag != null) {
                    // Same detour as the per-source case. The adopted-source
                    // problem below doesn't apply: nothing was opened from these
                    // results, so activeSource is still the series' own.
                    tagSearchReturn = null
                    activeSeries = cameFromTag
                } else {
                    // Opening a result adopted that result's source. Leaving
                    // search has to give it back, or the chain lands on a browse
                    // screen with nothing in it.
                    activeSource = null
                    activeSourceId = null
                }
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
            supportsLatest = activeSource!!.supportsLatest,
            supportsFilters = activeSource!!.supportsFilters,
            onOpenFilters = { filtersOpen = true },
            onDiagnose = { probeOpen = true },
            mode = browseMode,
            onModeChange = { m -> activeSource?.let { openSource(it, "", m) } },
            query = browseQuery,
            hasNext = browseHasNext,
            loadingMore = loadingMore,
            onSearch = { q -> activeSource?.let { openSource(it, q, browseMode) } },
            onLoadMore = { loadMoreSeries() },
            onRescan = { activeSource?.let { openSource(it, browseQuery, browseMode) } },
            onOpen = { openSeries(it) },
            onBack = {
                seriesList = null
                errorMessage = null
                val cameFromTag = tagSearchReturn
                if (cameFromTag != null) {
                    // Back to the series the tag was on. The source stays: it is
                    // that series' own source, which the series screen needs for
                    // chapters and pages.
                    tagSearchReturn = null
                    activeSeries = cameFromTag
                } else {
                    activeSource = null
                    activeSourceId = null
                }
            },
            libraryTick = libraryTick,
            // Local folder sources carry their own ids; everything loaded from
            // an extension is prefixed, which is the same test the library grid
            // uses for its Local chip.
            isLocalSource = !(activeSourceId ?: "").startsWith("tachi:"),
            scroll = browseScroll,
            onSolveChallenge = startChallenge
        )
    } else if (downloadsOpen) {
        // Above settings on purpose, and it's the ordering that does the work.
        // The queue is reachable from More *and* from Settings > Downloads, and
        // leaving `settingsOpen` set while this renders means backing out of the
        // queue falls through to whichever of the two it was opened from — no
        // "where did I come from" flag, just two booleans read in order.
        DownloadQueueScreen(onBack = { downloadsOpen = false })
    } else if (settingsOpen) {
        SettingsScreen(
            onBack = { settingsOpen = false },
            onOpenDownloadQueue = { downloadsOpen = true }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        label = { NavLabel("Library") },
                        icon = { Text("📚") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        label = { NavLabel("Browse") },
                        icon = { Text("🧭") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            currentTab = 2
                            history = History.list(context)
                        },
                        label = { NavLabel("History") },
                        icon = { Text("🕒") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        label = { NavLabel("Downloads") },
                        icon = { Text("⬇️") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        label = { NavLabel("More") },
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
                        activeCategory = libraryCategory,
                        onCategoryChange = {
                            libraryCategory = it
                            LibraryPrefs.setLastCategory(context, it)
                        },
                        search = librarySearch,
                        onSearchChange = { librarySearch = it },
                        searchOpen = librarySearchOpen,
                        onSearchOpenChange = { librarySearchOpen = it },
                        scroll = libraryScroll,
                        onOpen = { openFromLibrary(it) },
                        onRemoveMany = { ids ->
                            // removeAll, not remove-in-a-loop: each remove()
                            // rewrites the whole library JSON, so a hundred
                            // selected entries would be a hundred growing
                            // serialisations. Same reason mergeAll exists.
                            Library.removeAll(context, ids)
                            libraryTick++
                        }
                    )
                    1 -> BrowseTab(
                        configs = configs,
                        extensions = extensionSources,
                        scroll = sourcesScroll,
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
                        libraryTick = libraryTick,
                        onClearAll = {
                            History.list(context).forEach { History.remove(context, it.chapterKey) }
                            history = History.list(context)
                        },
                        // The same re-read every other mutation here does, so
                        // the gesture and the existing paths cannot drift.
                        onRefresh = { history = History.list(context) }
                    )
                    3 -> DownloadsTab(
                        downloadTick = downloadTick + DownloadQueue.tick,
                        libraryTick = libraryTick,
                        onOpen = { openFromDownloads(it) },
                        onOpenQueue = { downloadsOpen = true }
                    )
                    4 -> MoreTab(
                        onOpenDownloads = { downloadsOpen = true },
                        onOpenSettings = { settingsOpen = true }
                    )
                }
            }
        }
    }

    val probeSourceRef = activeSource
    if (probeOpen && probeSourceRef != null) {
        NetworkProbeDialog(source = probeSourceRef, onDismiss = { probeOpen = false })
    }

    val filterSource = activeSource
    if (filtersOpen && filterSource != null) {
        SourceFilterDialog(
            source = filterSource,
            onApply = {
                filtersOpen = false
                // Re-runs the listing as a filtered search from page 1. The
                // filters themselves live on the adapter, so nothing about them
                // has to be carried through here.
                openSource(filterSource, "", BrowseMode.FILTER)
            },
            onDismiss = { filtersOpen = false }
        )
    }

    if (whatsNewOpen) {
        WhatsNewDialog(
            notes = releaseNotes,
            onDismiss = {
                whatsNewOpen = false
                WhatsNew.markSeen(context)
            }
        )
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
 * A bottom-nav label that can't wrap.
 *
 * A fifth of the screen fits four of these words and not "Downloads", which
 * broke onto a second line and left an orphaned "s" under the icon. Wrapping is
 * never the right answer in a fixed-height bar, so every label is pinned to one
 * line and the longest is allowed to shrink instead — applied to all five rather
 * than just the offender, because a single odd one out is more noticeable than
 * five slightly smaller labels.
 *
 * Guarding with maxLines alone would clip "Download"; the smaller style is what
 * actually makes it fit, and the ellipsis is only insurance for a font scale
 * larger than any tested here.
 */
@Composable
private fun NavLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall
    )
}

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
