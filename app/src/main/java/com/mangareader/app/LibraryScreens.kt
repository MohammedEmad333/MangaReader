package com.mangareader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- library ----------

/**
 * The library grid.
 *
 * [activeCategory] is hoisted into `YomuApp` on purpose. It used to be a plain
 * `remember` in here, which meant opening a series — the routing chain replaces
 * this whole branch — destroyed it, and backing out always landed on the first
 * category rather than the one that was being looked at.
 *
 * There is no longer an "All" tab. The tabs are exactly the user's categories,
 * so a null [activeCategory] is only the pre-resolution state; the first frame
 * resolves it to a real id.
 */
/**
 * The library grid.
 *
 * [activeCategory] is hoisted into `YomuApp` on purpose. It used to be a plain
 * `remember` in here, which meant opening a series — the routing chain replaces
 * this whole branch — destroyed it, and backing out always landed on the first
 * category rather than the one that was being looked at.
 *
 * There is no "All" tab. The tabs are exactly the groups, so a null
 * [activeCategory] is only the pre-resolution state; the first frame resolves it
 * to a real key.
 *
 * Everything about layout, order, grouping and filtering comes from
 * [LibraryPrefs], written by the options sheet. This composable reads them once
 * per tick and does no work the current settings don't ask for — the download
 * index in particular is only touched when a badge or filter needs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTab(
    libraryTick: Int,
    error: String?,
    activeCategory: String?,
    onCategoryChange: (String?) -> Unit,
    // Hoisted for the same reason [activeCategory] is: opening a series replaces
    // this branch of the routing chain, so a `remember` here doesn't come back.
    // The search used to be one, and every trip into a series cleared it.
    search: String,
    onSearchChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    scroll: ScrollMemory,
    onOpen: (LibraryEntry) -> Unit,
    onRemoveMany: (Set<String>) -> Unit,
    onMarkRead: (Set<String>) -> Unit,
    onMarkUnread: (Set<String>) -> Unit,
    onDownloadMany: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A view-setting change alters what every tab holds without touching the
    // library itself, so there is nothing for YomuApp's libraryTick to say about
    // it. This is added to that tick rather than replacing it, so either can
    // invalidate the reads below — the same shape as downloadTick.
    var localTick by remember { mutableIntStateOf(0) }
    val tick = libraryTick + localTick

    val base = rememberLibraryBaseState(context, tick)
    if (base == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ErrorBanner(error)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Loading library…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val allEntries = base.entries
    var mediaFilterName by rememberSaveable {
        mutableStateOf(LibraryPrefs.mediaFilter(context).name)
    }
    val mediaFilter = runCatching { LibraryMediaFilter.valueOf(mediaFilterName) }
        .getOrDefault(LibraryMediaFilter.ALL)

    // Keep the text field immediate while avoiding a full multi-thousand-entry
    // filter/sort for every intermediate key event. The old synchronous path
    // did that work inline; the background path would otherwise launch and
    // cancel the same expensive job repeatedly while someone is typing.
    var arrangedSearch by remember { mutableStateOf(search) }
    LaunchedEffect(search) {
        delay(120)
        arrangedSearch = search
    }

    val entries = remember(allEntries, mediaFilter) {
        allEntries.filter { entry ->
            val anime = entry.sourceId.startsWith("aniyomi:") ||
                entry.seriesId.startsWith("anime:")
            when (mediaFilter) {
                LibraryMediaFilter.ANIME -> anime
                LibraryMediaFilter.MANGA -> !anime
                LibraryMediaFilter.ALL -> true
            }
        }
    }
    val categories = base.categories

    val sort = remember(tick) { LibraryPrefs.sort(context) }
    val ascending = remember(tick) { LibraryPrefs.ascending(context) }
    val randomSeed = remember(tick) { LibraryPrefs.randomSeed(context) }
    val display = remember(tick) { LibraryPrefs.display(context) }
    val perRow = remember(tick) { LibraryPrefs.itemsPerRow(context) }
    val grouping = remember(tick) { LibraryPrefs.group(context) }
    val badgeDl = remember(tick) { LibraryPrefs.badgeDownloaded(context) }
    val badgeLocal = remember(tick) { LibraryPrefs.badgeLocal(context) }
    val badgeUnread = remember(tick) { LibraryPrefs.badgeUnread(context) }
    val showTabs = remember(tick) { LibraryPrefs.showTabs(context) }
    val showCount = remember(tick) { LibraryPrefs.showCount(context) }
    val fDownloaded = remember(tick) { LibraryPrefs.filterDownloaded(context) }
    val fLocal = remember(tick) { LibraryPrefs.filterLocal(context) }
    val fRead = remember(tick) { LibraryPrefs.filterRead(context) }
    val fUnread = remember(tick) { LibraryPrefs.filterUnread(context) }
    val fStarted = remember(tick) { LibraryPrefs.filterStarted(context) }
    val fCompleted = remember(tick) { LibraryPrefs.filterCompleted(context) }
    val fNsfw = remember(tick) { LibraryPrefs.filterNsfw(context) }

    var optionsOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var assignOpen by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    val derived = rememberLibraryDerivedState(
        context = context,
        tick = tick,
        entries = entries,
        categories = categories,
        grouping = grouping,
        search = arrangedSearch,
        mediaFilter = mediaFilter.label,
        sort = sort,
        ascending = ascending,
        randomSeed = randomSeed,
        badgeDl = badgeDl,
        badgeUnread = badgeUnread,
        fDownloaded = fDownloaded,
        fLocal = fLocal,
        fRead = fRead,
        fUnread = fUnread,
        fStarted = fStarted,
        fCompleted = fCompleted,
        fNsfw = fNsfw,
        scroll = scroll,
    )
    if (derived == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ErrorBanner(error)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Preparing library…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val readIds = derived.readIds
    val downloadedIds = derived.downloadedIds
    val counts = derived.counts
    val groups = derived.groups
    val ordering = derived.ordering

    val tabIndex = groups.indexOfFirst { it.key == activeCategory }.let { if (it < 0) 0 else it }
    val pagerState = rememberPagerState(initialPage = tabIndex) { groups.size }

    val currentPage = pagerState.currentPage
    val visibleIds = remember(groups, currentPage) {
        groups.getOrNull(currentPage)
            ?.items
            .orEmpty()
            .map { it.seriesId }
    }
    val unreadCounts = remember(counts, badgeUnread) {
        if (!badgeUnread) {
            emptyMap()
        } else {
            counts.asSequence()
                .mapNotNull { (seriesId, value) ->
                    value.unread.takeIf { it > 0 }?.let { seriesId to it }
                }
                .toMap()
        }
    }

    // The pager owns the position. This is the only thing that reports it
    // outward, and it reads settledPage rather than currentPage on purpose.
    //
    // The two-way sync this replaces deadlocked itself: animateScrollToPage(3)
    // from page 0 animates *through* 1 and 2, currentPage updates at each one,
    // and reporting those intermediate values back out moved activeCategory,
    // which tripped a second effect into issuing its own animateScrollToPage and
    // cancelling the first mid-flight. Every distant tab tap landed one short.
    //
    // settledPage only moves when the scroll stops, so a jump reports once, at
    // the end. Nothing drives the pager from activeCategory any more — restoring
    // the tab after a series is opened and backed out of is what initialPage is
    // for, and that is read once, before any of this runs.
    LaunchedEffect(pagerState.settledPage, groups) {
        groups.getOrNull(pagerState.settledPage)?.let {
            if (it.key != activeCategory) onCategoryChange(it.key)
        }
    }

    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searchOpen) {
        onSearchOpenChange(false)
        onSearchChange("")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryTopControls(
            selecting = selecting,
            selectedCount = selected.size,
            visibleIds = visibleIds,
            search = search,
            searchOpen = searchOpen,
            mediaFilter = mediaFilter.label,
            groups = groups,
            currentPage = currentPage,
            showTabs = showTabs,
            showCount = showCount,
            filterActive = LibraryPrefs.anyFilterActive(context),
            visibleCount = visibleIds.size,
            totalCount = allEntries.size,
            onClearSelection = { selected = emptySet() },
            onSelectAll = { selected = it.toSet() },
            onAssignCategories = { assignOpen = true },
            onRemoveSelection = {
                onRemoveMany(selected)
                selected = emptySet()
            },
            onMarkRead = {
                onMarkRead(selected)
                selected = emptySet()
            },
            onMarkUnread = {
                onMarkUnread(selected)
                selected = emptySet()
            },
            onDownload = {
                onDownloadMany(selected)
                selected = emptySet()
            },
            onSearchChange = onSearchChange,
            onSearchOpenChange = onSearchOpenChange,
            onOpenOptions = { optionsOpen = true },
            onMediaFilterChange = { label ->
                val next = LibraryMediaFilter.entries.firstOrNull { it.label == label }
                    ?: LibraryMediaFilter.ALL
                mediaFilterName = next.name
                LibraryPrefs.setMediaFilter(context, next)
                selected = emptySet()
            },
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
        )

        ErrorBanner(error)

        LibraryPagerContent(
            groups = groups,
            allEntriesEmpty = allEntries.isEmpty(),
            pagerState = pagerState,
            selecting = selecting,
            scroll = scroll,
            ordering = ordering,
            display = display,
            perRow = perRow,
            readIds = readIds,
            downloadedIds = if (badgeDl) downloadedIds else emptySet(),
            badgeLocal = badgeLocal,
            unreadCounts = unreadCounts,
            selected = selected,
            onOpen = onOpen,
            onToggle = { id ->
                selected = if (id in selected) selected - id else selected + id
            },
            onRefresh = {
                Downloads.invalidateCompletion()
                localTick++
            },
            modifier = Modifier.weight(1f),
        )
    }

    LibraryScreenDialogs(
        optionsOpen = optionsOpen,
        onDismissOptions = { optionsOpen = false },
        onOptionsChanged = { localTick++ },
        assignOpen = assignOpen,
        selected = selected,
        onDismissAssign = { assignOpen = false },
        onAppliedAssign = {
            assignOpen = false
            selected = emptySet()
            localTick++
        },
    )
}
