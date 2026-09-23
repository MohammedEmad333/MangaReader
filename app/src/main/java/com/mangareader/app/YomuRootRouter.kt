package com.mangareader.app

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun YomuRootRouter(
    appState: AppUiState,
    browseState: SourceBrowseState,
    seriesState: SeriesNavigationState,
    readerSession: ReaderSessionState,
    globalSearch: GlobalSearchState,
    migrationState: MigrationNavigationState,
    mediaState: MediaNavigationState,
    actions: AppActionController,
    libraryCategory: String?,
    onLibraryCategoryChange: (String?) -> Unit,
    librarySearch: String,
    onLibrarySearchChange: (String) -> Unit,
    librarySearchOpen: Boolean,
    onLibrarySearchOpenChange: (Boolean) -> Unit,
    libraryScroll: ScrollMemory,
    browseScroll: ScrollMemory,
    seriesScroll: ScrollMemory,
    sourcesScroll: ScrollMemory,
    releaseNotes: List<ReleaseNote>,
    whatsNewOpen: Boolean,
    onDismissWhatsNew: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


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
        RootSeriesRoute(
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            globalSearch = globalSearch,
            migrationState = migrationState,
            actions = actions,
            scroll = seriesScroll
        )
    } else if (globalSearch.open) {
        RootGlobalSearchRoute(
            context = context,
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            globalSearch = globalSearch,
            migrationState = migrationState,
            actions = actions
        )
    } else if (browseState.source != null) {
        RootSourceBrowseRoute(
            appState = appState,
            browseState = browseState,
            seriesState = seriesState,
            actions = actions,
            scroll = browseScroll
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
        RootMainTabsRoute(
            context = context,
            scope = scope,
            appState = appState,
            globalSearch = globalSearch,
            actions = actions,
            libraryCategory = libraryCategory,
            onLibraryCategoryChange = onLibraryCategoryChange,
            librarySearch = librarySearch,
            onLibrarySearchChange = onLibrarySearchChange,
            librarySearchOpen = librarySearchOpen,
            onLibrarySearchOpenChange = onLibrarySearchOpenChange,
            libraryScroll = libraryScroll,
            sourcesScroll = sourcesScroll
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
        onDismissWhatsNew = onDismissWhatsNew,
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
