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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

// ---------- root ----------

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
    var history by remember { mutableStateOf(History.forDisplay(context)) }

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
    // Past queries, newest first — hoisted so the empty-state chips update the
    // instant a search runs, without the screen re-reading prefs.
    var globalRecents by remember { mutableStateOf(SourcePrefs.recentSearches(context)) }

    // Source migration. When [migrateFrom] is set, the global-search screen is
    // the target picker for moving that library series to another source, and a
    // tapped result opens the confirm dialog ([migrateTarget]) instead of the
    // series.
    var migrateFrom by remember { mutableStateOf<MigrateFrom?>(null) }
    var migrateTarget by remember { mutableStateOf<Pair<Source, Series>?>(null) }

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

    // Whether a chapter fetch has COMPLETED for the series on screen.
    //
    // chapterList is empty in three different situations — nothing fetched yet,
    // a fetch that failed, and a fetch that genuinely returned nothing — and the
    // series screen was asserting the third whenever it saw the first. It said
    // "This source returned no chapters" while the request was still in flight.
    //
    // The app still cannot tell a genuine zero from an extension whose selector
    // matched nothing; that information does not exist anywhere, because Jsoup's
    // select() returns an empty set either way. What it CAN now tell is whether
    // it has an answer at all, which is the difference between a claim and a
    // guess.
    var chaptersFetched by remember { mutableStateOf(false) }

    // Video links found on a chapter's page, for handing to an external player.
    // null = never asked, empty = asked and found none — the same three-state
    // shape as chaptersFetched, and for the same reason: "no videos" and "not
    // looked yet" are different sentences.
    var videoScan by remember { mutableStateOf<VideoScan?>(null) }
    // url to open, and the page it is embedded on. The second half is the
    // Referer, and without it cossora.stream answers "Unknown Error xD".
    var openEmbed by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Media urls scraped out of a player, for handing to an external app.
    var mediaUrls by remember { mutableStateOf<List<String>?>(null) }
    var videoScanning by remember { mutableStateOf(false) }

    val activity = context as? ComponentActivity
    DoubleBackToExitHandler(activity)

    DownloadQueueIntentHandler(
        activity = activity,
        onOpenQueue = {
            currentTab = 3
            downloadsOpen = true
        }
    )
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

    ExtensionResumeObserver(
        activity = activity,
        onSourcesChanged = { extensionSources = it }
    )

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
        globalRecents = SourcePrefs.addRecentSearch(context, query)
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
        chaptersFetched = false
        errorMessage = null
        enrichSeries(src, series)
        scope.launch {
            isLoading = true
            try {
                chapterList = withContext(Dispatchers.IO) {
                    src.listChapters(series).also { ChapterCache.save(context, series.id, it) }
                }
                chaptersFetched = true
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
    /**
     * Scans one chapter's page for videos. See Source.scanVideos.
     *
     * The CALLER picks which — the chapter at the top of the list as sorted and
     * filtered on screen. Choosing it here meant chapterList[0], the raw list's
     * first entry, which under a descending sort is the last one drawn.
     *
     * A failure comes back as a VideoScan carrying the reason rather than as a
     * thrown error, so "the scan failed" and "the page has no videos" reach the
     * dialog as different sentences. They looked identical in 0.173 and that is
     * exactly the failure this codebase keeps writing cards about.
     */
    fun findVideos(chapter: Chapter) {
        val src = activeSource ?: return
        videoScan = null
        videoScanning = true
        scope.launch {
            videoScan = try {
                withContext(Dispatchers.IO) { src.scanVideos(chapter) }
            } catch (e: Throwable) {
                VideoScan(emptyList(), note = sourceFailureMessage(e, "The scan failed"))
            }
            videoScanning = false
        }
    }

    fun refreshChapters() {
        val src = activeSource ?: return
        val series = activeSeries ?: return
        errorMessage = null
        // A manual refresh is the user asserting "re-check this series", and that
        // includes its download state, not only its chapter list. The per-chapter
        // download ticks and the cover badge both answer from Downloads.isComplete,
        // which memoises — so without this a chapter deleted with a file manager
        // keeps its downloaded marker through a refresh. invalidateCompletion drops
        // that memo (and the index) the same way the Downloads and Library pulls
        // do; downloadTick++ makes this screen's isComplete reads recompute.
        Downloads.invalidateCompletion()
        downloadTick++
        enrichSeries(src, series)
        scope.launch {
            isLoading = true
            try {
                chapterList = withContext(Dispatchers.IO) {
                    src.listChapters(series).also { ChapterCache.save(context, series.id, it) }
                }
                chaptersFetched = true
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

    // ---------- library bulk actions ----------

    /**
     * A selection's chapters, cache first and the source only as a fallback.
     *
     * The cached list is what most library entries already have — every series
     * that has been opened or that a refresh has swept — so the common path
     * makes no request at all. A series with nothing cached is resolved through
     * its source exactly as [openFromLibrary] does (`restoreSeries` then
     * `listChapters`), and an empty return means "couldn't load it", which the
     * callers count and report rather than silently doing nothing.
     *
     * Always on [Dispatchers.IO]: called from a loop over the selection, each
     * iteration a pref read and possibly two requests.
     */
    suspend fun chaptersForBulk(entry: LibraryEntry, src: Source?): List<Chapter> =
        withContext(Dispatchers.IO) {
            val cached = ChapterCache.load(context, entry.seriesId)
                .map { src?.rehydrateChapter(it) ?: it }
            if (cached.isNotEmpty()) return@withContext cached
            if (src == null) return@withContext emptyList()
            val series = runCatching { src.restoreSeries(entry.seriesId, entry.title) }
                .getOrNull() ?: return@withContext emptyList()
            runCatching { src.listChapters(series) }
                .onSuccess { if (it.isNotEmpty()) ChapterCache.save(context, entry.seriesId, it) }
                .getOrDefault(emptyList())
        }

    /**
     * Marks every chapter of each selected series read (or unread) at once.
     *
     * The read flags and the [SeriesIndex] counts are the same two halves a
     * single series keeps in step: the flags come first through [ReadState], then
     * [SeriesIndex.record] recomputes the stored read count from them so the
     * unread badge and the Unread/Started/Completed filters move with the change.
     * `record` per series is one index write each, which is fine for a selection
     * and is not the whole-library loop its KDoc warns off.
     */
    fun bulkSetRead(ids: Set<String>, value: Boolean) {
        if (ids.isEmpty()) return
        scope.launch {
            val (changed, skipped) = withContext(Dispatchers.IO) {
                val sources = SourceManager.listAllSources(context).associateBy { it.id }
                val entries = Library.list(context).filter { it.seriesId in ids }
                var changed = 0
                var skipped = 0
                for (entry in entries) {
                    val chapters = chaptersForBulk(entry, sources[entry.sourceId])
                    if (chapters.isEmpty()) { skipped++; continue }
                    chapters.forEach {
                        ReadState.setRead(context, chapterKeyOf(entry.sourceId, it), value)
                    }
                    SeriesIndex.record(context, entry.sourceId, entry.seriesId, chapters)
                    changed++
                }
                changed to skipped
            }
            libraryTick++
            val verb = if (value) "read" else "unread"
            val msg = buildString {
                append("Marked $changed series $verb")
                if (skipped > 0) append(" · $skipped skipped (no chapter list)")
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Queues every chapter of each selected series for download.
     *
     * Builds the [DownloadItem]s straight from the [LibraryEntry] rather than
     * routing through [queueDownloads], which reads title and cover off
     * `activeSeries` — there is no active series here. `enqueue` drops anything
     * already downloaded or already queued, so re-running this is safe and the
     * count reported is only what it actually added.
     */
    fun bulkDownload(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            val (items, skipped) = withContext(Dispatchers.IO) {
                val sources = SourceManager.listAllSources(context).associateBy { it.id }
                val entries = Library.list(context).filter { it.seriesId in ids }
                val items = ArrayList<DownloadItem>()
                var skipped = 0
                for (entry in entries) {
                    val chapters = chaptersForBulk(entry, sources[entry.sourceId])
                    if (chapters.isEmpty()) { skipped++; continue }
                    chapters.forEach { ch ->
                        items.add(
                            DownloadItem(
                                sourceId = entry.sourceId,
                                chapterId = ch.id,
                                chapterName = ch.name,
                                seriesTitle = entry.title,
                                seriesId = entry.seriesId,
                                cover = entry.cover
                            )
                        )
                    }
                }
                items to skipped
            }
            val added = DownloadQueue.enqueue(context, items)
            if (added > 0) {
                if (DownloadQueue.paused) DownloadQueue.setPaused(context, false)
                DownloadService.start(context)
            }
            downloadTick++
            val msg = buildString {
                append(
                    if (added > 0) "Queued $added ${if (added == 1) "chapter" else "chapters"}"
                    else "Nothing to download — already downloaded or queued"
                )
                if (skipped > 0) append(" · $skipped skipped (no chapter list)")
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Moves a saved series from one source to another.
     *
     * The point of migration is a source that has gone dead or lost the series:
     * the entry is re-created under [toSource]/[toSeries], its categories are
     * carried over, and read progress is transferred **by chapter number** —
     * the only key both sides share, since chapter ids are per-source. A target
     * chapter counts as read when a numbered source chapter with the same number
     * was read; anything unnumbered is left alone rather than guessed at. The old
     * entry, its categories, and its index row are then removed.
     *
     * Best-effort by design: if the target's chapter list can't be fetched the
     * entry still moves (so a dead source isn't a trap), it just carries no
     * transferred progress.
     */
    fun performMigration(from: MigrateFrom, toSource: Source, toSeries: Series) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val readNumbers = ChapterCache.load(context, from.seriesId)
                        .filter {
                            it.number > Chapter.NO_NUMBER &&
                                ReadState.isRead(context, chapterKeyOf(from.sourceId, it))
                        }
                        .map { it.number }
                        .toSet()

                    val toChapters = runCatching { toSource.listChapters(toSeries) }
                        .getOrDefault(emptyList())
                    if (toChapters.isNotEmpty()) {
                        ChapterCache.save(context, toSeries.id, toChapters)
                        val readKeys = toChapters
                            .filter { it.number > Chapter.NO_NUMBER && it.number in readNumbers }
                            .map { chapterKeyOf(toSource.id, it) }
                        ReadState.setReadBulk(context, readKeys)
                        SeriesIndex.record(context, toSource.id, toSeries.id, toChapters)
                    }

                    val cats = Categories.categoriesFor(context, from.seriesId)
                    val cover = (toSeries.cover as? String)
                        ?: (toSeries.cover as? File)?.absolutePath
                        ?: ""
                    Library.add(
                        context,
                        LibraryEntry(
                            seriesId = toSeries.id,
                            sourceId = toSource.id,
                            title = toSeries.title,
                            cover = cover,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                    Categories.setCategoriesFor(context, toSeries.id, cats)

                    // Only after the new entry is in place: a crash between the
                    // two would otherwise lose the series from the library
                    // entirely rather than leaving the old copy to retry from.
                    Library.remove(context, from.seriesId)
                    Categories.setCategoriesFor(context, from.seriesId, emptySet())
                    SeriesIndex.forget(context, setOf(from.seriesId))
                }.isSuccess
            }
            migrateTarget = null
            migrateFrom = null
            if (ok) {
                cancelGlobalSearch()
                globalSearchOpen = false
                seriesList = null
                tagSearchReturn = null
                activeSeries = null
                activeSource = null
                activeSourceId = null
                currentTab = 0
                libraryTick++
                android.widget.Toast
                    .makeText(context, "Migrated to ${toSource.name}", android.widget.Toast.LENGTH_SHORT)
                    .show()
            } else {
                errorMessage = "Couldn't migrate this series"
            }
        }
    }

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
        chaptersFetched = false
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
                    chaptersFetched = true
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
        chaptersFetched = false
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
                    chaptersFetched = true
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

    // Same shape and same justification as the challenge branch below: gated on
    // state that is null in every other flow, and clearing it drops back onto
    // whatever was underneath. Placed BELOW the challenge so a Cloudflare wall
    // still wins — a player is never more urgent than being able to reach the
    // site at all.
    val embed = openEmbed
    if (challengeUrl == null && embed != null) {
        EmbedPlayerRoute(
            embed = embed,
            media = mediaUrls,
            onMediaFound = { mediaUrls = it },
            onDismissMedia = { mediaUrls = null },
            onBack = { openEmbed = null },
            onPlayerError = { errorMessage = it }
        )
        return
    }

    val challenge = challengeUrl
    if (challenge != null) {
        // Sits above every other branch, and safely so: it's gated on state that
        // is null in every other flow, and clearing that state drops back onto
        // whatever was underneath with nothing else touched. No branch below has
        // to know this one exists — which is the only reason it was safe to put
        // anything at the top of this chain.
        ChallengeRoute(
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
                    history = History.forDisplay(context)
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
            // Empty means three different things; this says which. See its
            // declaration.
            chaptersFetched = chaptersFetched,
            onFindVideos = { chapter -> findVideos(chapter) },
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
                    // Search BY GENRE when the source has a matching filter: a
                    // tag is a genre, so tapping "Romance" should list what is
                    // tagged Romance, not what has "Romance" in its title. When
                    // the source has no such filter, fall back to the title
                    // search — many still match a genre inside their text query.
                    //
                    // Keeps the adopted source rather than dropping it the way
                    // onBack does for a non-BROWSE origin — searching *this*
                    // source is the whole request, so the browse screen it falls
                    // through to is the destination, not a stranding.
                    if (src.applyGenreFilter(tag)) {
                        openSource(src, "", BrowseMode.FILTER)
                    } else {
                        openSource(src, tag, browseMode)
                    }
                }
            },
            onGlobalSearchTag = { tag ->
                tagSearchReturn = activeSeries
                activeSeries = null
                errorMessage = null
                globalSearchOpen = true
                runGlobalSearch(tag)
            },
            onMigrate = {
                val s = activeSeries
                val sid = activeSourceId
                if (s != null && sid != null) {
                    // Same detour mechanism the tag search uses: remember the
                    // series so Back from the picker returns to it, and open the
                    // global-search screen — which becomes the target picker
                    // while migrateFrom is set — pre-seeded with the title.
                    migrateFrom = MigrateFrom(s.id, sid, s.title)
                    tagSearchReturn = s
                    activeSeries = null
                    errorMessage = null
                    globalSearchOpen = true
                    runGlobalSearch(s.title)
                }
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
            recents = globalRecents,
            onRemoveRecent = { globalRecents = SourcePrefs.removeRecentSearch(context, it) },
            onClearRecents = {
                SourcePrefs.clearRecentSearches(context)
                globalRecents = emptyList()
            },
            onSearch = { runGlobalSearch(it) },
            onCancel = { cancelGlobalSearch() },
            onOpenSource = { openGlobalSource(it) },
            migrating = migrateFrom != null,
            onOpenSeries = { src, s ->
                // In migrate mode a tapped result is the chosen target, not a
                // series to open — confirm before moving anything.
                if (migrateFrom != null) migrateTarget = src to s
                else openGlobalResult(src, s)
            },
            libraryTick = libraryTick,
            onBack = {
                cancelGlobalSearch()
                globalSearchOpen = false
                migrateFrom = null
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
        migrateTarget?.let { (targetSource, targetSeries) ->
            val from = migrateFrom
            AlertDialog(
                onDismissRequest = { migrateTarget = null },
                title = { Text("Migrate series") },
                text = {
                    Text(
                        "Move “${from?.title}” to ${targetSource.name}? " +
                            "Your categories and read progress move with it, and the " +
                            "old entry is removed."
                    )
                },
                confirmButton = {
                    Button(
                        enabled = from != null,
                        onClick = { if (from != null) performMigration(from, targetSource, targetSeries) }
                    ) { Text("Migrate") }
                },
                dismissButton = {
                    TextButton(onClick = { migrateTarget = null }) { Text("Cancel") }
                }
            )
        }
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
                MainBottomNavigation(
                    currentTab = currentTab,
                    onSelectTab = { tab ->
                        currentTab = tab
                        if (tab == 2) {
                            history = History.forDisplay(context)
                        }
                    }
                )
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
                        },
                        onMarkRead = { ids -> bulkSetRead(ids, true) },
                        onMarkUnread = { ids -> bulkSetRead(ids, false) },
                        onDownloadMany = { ids -> bulkDownload(ids) }
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
                            history = History.forDisplay(context)
                        },
                        libraryTick = libraryTick,
                        onClearAll = {
                            History.list(context).forEach { History.remove(context, it.chapterKey) }
                            history = History.forDisplay(context)
                        },
                        // The same re-read every other mutation here does, so
                        // the gesture and the existing paths cannot drift.
                        onRefresh = { history = History.forDisplay(context) }
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

    val scan = videoScan
    if (videoScanning || scan != null) {
        ChapterVideoDialog(
            scanning = videoScanning,
            scan = scan,
            onDismiss = { videoScan = null },
            onOpenEmbed = { url ->
                val page = activeSeries?.let { series ->
                    activeSource?.seriesUrl(series)
                }
                videoScan = null
                openEmbed = url to (page ?: "")
            },
            onOpenVideo = { url ->
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(view) }
                    .onFailure {
                        errorMessage = "No app on this device can play that link"
                    }
            }
        )
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
