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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onRemoveMany: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A view-setting change alters what every tab holds without touching the
    // library itself, so there is nothing for YomuApp's libraryTick to say about
    // it. This is added to that tick rather than replacing it, so either can
    // invalidate the reads below — the same shape as downloadTick.
    var localTick by remember { mutableIntStateOf(0) }
    val tick = libraryTick + localTick

    val entries = remember(tick) { Library.list(context) }
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

    /** Filter, then order. Applied per group so each tab sorts within itself. */
    fun arrange(list: List<LibraryEntry>): List<LibraryEntry> {
        val needle = search.trim()
        val filtered = list.filter { e ->
            val isLocal = !e.sourceId.startsWith("tachi:")
            // A missing index entry reads as "doesn't hold" on all three, which
            // is why these are `?: false` rather than a null branch — see the
            // note on LibraryPrefs.filterUnread.
            val c = counts[e.seriesId]
            val checks = listOf(
                fDownloaded to (e.seriesId in downloadedIds),
                fLocal to isLocal,
                fRead to (e.seriesId in readIds),
                fUnread to ((c?.unread ?: 0) > 0),
                fStarted to (c?.started ?: false),
                fCompleted to (c?.completed ?: false)
            )
            checks.all { (state, holds) ->
                when (state) {
                    FilterState.OFF -> true
                    FilterState.INCLUDE -> holds
                    FilterState.EXCLUDE -> !holds
                }
            } && (needle.isBlank() || e.title.contains(needle, ignoreCase = true))
        }
        val ordered = when (sort) {
            LibrarySort.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
            LibrarySort.DATE_ADDED -> filtered.sortedBy { it.addedAt }
            LibrarySort.LAST_READ -> filtered.sortedBy { lastReadAt[it.seriesId] ?: Long.MIN_VALUE }
            // MIN_VALUE, not 0, for an un-counted series: ascending puts it
            // first and descending last, which is what "we don't know" deserves
            // in both directions. Zero would claim it has nothing unread, and
            // after an import that claim would be made about most of the
            // library.
            LibrarySort.UNREAD_COUNT ->
                filtered.sortedBy { counts[it.seriesId]?.unread ?: Int.MIN_VALUE }
            LibrarySort.TOTAL_CHAPTERS ->
                filtered.sortedBy { counts[it.seriesId]?.total ?: Int.MIN_VALUE }
            LibrarySort.LATEST_CHAPTER ->
                filtered.sortedBy { counts[it.seriesId]?.latestChapterAt ?: Long.MIN_VALUE }
            // Seeded so the order holds across recompositions and restarts, and
            // avalanche-mixed so it doesn't inherit the shape of the ids — see
            // LibraryPrefs.shuffleKey. Still a pure key per entry, so this is
            // one sort and no list copy per group.
            LibrarySort.RANDOM ->
                filtered.sortedBy { LibraryPrefs.shuffleKey(it.seriesId, randomSeed) }
        }
        return if (ascending || sort == LibrarySort.RANDOM) ordered else ordered.reversed()
    }

    // The tabs, and what each holds. Grouping by source is deliberately absent:
    // a LibraryEntry stores the source id it came from and never the source's
    // name, so those tabs would read as raw extension ids.
    data class Group(val key: String, val label: String, val items: List<LibraryEntry>)

    val groups: List<Group> = remember(
        entries, categories, tick, search, grouping, sort, ascending,
        fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted, counts, randomSeed
    ) {
        when (grouping) {
            LibraryGroup.UNGROUPED -> listOf(Group("all", "All", arrange(entries)))
            else -> {
                val assigned by lazy { Categories.assignedSeries(context) }
                categories.map { cat ->
                    val base = if (cat.id == Categories.DEFAULT_ID) {
                        // Default isn't a category things are filed under — it's
                        // where a series sits when it's filed under nothing,
                        // which is what Tachiyomi means by it too. Series put
                        // there by hand count as well, since the category editor
                        // writes it explicitly rather than saving an empty set.
                        val explicit = Categories.seriesIn(context, cat.id)
                        entries.filter { it.seriesId !in assigned || it.seriesId in explicit }
                    } else {
                        val ids = Categories.seriesIn(context, cat.id)
                        entries.filter { it.seriesId in ids }
                    }
                    Group(cat.id, cat.name, arrange(base))
                }
            }
        }
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
        sort, ascending, randomSeed, grouping, search,
        fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted
    ) {
        listOf(
            sort, ascending, randomSeed, grouping, search.trim(),
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
        if (selecting) {
            // Contextual bar. Replaces the normal one rather than sitting under
            // it, so the grid doesn't jump by a bar's height on every long press.
            TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                    }
                },
                actions = {
                    val visible = groups.getOrNull(pagerState.currentPage)?.items.orEmpty()
                    IconButton(onClick = { selected = visible.map { it.seriesId }.toSet() }) {
                        Icon(Icons.Default.Check, contentDescription = "Select all")
                    }
                    IconButton(onClick = { assignOpen = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Change categories")
                    }
                    IconButton(onClick = {
                        onRemoveMany(selected)
                        selected = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove from library")
                    }
                }
            )
        } else {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        // Not an OutlinedTextField: a bordered box inside a bar
                        // is taller than the bar's own content slot and clips.
                        TextField(
                            value = search,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Search library") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Library")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) onSearchChange("")
                        onSearchOpenChange(!searchOpen)
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search"
                        )
                    }
                    IconButton(onClick = { optionsOpen = true }) {
                        // Menu, not a funnel: material-icons-core has no
                        // FilterList and the extended pack isn't a dependency.
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Filter, sort and display options",
                            tint = if (LibraryPrefs.anyFilterActive(context))
                                MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                }
            )
        }

        ErrorBanner(error)

        if (groups.size > 1 && showTabs) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.size - 1),
                edgePadding = 8.dp
            ) {
                groups.forEachIndexed { index, g ->
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                if (showCount) "${g.label} (${g.items.size})" else g.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            LibraryEmpty(allEmpty = true, modifier = Modifier.weight(1f))
        } else {
            HorizontalPager(
                state = pagerState,
                // A swipe that also drags entries around is not a swipe. Held
                // off during selection so a mis-swipe can't change tab out from
                // under a half-made selection.
                userScrollEnabled = !selecting && groups.size > 1,
                modifier = Modifier.weight(1f)
            ) { page ->
                LibraryGrid(
                    shown = groups[page].items,
                    allEmpty = entries.isEmpty(),
                    // Per group, not per page index: the tabs can be reordered
                    // or renamed under a position, and a category's own scroll
                    // should follow the category.
                    scrollKey = groups[page].key,
                    scroll = scroll,
                    ordering = ordering,
                    display = display,
                    perRow = perRow,
                    readIds = readIds,
                    downloadedIds = if (badgeDl) downloadedIds else emptySet(),
                    badgeLocal = badgeLocal,
                    // Only what the badge needs, and only when it's on: the grid
                    // has no use for totals or dates, and handing it the whole
                    // index would make every count change recompose every cell.
                    unreadCounts = if (badgeUnread) {
                        counts.mapValues { (_, c) -> c.unread }.filterValues { it > 0 }
                    } else emptyMap(),
                    selected = selected,
                    selecting = selecting,
                    onOpen = onOpen,
                    onToggle = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (optionsOpen) {
        LibraryOptionsSheet(
            onDismiss = { optionsOpen = false },
            onChanged = { localTick++ }
        )
    }

    if (assignOpen) {
        BulkCategoryDialog(
            seriesIds = selected,
            onDismiss = { assignOpen = false },
            onApplied = {
                assignOpen = false
                selected = emptySet()
                localTick++
            }
        )
    }
}

@Composable
private fun LibraryEmpty(allEmpty: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (allEmpty) "Your library is empty." else "Nothing here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (allEmpty) "Open a series from Browse and tap \u201cAdd to library\u201d."
            else "Nothing matches the current filters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
/**
 * One group's entries, in whichever display mode is set.
 *
 * Split out of [LibraryTab] because the pager instantiates it per page, and
 * because the selection rules — tap opens, or toggles while selecting; long
 * press always starts a selection — are the same on every page and worth having
 * in one place.
 */
@Composable
private fun LibraryGrid(
    shown: List<LibraryEntry>,
    allEmpty: Boolean,
    scrollKey: String,
    scroll: ScrollMemory,
    ordering: Any?,
    display: LibraryDisplay,
    perRow: Int,
    readIds: Set<String>,
    downloadedIds: Set<String>,
    badgeLocal: Boolean,
    /** Series id to unread chapter count. Absent means un-counted, not zero. */
    unreadCounts: Map<String, Int>,
    selected: Set<String>,
    selecting: Boolean,
    onOpen: (LibraryEntry) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (shown.isEmpty()) {
        LibraryEmpty(allEmpty = allEmpty, modifier = modifier)
        return
    }

    if (display == LibraryDisplay.LIST) {
        LazyColumn(
            // Suffixed, because list and grid measure position in different
            // units — item index in a column isn't item index in a four-wide
            // grid — so switching display mode shouldn't restore the other's.
            state = rememberRestoredListState(scroll, "$scrollKey#list", ordering),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            items(shown, key = { it.seriesId }) { entry ->
                val isSelected = entry.seriesId in selected
                val dim = entry.seriesId in readIds && !isSelected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected)
                                Modifier.background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                )
                            else Modifier
                        )
                        .pointerInput(entry.seriesId, selecting) {
                            detectTapGestures(
                                onTap = {
                                    if (selecting) onToggle(entry.seriesId) else onOpen(entry)
                                },
                                onLongPress = { onToggle(entry.seriesId) }
                            )
                        }
                        .padding(vertical = 6.dp)
                ) {
                    CoverImage(
                        cover = entry.cover.ifBlank { null },
                        title = entry.title,
                        // The library grid is where a stale cover is visible and
                        // where the entry behind it is known, so this is the one
                        // place a failed draw can be turned into a repair.
                        seriesId = entry.seriesId,
                        modifier = Modifier
                            .width(44.dp)
                            .aspectRatio(0.7f)
                            .alpha(if (dim) 0.4f else 1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (dim) 0.4f else 1f)
                    )
                    EntryBadges(
                        downloaded = entry.seriesId in downloadedIds,
                        local = badgeLocal && !entry.sourceId.startsWith("tachi:"),
                        unread = unreadCounts[entry.seriesId]
                    )
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        // A fixed count when the user has set one, otherwise size-driven.
        columns = if (perRow > 0) GridCells.Fixed(perRow)
        else GridCells.Adaptive(minSize = 110.dp),
        state = rememberRestoredGridState(scroll, "$scrollKey#grid", ordering),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shown, key = { it.seriesId }) { entry ->
            val isSelected = entry.seriesId in selected
            // Read entries are dimmed everywhere, not only inside the Read tab:
            // the same series showing bright in Manhwa and dim in Read would be
            // a state that depends on where you're standing.
            val dim = entry.seriesId in readIds && !isSelected

            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .pointerInput(entry.seriesId, selecting) {
                        detectTapGestures(
                            onTap = {
                                if (selecting) onToggle(entry.seriesId) else onOpen(entry)
                            },
                            onLongPress = { onToggle(entry.seriesId) }
                        )
                    }
            ) {
                Box {
                    CoverImage(
                        cover = entry.cover.ifBlank { null },
                        title = entry.title,
                        // The library grid is where a stale cover is visible and
                        // where the entry behind it is known, so this is the one
                        // place a failed draw can be turned into a repair.
                        seriesId = entry.seriesId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .alpha(if (dim) 0.4f else 1f)
                    )

                    Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                        EntryBadges(
                            downloaded = entry.seriesId in downloadedIds,
                            local = badgeLocal && !entry.sourceId.startsWith("tachi:"),
                            unread = unreadCounts[entry.seriesId]
                        )
                    }

                    if (display == LibraryDisplay.COMPACT_GRID) {
                        // Title over the cover, on a scrim. A plain Text here is
                        // unreadable on a pale cover, which is most of them.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(if (dim) 0.6f else 1f)
                            )
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        )
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(2.dp)
                        )
                    }
                }

                if (display == LibraryDisplay.COMFORTABLE_GRID) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (dim) 0.4f else 1f)
                    )
                }
            }
        }
    }
}

/**
 * The corner markers. Nothing is drawn when all are off, so there is no box.
 *
 * [unread] is null for a series `SeriesIndex` has no counts for, which is not
 * the same as zero and must not render as "0" — after an import that is most of
 * the library, and a grid of zeroes would read as "you have read everything"
 * rather than "nothing has been counted yet". Null draws nothing; the count
 * appears the first time the series is opened.
 */
@Composable
private fun EntryBadges(downloaded: Boolean, local: Boolean, unread: Int? = null) {
    val showUnread = unread != null && unread > 0
    if (!downloaded && !local && !showUnread) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        // First, because it's the one that changes and the one being looked for.
        if (showUnread) MiniBadge("$unread", MaterialTheme.colorScheme.primary)
        if (downloaded) MiniBadge("DL", MaterialTheme.colorScheme.tertiary)
        if (local) MiniBadge("Local", MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun MiniBadge(text: String, colour: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}
/**
 * Category editor for a selection of any size.
 *
 * The checkboxes are tri-state because a selection usually isn't uniform: with
 * six series highlighted, "Manhwa" may hold four of them, and both a plain
 * checked box and a plain unchecked one would be a lie that silently rewrites
 * the other two on save. Indeterminate means *leave this alone*, and it is the
 * state a mixed category starts in and returns to.
 *
 * Tapping cycles On -> Off -> back to where it started. A category that began
 * mixed can therefore be forced on, forced off, or restored; one that began
 * uniform just toggles.
 */
@Composable
internal fun BulkCategoryDialog(
    seriesIds: Set<String>,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val cats = remember { Categories.list(context) }

    // One membership lookup per category against the cached assignment object,
    // then a set test per selected id. The other direction — categoriesFor()
    // per selected series — is a full parse each time.
    val initial = remember(seriesIds, cats) {
        cats.associate { cat ->
            val members = Categories.seriesIn(context, cat.id)
            val hits = seriesIds.count { it in members }
            cat.id to when (hits) {
                0 -> ToggleableState.Off
                seriesIds.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }
    }
    val state = remember(initial) {
        mutableStateMapOf<String, ToggleableState>().apply { putAll(initial) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (cats.isEmpty()) {
                Text("No categories yet - create some under More > Categories.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (seriesIds.size == 1) "1 entry selected"
                        else "${seriesIds.size} entries selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    cats.forEach { cat ->
                        val current = state[cat.id] ?: ToggleableState.Off
                        val start = initial[cat.id] ?: ToggleableState.Off
                        val cycle = {
                            state[cat.id] = when (current) {
                                ToggleableState.On -> ToggleableState.Off
                                ToggleableState.Off ->
                                    if (start == ToggleableState.Indeterminate) start
                                    else ToggleableState.On
                                ToggleableState.Indeterminate -> ToggleableState.On
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cycle() }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TriStateCheckbox(state = current, onClick = cycle)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(cat.name)
                                if (current == ToggleableState.Indeterminate) {
                                    Text(
                                        "Some selected - left unchanged",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = cats.isNotEmpty(),
                onClick = {
                    Categories.applyCategories(
                        context,
                        seriesIds,
                        add = state.filterValues { it == ToggleableState.On }.keys.toSet(),
                        remove = state.filterValues { it == ToggleableState.Off }.keys.toSet()
                    )
                    onApplied()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- browse ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToLibraryDialog(
    series: Series,
    sourceId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current

    // Guarantees there is always at least one category to save into.
    val default = remember { Categories.ensureDefault(context) }
    var cats by remember { mutableStateOf(Categories.list(context)) }
    var selected by remember { mutableStateOf(setOf(default.id)) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                Text(
                    "Categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    cats.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(cat.id)) {
                                        selected - cat.id
                                    } else {
                                        selected + cat.id
                                    }
                                }
                        ) {
                            Checkbox(
                                checked = selected.contains(cat.id),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + cat.id else selected - cat.id
                                }
                            )
                            Text(cat.name)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val created = Categories.addAndGet(context, newName.trim())
                            cats = Categories.list(context)
                            selected = selected + created.id
                            newName = ""
                        }
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Never save with zero categories; fall back to Default.
                val finalCats = if (selected.isEmpty()) setOf(default.id) else selected
                Library.add(
                    context,
                    LibraryEntry(
                        seriesId = series.id,
                        sourceId = sourceId,
                        title = series.title,
                        cover = (series.cover as? String)
                            ?: (series.cover as? java.io.File)?.absolutePath
                            ?: "",
                        addedAt = System.currentTimeMillis()
                    )
                )
                Categories.setCategoriesFor(context, series.id, finalCats)
                onSaved()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun CategoryAssignDialog(seriesId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = remember { Categories.list(context) }
    var selected by remember { mutableStateOf(Categories.categoriesFor(context, seriesId)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (all.isEmpty()) {
                Text("No categories yet — create some under More → Categories.")
            } else {
                Column {
                    all.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected =
                                        if (selected.contains(cat.id)) selected - cat.id
                                        else selected + cat.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(cat.id),
                                onCheckedChange = {
                                    selected = if (it) selected + cat.id else selected - cat.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Categories.setCategoriesFor(context, seriesId, selected)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- reader ----------
