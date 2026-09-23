package com.mangareader.app

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val browseState = remember { SourceBrowseState() }
    var filtersOpen by remember { mutableStateOf(false) }
    var probeOpen by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<Series?>(null) }
    var chapterList by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    val readerSession = remember { ReaderSessionState() }

    // Hoisted so results survive opening a series and navigating back.
    val globalSearch = remember { GlobalSearchState(context) }

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

    // Live download state lives in DownloadQueue. This local tick covers
    // filesystem mutations caused directly by this screen.
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
        browseState.resetListing(source, query, mode)
        // Feeds the "Last used" section at the top of the Sources list.
        SourcePrefs.setLastUsed(context, source.id)
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                val page = withContext(Dispatchers.IO) {
                    loadSourcePage(source, query, mode, 1)
                }
                browseState.series = page.series
                browseState.hasNext = page.hasNext
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not scan this source")
                browseState.series = emptyList()
            }
            isLoading = false
        }
    }

    /** Appends the next page to the current browse/search results. */
    fun loadMoreSeries() {
        val source = browseState.source ?: return
        if (browseState.loadingMore || !browseState.hasNext) return
        scope.launch {
            browseState.loadingMore = true
            val next = browseState.page + 1
            try {
                val page = withContext(Dispatchers.IO) {
                    loadSourcePage(source, browseState.query, browseState.mode, next)
                }
                browseState.series = (browseState.series ?: emptyList()) + page.series
                browseState.page = next
                browseState.hasNext = page.hasNext
            } catch (e: Throwable) {
                errorMessage = sourceFailureMessage(e, "Could not load more")
                browseState.hasNext = false
            }
            browseState.loadingMore = false
        }
    }

    fun runGlobalSearch(query: String) {
        globalSearch.search(
            context = context,
            scope = scope,
            configs = configs,
            extensions = extensionSources,
            newQuery = query
        )
    }

    fun cancelGlobalSearch() {
        globalSearch.cancel()
    }

    fun setGlobalPinnedOnly(value: Boolean) {
        globalSearch.setPinnedOnly(
            context = context,
            scope = scope,
            configs = configs,
            extensions = extensionSources,
            value = value
        )
    }

    fun openSourceConfig(config: SourceConfig) {
        val source = resolveSourceConfig(context, config)
        if (source == null) {
            errorMessage = "\"${config.label}\" isn't configured yet"
            return
        }
        openSource(source)
    }

    fun enrichSeries(source: Source, series: Series) {
        scope.launch {
            val enriched = withContext(Dispatchers.IO) {
                loadAndHealSeriesDetails(
                    context,
                    source,
                    series
                )
            }
            if (enriched != null && activeSeries?.id == series.id) {
                activeSeries = enriched
            }
        }
    }

    fun openSeries(series: Series) {
        val src = browseState.source ?: return
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
                    loadSeriesChapters(context, src, series)
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
        val source = browseState.source ?: return
        videoScan = null
        videoScanning = true
        scope.launch {
            videoScan = withContext(Dispatchers.IO) {
                scanChapterVideos(source, chapter)
            }
            videoScanning = false
        }
    }

    fun refreshChapters() {
        val src = browseState.source ?: return
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
                    loadSeriesChapters(context, src, series)
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
        browseState.source = source
        browseState.sourceId = source.id
        browseState.series = null
        browseState.page = 1
        browseState.hasNext = false
        browseState.query = globalSearch.query
        openSeries(series)
        // After openSeries, which sets it to BROWSE unconditionally.
        seriesOrigin = SeriesOrigin.GLOBAL_SEARCH
    }

    /** "See all" on a global search row: leave the results and browse that source. */
    fun openGlobalSource(source: Source) {
        cancelGlobalSearch()
        globalSearch.open = false
        openSource(source, globalSearch.query)
    }

    fun openChapter(index: Int) {
        val source = browseState.source ?: return
        readerSession.open(
            context = context,
            scope = scope,
            source = source,
            chapters = chapterList,
            index = index,
            onRootLoadingChanged = { isLoading = it },
            onClearError = { errorMessage = null },
            onError = { errorMessage = it }
        )
    }

    fun queueDownloads(source: Source, chapters: List<Chapter>) {
        queueSeriesDownloads(
            context = context,
            series = activeSeries,
            source = source,
            chapters = chapters
        )
    }

    fun downloadChapter(source: Source, chapter: Chapter) =
        queueDownloads(source, listOf(chapter))

    fun downloadAll(source: Source, chapters: List<Chapter>) =
        queueDownloads(source, chapters)

    fun bulkSetRead(ids: Set<String>, value: Boolean) {
        if (ids.isEmpty()) return

        scope.launch {
            val result = setLibrarySeriesRead(
                context = context,
                ids = ids,
                value = value
            )
            libraryTick++

            val verb = if (value) "read" else "unread"
            val message = buildString {
                append("Marked ${result.changed} series $verb")
                if (result.skipped > 0) {
                    append(" · ${result.skipped} skipped (no chapter list)")
                }
            }
            android.widget.Toast
                .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun bulkDownload(ids: Set<String>) {
        if (ids.isEmpty()) return

        scope.launch {
            val result = queueLibraryDownloads(
                context = context,
                ids = ids
            )
            downloadTick++

            val message = buildString {
                append(
                    if (result.added > 0) {
                        "Queued ${result.added} " +
                            if (result.added == 1) "chapter" else "chapters"
                    } else {
                        "Nothing to download — already downloaded or queued"
                    }
                )
                if (result.skipped > 0) {
                    append(" · ${result.skipped} skipped (no chapter list)")
                }
            }
            android.widget.Toast
                .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun performMigration(
        from: MigrateFrom,
        toSource: Source,
        toSeries: Series
    ) {
        scope.launch {
            val migrated = migrateLibrarySeries(
                context = context,
                from = from,
                toSource = toSource,
                toSeries = toSeries
            )

            migrateTarget = null
            migrateFrom = null

            if (migrated) {
                cancelGlobalSearch()
                globalSearch.open = false
                browseState.series = null
                tagSearchReturn = null
                activeSeries = null
                browseState.source = null
                browseState.sourceId = null
                currentTab = 0
                libraryTick++
                android.widget.Toast
                    .makeText(
                        context,
                        "Migrated to ${toSource.name}",
                        android.widget.Toast.LENGTH_SHORT
                    )
                    .show()
            } else {
                errorMessage = "Couldn't migrate this series"
            }
        }
    }

    fun cancelSeriesDownloads(seriesId: String) {
        if (cancelSeriesDownloadsAction(context, seriesId)) {
            downloadTick++
        }
    }

    /** Reopen a saved series: resolve its source, then re-fetch its chapter list. */
    fun openFromLibrary(entry: LibraryEntry) {
        errorMessage = null
        seriesOrigin = SeriesOrigin.LIBRARY
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
                val source = withContext(Dispatchers.IO) {
                    findInstalledSource(context, entry.sourceId)
                }
                browseState.source = source
                browseState.sourceId = source.id

                val cached = withContext(Dispatchers.IO) {
                    loadCachedChapters(
                        context,
                        source,
                        entry.seriesId
                    )
                }
                if (cached.isNotEmpty() && activeSeries?.id == entry.seriesId) {
                    chapterList = cached
                }

                val result = withContext(Dispatchers.IO) {
                    resolveLibrarySeries(
                        context,
                        source,
                        entry
                    )
                }
                if (activeSeries?.id == entry.seriesId) {
                    activeSeries = result.first
                    chapterList = result.second
                    chaptersFetched = true
                    enrichSeries(source, result.first)
                }
            } catch (error: Throwable) {
                errorMessage = sourceFailureMessage(
                    error,
                    "Could not open this series"
                )
            }
            isLoading = false
        }
    }

    /** Opens a downloaded series from cache first, then refreshes it when possible. */
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
                val source = withContext(Dispatchers.IO) {
                    findInstalledSource(context, entry.sourceId)
                }
                browseState.source = source
                browseState.sourceId = source.id

                val cached = withContext(Dispatchers.IO) {
                    loadCachedChapters(
                        context,
                        source,
                        entry.seriesId
                    )
                }
                if (cached.isNotEmpty() && activeSeries?.id == entry.seriesId) {
                    chapterList = cached
                }

                val resolved = withContext(Dispatchers.IO) {
                    resolveDownloadedSeries(
                        context,
                        source,
                        entry
                    )
                }

                if (resolved == null) {
                    if (cached.isEmpty()) {
                        throw IllegalStateException(
                            "No chapter list cached for this series — open it once online"
                        )
                    }
                } else if (activeSeries?.id == entry.seriesId) {
                    activeSeries = resolved.first
                    chapterList = resolved.second
                    chaptersFetched = true
                    enrichSeries(source, resolved.first)
                }
            } catch (error: Throwable) {
                errorMessage = sourceFailureMessage(
                    error,
                    "Could not open this series"
                )
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
                    loadHistoryResumeTarget(context, entry)
                }
                seriesOrigin = SeriesOrigin.HISTORY
                browseState.source = target.source
                browseState.sourceId = target.source.id
                activeSeries = target.series
                chapterList = target.chapters
                enrichSeries(target.source, target.series)
                openChapter(target.index)
            } catch (error: Throwable) {
                errorMessage = sourceFailureMessage(
                    error,
                    "Could not resume"
                )
            }
            isLoading = false
        }
    }

    // ---- routing ----

    val chapterIdx = readerSession.chapterIndex
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
                    browseState.source?.let { openSource(it, browseState.query, browseState.mode) }
                }
            },
            onBack = { challengeUrl = null }
        )
    } else if (chapterIdx != null && readerChapter != null && readerSession.pages.isNotEmpty()) {
        ReaderRoute(
            pages = readerSession.pages,
            stillLoading = readerSession.loading,
            sourceId = browseState.sourceId ?: "",
            series = activeSeries,
            chapter = readerChapter,
            chapters = chapterList,
            chapterIndex = chapterIdx,
            onOpenChapter = { openChapter(it) },
            onClose = {
                readerSession.close()
                history = History.forDisplay(context)
                readTick++
            }
        )
    } else if (activeSeries != null) {
        val series = activeSeries!!
        val seriesSite = browseState.source?.siteUrl()
        val solveFromSeries: (() -> Unit)? = if (seriesSite == null) null else fun() {
            challengeUrl = seriesSite
        }
        SeriesRoute(
            series = series,
            chapters = chapterList,
            chaptersFetched = chaptersFetched,
            source = browseState.source,
            sourceId = browseState.sourceId,
            loading = isLoading,
            error = errorMessage,
            readTick = readTick,
            scroll = seriesScroll,
            localDownloadTick = downloadTick,
            onFindVideos = { findVideos(it) },
            onDownload = { src, chapter -> downloadChapter(src, chapter) },
            onDownloadAll = { src, chapters -> downloadAll(src, chapters) },
            onCancelDownloads = { cancelSeriesDownloads(it) },
            onDownloadStateChanged = { downloadTick++ },
            onOpenChapter = { openChapter(it) },
            onRefresh = { refreshChapters() },
            onReadStateChanged = { readTick++ },
            onLibraryChanged = { libraryTick++ },
            onSearchTag = { tag ->
                browseState.source?.let { src ->
                    tagSearchReturn = activeSeries
                    activeSeries = null
                    errorMessage = null
                    if (src.applyGenreFilter(tag)) {
                        openSource(src, "", BrowseMode.FILTER)
                    } else {
                        openSource(src, tag, browseState.mode)
                    }
                }
            },
            onGlobalSearchTag = { tag ->
                tagSearchReturn = activeSeries
                activeSeries = null
                errorMessage = null
                globalSearch.open = true
                runGlobalSearch(tag)
            },
            onMigrate = {
                val s = activeSeries
                val sid = browseState.sourceId
                if (s != null && sid != null) {
                    migrateFrom = MigrateFrom(s.id, sid, s.title)
                    tagSearchReturn = s
                    activeSeries = null
                    errorMessage = null
                    globalSearch.open = true
                    runGlobalSearch(s.title)
                }
            },
            onSolveChallenge = solveFromSeries,
            onBack = {
                activeSeries = null
                chapterList = emptyList()
                errorMessage = null
                if (seriesOrigin != SeriesOrigin.BROWSE) {
                    browseState.source = null
                    browseState.sourceId = null
                    browseState.series = null
                }
            }
        )
    } else if (globalSearch.open) {
        GlobalSearchRoute(
            query = globalSearch.query,
            results = globalSearch.results,
            running = globalSearch.running,
            done = globalSearch.done,
            total = globalSearch.total,
            pinnedOnly = globalSearch.pinnedOnly,
            onTogglePinnedOnly = { setGlobalPinnedOnly(it) },
            hasResultsOnly = globalSearch.hasResultsOnly,
            onToggleHasResultsOnly = { globalSearch.hasResultsOnly = it },
            recents = globalSearch.recents,
            onRemoveRecent = { globalSearch.recents = SourcePrefs.removeRecentSearch(context, it) },
            onClearRecents = {
                SourcePrefs.clearRecentSearches(context)
                globalSearch.recents = emptyList()
            },
            onSearch = { runGlobalSearch(it) },
            onCancel = { cancelGlobalSearch() },
            onOpenSource = { openGlobalSource(it) },
            migrating = migrateFrom != null,
            onOpenSeries = { src, s ->
                if (migrateFrom != null) migrateTarget = src to s
                else openGlobalResult(src, s)
            },
            libraryTick = libraryTick,
            onBack = {
                cancelGlobalSearch()
                globalSearch.open = false
                migrateFrom = null
                browseState.series = null
                val cameFromTag = tagSearchReturn
                if (cameFromTag != null) {
                    tagSearchReturn = null
                    activeSeries = cameFromTag
                } else {
                    browseState.source = null
                    browseState.sourceId = null
                }
            },
            migrateFrom = migrateFrom,
            migrateTarget = migrateTarget,
            onDismissMigration = { migrateTarget = null },
            onConfirmMigration = { from, targetSource, targetSeries ->
                performMigration(from, targetSource, targetSeries)
            }
        )
    } else if (browseState.source != null) {
        val source = browseState.source!!
        val site = source.siteUrl()
        val startChallenge: (() -> Unit)? = if (site == null) null else fun() {
            challengeUrl = site
        }
        SourceBrowseRoute(
            source = source,
            sourceId = browseState.sourceId,
            series = browseState.series,
            loading = isLoading,
            error = errorMessage,
            filtersOpen = { filtersOpen = true },
            diagnose = { probeOpen = true },
            mode = browseState.mode,
            onModeChange = { m -> openSource(source, "", m) },
            query = browseState.query,
            hasNext = browseState.hasNext,
            loadingMore = browseState.loadingMore,
            onSearch = { q -> openSource(source, q, browseState.mode) },
            onLoadMore = { loadMoreSeries() },
            onRescan = { openSource(source, browseState.query, browseState.mode) },
            onOpen = { openSeries(it) },
            onBack = {
                browseState.series = null
                errorMessage = null
                val cameFromTag = tagSearchReturn
                if (cameFromTag != null) {
                    tagSearchReturn = null
                    activeSeries = cameFromTag
                } else {
                    browseState.source = null
                    browseState.sourceId = null
                }
            },
            libraryTick = libraryTick,
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
        MainTabsRoute(
            currentTab = currentTab,
            onSelectTab = { tab ->
                currentTab = tab
                if (tab == 2) {
                    history = History.forDisplay(context)
                }
            },
            libraryTick = libraryTick,
            error = errorMessage,
            libraryCategory = libraryCategory,
            onLibraryCategoryChange = {
                libraryCategory = it
                LibraryPrefs.setLastCategory(context, it)
            },
            librarySearch = librarySearch,
            onLibrarySearchChange = { librarySearch = it },
            librarySearchOpen = librarySearchOpen,
            onLibrarySearchOpenChange = { librarySearchOpen = it },
            libraryScroll = libraryScroll,
            onOpenLibrary = { openFromLibrary(it) },
            onRemoveLibraryMany = { ids ->
                Library.removeAll(context, ids)
                libraryTick++
            },
            onMarkRead = { ids -> bulkSetRead(ids, true) },
            onMarkUnread = { ids -> bulkSetRead(ids, false) },
            onDownloadMany = { ids -> bulkDownload(ids) },
            configs = configs,
            extensions = extensionSources,
            sourcesScroll = sourcesScroll,
            onGlobalSearch = {
                globalSearch.open = true
                if (globalSearch.query.isNotBlank() &&
                    globalSearch.results.isEmpty() &&
                    !globalSearch.running
                ) {
                    runGlobalSearch(globalSearch.query)
                }
            },
            onAddSource = {
                editingConfig = SourceConfig(SourceManager.newId(), "local", "")
                showSourceDialog = true
            },
            onOpenConfig = { openSourceConfig(it) },
            onOpenExtension = { openSource(it) },
            onEditConfig = {
                editingConfig = it
                showSourceDialog = true
            },
            onDeleteConfig = {
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
            },
            history = history,
            loading = isLoading,
            onOpenHistory = { openFromHistory(it) },
            onDeleteHistory = {
                History.remove(context, it.chapterKey)
                history = History.forDisplay(context)
            },
            onClearHistory = {
                History.list(context).forEach { History.remove(context, it.chapterKey) }
                history = History.forDisplay(context)
            },
            onRefreshHistory = { history = History.forDisplay(context) },
            downloadTick = downloadTick + DownloadQueue.tick,
            onOpenDownload = { openFromDownloads(it) },
            onOpenDownloadQueue = { downloadsOpen = true },
            onOpenSettings = { settingsOpen = true }
        )
    }

    val scan = videoScan
    if (videoScanning || scan != null) {
        ChapterVideoDialog(
            scanning = videoScanning,
            scan = scan,
            onDismiss = { videoScan = null },
            onOpenEmbed = { url ->
                val page = activeSeries?.let { series ->
                    browseState.source?.seriesUrl(series)
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

    MainOverlayDialogs(
        activeSource = browseState.source,
        probeOpen = probeOpen,
        onDismissProbe = { probeOpen = false },
        filtersOpen = filtersOpen,
        onApplyFilters = { source ->
            filtersOpen = false
            openSource(source, "", BrowseMode.FILTER)
        },
        onDismissFilters = { filtersOpen = false },
        whatsNewOpen = whatsNewOpen,
        releaseNotes = releaseNotes,
        onDismissWhatsNew = {
            whatsNewOpen = false
            WhatsNew.markSeen(context)
        },
        showSourceDialog = showSourceDialog,
        editingConfig = editingConfig,
        onEditingConfigChange = { editingConfig = it },
        onDismissSourceDialog = {
            showSourceDialog = false
            editingConfig = null
        },
        onSaveSource = { saved ->
            SourceManager.upsert(context, saved)
            configs = SourceManager.list(context)
            showSourceDialog = false
            editingConfig = null
        }
    )

}
