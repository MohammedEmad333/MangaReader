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

    val allEntries = remember(tick) { Library.list(context) }
    var mediaFilter by rememberSaveable { mutableStateOf("All") }
    val entries = remember(allEntries, mediaFilter) {
        allEntries.filter { entry ->
            val anime = entry.sourceId.startsWith("aniyomi:") ||
                entry.seriesId.startsWith("anime:")
            when (mediaFilter) {
                "Anime" -> anime
                "Manga" -> !anime
                else -> true
            }
        }
    }
    val categories = remember(tick) { Categories.list(context) }

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

    // "Read" is a normal user category, so this is a name match rather than a
    // new field. One parse for the members; asking it per entry, through
    // categoriesFor(), is a full parse per series and is the mistake §5 records.
    val readIds = remember(tick, categories) {
        val readCat = categories.firstOrNull { it.name.equals("Read", ignoreCase = true) }
        if (readCat == null) emptySet<String>() else Categories.seriesIn(context, readCat.id)
    }

    // Asked for only when something on screen depends on it — and that gate was
    // never enough on its own, because the thing that depends on it (the
    // Downloaded badge) is on by default for everybody on every cold start.
    // This used to call DownloadIndex.list(), which sizes every downloaded
    // chapter, sorts by size, and runs an O(library) recovery scan to answer a
    // question about ids. Measured at 19.9 s of a cold start on this library.
    // seriesIds() is the same answer without the Downloads tab's work attached.
    val downloadedIds = remember(tick, badgeDl, fDownloaded) {
        if (!badgeDl && fDownloaded == FilterState.OFF) emptySet()
        else StartupTimings.once("Downloaded ids") { DownloadIndex.seriesIds(context) }
    }

    // Chapter counts per series. Unlike DownloadIndex this is a single string
    // read and one parse — no directory walk — but it is still conditional, for
    // the same reason and by the same rule: the library screen does no work the
    // current settings don't ask for.
    val counts = remember(tick, badgeUnread, sort, fUnread, fStarted, fCompleted) {
        val wanted = badgeUnread ||
            sort == LibrarySort.UNREAD_COUNT ||
            sort == LibrarySort.TOTAL_CHAPTERS ||
            sort == LibrarySort.LATEST_CHAPTER ||
            fUnread != FilterState.OFF ||
            fStarted != FilterState.OFF ||
            fCompleted != FilterState.OFF
        if (wanted) SeriesIndex.all(context) else emptyMap()
    }

    // Which sources are 18+. One string read and one parse, and only when the
    // filter is on — same rule as `counts` above. Keyed on SourceNsfw.version
    // because the flags are learned inside listAllSources on IO, which on a
    // cold start finishes after this screen has already filtered itself.
    val nsfwSources = remember(tick, fNsfw, SourceNsfw.version) {
        if (fNsfw == FilterState.OFF) emptyMap() else SourceNsfw.all(context)
    }

    // Most recent read per series, from History. History is capped at 40
    // chapters, so this is a partial answer by construction: anything older
    // simply has no timestamp and sorts to the end. That is worth having and
    // isn't worth a second store.
    val lastReadAt = remember(tick, sort) {
        if (sort != LibrarySort.LAST_READ) emptyMap()
        else History.list(context)
            .filter { it.seriesId.isNotBlank() }
            .groupBy { it.seriesId }
            .mapValues { (_, v) -> v.maxOf { it.updatedAt } }
    }

    val arrangeSpec = remember(
        search, sort, ascending, randomSeed,
        fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted, fNsfw,
        downloadedIds, readIds, counts, nsfwSources, lastReadAt,
    ) {
        LibraryArrangeSpec(
            search = search,
            sort = sort,
            ascending = ascending,
            randomSeed = randomSeed,
            filterDownloaded = fDownloaded,
            filterLocal = fLocal,
            filterRead = fRead,
            filterUnread = fUnread,
            filterStarted = fStarted,
            filterCompleted = fCompleted,
            filterNsfw = fNsfw,
            downloadedIds = downloadedIds,
            readIds = readIds,
            counts = counts,
            nsfwSources = nsfwSources,
            lastReadAt = lastReadAt,
        )
    }

    val groups: List<LibraryGroupView> = remember(
        entries, categories, tick, grouping, arrangeSpec,
        SourceNames.version,
    ) {
        buildLibraryGroups(
            context = context,
            entries = entries,
            categories = categories,
            grouping = grouping,
            spec = arrangeSpec,
        )
    }

    // What a stored scroll position is a position *into*. Everything that
    // changes which series sits at which index goes in here, and nothing else
    // does — a return trip from a series has to leave this identical or it
    // counts as a re-sort and throws the position away.
    //
    // This is why re-sorting used to look like the grid "just scrolling down":
    // the grid keys its items by series id, so on a reorder it hunts down
    // whatever was at the top and scrolls to its new index — which after a
    // shuffle is somewhere in the middle. The list really had been reordered;
    // it was just showing the same series, a thousand rows further in. Clearing
    // the position here is what makes a reorder start at the top.
    //
    // `counts` is deliberately *not* in here, though the three index-backed
    // sorts read it. It moves every time a chapter is finished, so including it
    // would drop the library's scroll position on every return from the reader —
    // which is the bug this whole mechanism exists to prevent, reintroduced from
    // the other end. What it costs is a slightly stale anchor when counts shift
    // under an index sort, and that case is a single series moving a few rows
    // rather than the wholesale reorder the position can't survive. Item keys
    // then re-anchor to the same series, which under a small change is the
    // behaviour you want anyway.
    val ordering = remember(
        sort, ascending, randomSeed, grouping, search, mediaFilter,
        fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted
    ) {
        listOf(
            sort, ascending, randomSeed, grouping, search.trim(), mediaFilter,
            fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted
        )
    }
    // Deliberately in composition rather than an effect: the grids below build
    // their state from `scroll` as they compose, so a stale position has to be
    // gone before they do, not one frame later. Idempotent, and a map lookup.
    scroll.sync(ordering)

    val tabIndex = groups.indexOfFirst { it.key == activeCategory }.let { if (it < 0) 0 else it }
    val pagerState = rememberPagerState(initialPage = tabIndex) { groups.size }

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
            visibleIds = groups.getOrNull(pagerState.currentPage)?.items.orEmpty().map { it.seriesId },
            search = search,
            searchOpen = searchOpen,
            mediaFilter = mediaFilter,
            groups = groups,
            currentPage = pagerState.currentPage,
            showTabs = showTabs,
            showCount = showCount,
            filterActive = LibraryPrefs.anyFilterActive(context),
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
            onMediaFilterChange = {
                mediaFilter = it
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
            unreadCounts = if (badgeUnread) {
                counts.mapValues { (_, counts) -> counts.unread }.filterValues { it > 0 }
            } else {
                emptyMap()
            },
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
