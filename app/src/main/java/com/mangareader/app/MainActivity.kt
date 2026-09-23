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

    val appState = remember { AppUiState(context) }

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

    // navigation state
    val browseState = remember { SourceBrowseState() }
    val seriesState = remember { SeriesNavigationState() }
    val readerSession = remember { ReaderSessionState() }

    // Hoisted so results survive opening a series and navigating back.
    val globalSearch = remember { GlobalSearchState(context) }

    val migrationState = remember { MigrationNavigationState() }

    val mediaState = remember { MediaNavigationState() }

    val activity = context as? ComponentActivity
    DoubleBackToExitHandler(activity)

    DownloadQueueIntentHandler(
        activity = activity,
        onOpenQueue = {
            appState.currentTab = 3
            appState.downloadsOpen = true
        }
    )
    ExtensionResumeObserver(
        activity = activity,
        onSourcesChanged = { appState.extensionSources = it }
    )

    val actions = remember(
        context,
        scope,
        appState,
        browseState,
        seriesState,
        readerSession,
        globalSearch,
        migrationState,
        mediaState
    ) {
        AppActionController(
            context = context,
            scope = scope,
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            readerSession = readerSession,
            globalSearch = globalSearch,
            migrationState = migrationState,
            mediaState = mediaState
        )
    }

    // ---- routing ----

    val chapterIdx = readerSession.chapterIndex
    val readerChapter = chapterIdx?.let { seriesState.chapters.getOrNull(it) }

    // Same shape and same justification as the challenge branch below: gated on
    // state that is null in every other flow, and clearing it drops back onto
    // whatever was underneath. Placed BELOW the challenge so a Cloudflare wall
    // still wins — a player is never more urgent than being able to reach the
    // site at all.
    val embed = mediaState.embed
    if (appState.challengeUrl == null && embed != null) {
        EmbedPlayerRoute(
            embed = embed,
            media = mediaState.media,
            onMediaFound = { mediaState.media = it },
            onDismissMedia = { mediaState.media = null },
            onBack = { mediaState.embed = null },
            onPlayerError = { appState.error = it }
        )
        return
    }

    val challenge = appState.challengeUrl
    if (challenge != null) {
        // Sits above every other branch, and safely so: it's gated on state that
        // is null in every other flow, and clearing that state drops back onto
        // whatever was underneath with nothing else touched. No branch below has
        // to know this one exists — which is the only reason it was safe to put
        // anything at the top of this chain.
        ChallengeRoute(
            url = challenge,
            onSolved = {
                appState.challengeUrl = null
                // Re-run whatever was on screen. The clearance cookie is in the
                // store OkHttp already reads, so this is an ordinary retry.
                //
                // Which screen matters now that the challenge is reachable from
                // the series screen too: re-running the browse from there would
                // solve the challenge and then throw away the series the user
                // was trying to open.
                val openSeriesAgain = seriesState.active
                if (openSeriesAgain != null) {
                    // openSeries sets the origin to BROWSE unconditionally — see
                    // the note on openGlobalResult. This is a retry, not a fresh
                    // navigation, so back has to still go where it did before.
                    val origin = seriesState.origin
                    actions.openSeries(openSeriesAgain)
                    seriesState.origin = origin
                } else {
                    browseState.source?.let { actions.openSource(it, browseState.query, browseState.mode) }
                }
            },
            onBack = { appState.challengeUrl = null }
        )
    } else if (chapterIdx != null && readerChapter != null && readerSession.pages.isNotEmpty()) {
        ReaderRoute(
            pages = readerSession.pages,
            stillLoading = readerSession.loading,
            sourceId = browseState.sourceId ?: "",
            series = seriesState.active,
            chapter = readerChapter,
            chapters = seriesState.chapters,
            chapterIndex = chapterIdx,
            onOpenChapter = { actions.openChapter(it) },
            onClose = {
                readerSession.close()
                appState.history = History.forDisplay(context)
                appState.readTick++
            }
        )
    } else if (seriesState.active != null) {
        val series = seriesState.active!!
        val seriesSite = browseState.source?.siteUrl()
        val solveFromSeries: (() -> Unit)? = if (seriesSite == null) null else fun() {
            appState.challengeUrl = seriesSite
        }
        SeriesRoute(
            series = series,
            chapters = seriesState.chapters,
            chaptersFetched = seriesState.fetched,
            source = browseState.source,
            sourceId = browseState.sourceId,
            loading = appState.loading,
            error = appState.error,
            readTick = appState.readTick,
            scroll = seriesScroll,
            localDownloadTick = appState.downloadTick,
            onFindVideos = { actions.findVideos(it) },
            onDownload = { src, chapter -> actions.downloadChapter(src, chapter) },
            onDownloadAll = { src, chapters -> actions.downloadAll(src, chapters) },
            onCancelDownloads = { actions.cancelSeriesDownloads(it) },
            onDownloadStateChanged = { appState.downloadTick++ },
            onOpenChapter = { actions.openChapter(it) },
            onRefresh = { actions.refreshChapters() },
            onReadStateChanged = { appState.readTick++ },
            onLibraryChanged = { appState.libraryTick++ },
            onSearchTag = { tag ->
                browseState.source?.let { src ->
                    seriesState.tagReturn = seriesState.active
                    seriesState.active = null
                    appState.error = null
                    if (src.applyGenreFilter(tag)) {
                        actions.openSource(src, "", BrowseMode.FILTER)
                    } else {
                        actions.openSource(src, tag, browseState.mode)
                    }
                }
            },
            onGlobalSearchTag = { tag ->
                seriesState.tagReturn = seriesState.active
                seriesState.active = null
                appState.error = null
                globalSearch.open = true
                actions.runGlobalSearch(tag)
            },
            onMigrate = {
                val s = seriesState.active
                val sid = browseState.sourceId
                if (s != null && sid != null) {
                    migrationState.from = MigrateFrom(s.id, sid, s.title)
                    seriesState.tagReturn = s
                    seriesState.active = null
                    appState.error = null
                    globalSearch.open = true
                    actions.runGlobalSearch(s.title)
                }
            },
            onSolveChallenge = solveFromSeries,
            onBack = {
                seriesState.clear()
                appState.error = null
                if (seriesState.origin != SeriesOrigin.BROWSE) {
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
            onTogglePinnedOnly = { actions.setGlobalPinnedOnly(it) },
            hasResultsOnly = globalSearch.hasResultsOnly,
            onToggleHasResultsOnly = { globalSearch.hasResultsOnly = it },
            recents = globalSearch.recents,
            onRemoveRecent = { globalSearch.recents = SourcePrefs.removeRecentSearch(context, it) },
            onClearRecents = {
                SourcePrefs.clearRecentSearches(context)
                globalSearch.recents = emptyList()
            },
            onSearch = { actions.runGlobalSearch(it) },
            onCancel = { actions.cancelGlobalSearch() },
            onOpenSource = { actions.openGlobalSource(it) },
            migrating = migrationState.from != null,
            onOpenSeries = { src, s ->
                if (migrationState.from != null) migrationState.target = src to s
                else actions.openGlobalResult(src, s)
            },
            libraryTick = appState.libraryTick,
            onBack = {
                actions.cancelGlobalSearch()
                globalSearch.open = false
                migrationState.from = null
                browseState.series = null
                val cameFromTag = seriesState.tagReturn
                if (cameFromTag != null) {
                    seriesState.tagReturn = null
                    seriesState.active = cameFromTag
                } else {
                    browseState.source = null
                    browseState.sourceId = null
                }
            },
            migrateFrom = migrationState.from,
            migrateTarget = migrationState.target,
            onDismissMigration = { migrationState.target = null },
            onConfirmMigration = { from, targetSource, targetSeries ->
                actions.performMigration(from, targetSource, targetSeries)
            }
        )
    } else if (browseState.source != null) {
        val source = browseState.source!!
        val site = source.siteUrl()
        val startChallenge: (() -> Unit)? = if (site == null) null else fun() {
            appState.challengeUrl = site
        }
        SourceBrowseRoute(
            source = source,
            sourceId = browseState.sourceId,
            series = browseState.series,
            loading = appState.loading,
            error = appState.error,
            filtersOpen = { appState.filtersOpen = true },
            diagnose = { appState.probeOpen = true },
            mode = browseState.mode,
            onModeChange = { m -> actions.openSource(source, "", m) },
            query = browseState.query,
            hasNext = browseState.hasNext,
            loadingMore = browseState.loadingMore,
            onSearch = { q -> actions.openSource(source, q, browseState.mode) },
            onLoadMore = { actions.loadMoreSeries() },
            onRescan = { actions.openSource(source, browseState.query, browseState.mode) },
            onOpen = { actions.openSeries(it) },
            onBack = {
                browseState.series = null
                appState.error = null
                val cameFromTag = seriesState.tagReturn
                if (cameFromTag != null) {
                    seriesState.tagReturn = null
                    seriesState.active = cameFromTag
                } else {
                    browseState.source = null
                    browseState.sourceId = null
                }
            },
            libraryTick = appState.libraryTick,
            scroll = browseScroll,
            onSolveChallenge = startChallenge
        )
    } else if (appState.downloadsOpen) {
        // Above settings on purpose, and it's the ordering that does the work.
        // The queue is reachable from More *and* from Settings > Downloads, and
        // leaving `appState.settingsOpen` set while this renders means backing out of the
        // queue falls through to whichever of the two it was opened from — no
        // "where did I come from" flag, just two booleans read in order.
        DownloadQueueScreen(onBack = { appState.downloadsOpen = false })
    } else if (appState.settingsOpen) {
        SettingsScreen(
            onBack = { appState.settingsOpen = false },
            onOpenDownloadQueue = { appState.downloadsOpen = true }
        )
    } else {
        MainTabsRoute(
            currentTab = appState.currentTab,
            onSelectTab = { tab ->
                appState.currentTab = tab
                if (tab == 2) {
                    appState.history = History.forDisplay(context)
                }
            },
            libraryTick = appState.libraryTick,
            error = appState.error,
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
            onOpenLibrary = { actions.openFromLibrary(it) },
            onRemoveLibraryMany = { ids ->
                Library.removeAll(context, ids)
                appState.libraryTick++
            },
            onMarkRead = { ids -> actions.bulkSetRead(ids, true) },
            onMarkUnread = { ids -> actions.bulkSetRead(ids, false) },
            onDownloadMany = { ids -> actions.bulkDownload(ids) },
            configs = appState.configs,
            extensions = appState.extensionSources,
            sourcesScroll = sourcesScroll,
            onGlobalSearch = {
                globalSearch.open = true
                if (globalSearch.query.isNotBlank() &&
                    globalSearch.results.isEmpty() &&
                    !globalSearch.running
                ) {
                    actions.runGlobalSearch(globalSearch.query)
                }
            },
            onAddSource = {
                appState.editingConfig = SourceConfig(SourceManager.newId(), "local", "")
                appState.showSourceDialog = true
            },
            onOpenConfig = { actions.openSourceConfig(it) },
            onOpenExtension = { actions.openSource(it) },
            onEditConfig = {
                appState.editingConfig = it
                appState.showSourceDialog = true
            },
            onDeleteConfig = {
                SourceManager.remove(context, it.id)
                appState.configs = SourceManager.list(context)
            },
            onExtensionsChanged = {
                scope.launch {
                    appState.extensionSources = withContext(Dispatchers.IO) {
                        runCatching {
                            SourceManager.listAllSources(context)
                                .filter { it.id.startsWith("tachi:") }
                        }.getOrDefault(emptyList())
                    }
                }
            },
            history = appState.history,
            loading = appState.loading,
            onOpenHistory = { actions.openFromHistory(it) },
            onDeleteHistory = {
                History.remove(context, it.chapterKey)
                appState.history = History.forDisplay(context)
            },
            onClearHistory = {
                History.list(context).forEach { History.remove(context, it.chapterKey) }
                appState.history = History.forDisplay(context)
            },
            onRefreshHistory = { appState.history = History.forDisplay(context) },
            downloadTick = appState.downloadTick + DownloadQueue.tick,
            onOpenDownload = { actions.openFromDownloads(it) },
            onOpenDownloadQueue = { appState.downloadsOpen = true },
            onOpenSettings = { appState.settingsOpen = true }
        )
    }

    val scan = mediaState.scan
    if (mediaState.scanning || scan != null) {
        ChapterVideoDialog(
            scanning = mediaState.scanning,
            scan = scan,
            onDismiss = { mediaState.scan = null },
            onOpenEmbed = { url ->
                val page = seriesState.active?.let { series ->
                    browseState.source?.seriesUrl(series)
                }
                mediaState.scan = null
                mediaState.embed = url to (page ?: "")
            },
            onOpenVideo = { url ->
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(view) }
                    .onFailure {
                        appState.error = "No app on this device can play that link"
                    }
            }
        )
    }

    MainOverlayDialogs(
        activeSource = browseState.source,
        probeOpen = appState.probeOpen,
        onDismissProbe = { appState.probeOpen = false },
        filtersOpen = appState.filtersOpen,
        onApplyFilters = { source ->
            appState.filtersOpen = false
            actions.openSource(source, "", BrowseMode.FILTER)
        },
        onDismissFilters = { appState.filtersOpen = false },
        whatsNewOpen = whatsNewOpen,
        releaseNotes = releaseNotes,
        onDismissWhatsNew = {
            whatsNewOpen = false
            WhatsNew.markSeen(context)
        },
        showSourceDialog = appState.showSourceDialog,
        editingConfig = appState.editingConfig,
        onEditingConfigChange = { appState.editingConfig = it },
        onDismissSourceDialog = {
            appState.showSourceDialog = false
            appState.editingConfig = null
        },
        onSaveSource = { saved ->
            SourceManager.upsert(context, saved)
            appState.configs = SourceManager.list(context)
            appState.showSourceDialog = false
            appState.editingConfig = null
        }
    )

}
