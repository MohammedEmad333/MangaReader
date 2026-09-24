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

@Composable
internal fun LibraryEmpty(allEmpty: Boolean, modifier: Modifier = Modifier) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryGrid(
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
    /**
     * Pull-to-refresh handler. Re-reads on-disk state (see the call site) — it
     * does NOT start the library sweep. Lives on the grid rather than the pager
     * because PullToRefreshBox needs its scrollable child directly beneath it.
     */
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (shown.isEmpty()) {
        // Outside the refresh box on purpose, matching History and Downloads:
        // the empty state has no scrollable child to feed the box's overscroll,
        // so wrapping it would be a gesture that can't fire. A tab with nothing
        // in it is refreshed by switching to a populated one, or by the pull on
        // any other tab bumping the shared localTick.
        LibraryEmpty(allEmpty = allEmpty, modifier = modifier)
        return
    }

    // Cleared from an effect, not the gesture lambda — History and Downloads do
    // the same (0.161). onRefresh is synchronous (a cache invalidate and a tick
    // bump, both already applied by the recomposition this flag triggers), so
    // there is no honest "refreshing" period; the flag exists only to give the
    // widget the two frames it needs to animate the arrow back out. Set true
    // and false in one pass and PullToRefreshBox never observes the transition.
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) refreshing = false }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
        modifier = modifier.fillMaxSize()
    ) {
    // if / else, not two early returns from this lambda: the grid branch calls
    // rememberRestoredGridState and the list branch rememberRestoredListState,
    // and a remember reached on one composition and skipped on the next (which
    // is what an early return between them would do when display flips) desyncs
    // the slot table. Each branch keeps its own remember inside its own group.
    if (display == LibraryDisplay.LIST) {
        LazyColumn(
            // Suffixed, because list and grid measure position in different
            // units — item index in a column isn't item index in a four-wide
            // grid — so switching display mode shouldn't restore the other's.
            state = rememberRestoredListState(scroll, "$scrollKey#list", ordering),
            modifier = Modifier
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
                        // Without it CoverRepair never learns about a 404 and
                        // half the cover fix is inert.
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
                        local = badgeLocal &&
                            entry.sourceId.isLocalSourceId(),
                        unread = unreadCounts[entry.seriesId]
                    )
                }
            }
        }
    } else {

    // Hoisted out of the LazyVerticalGrid call so the scroll handle beside it
    // reads the same state object. Two would give the handle a state that never
    // scrolls, and the symptom is a handle that never moves — which reads as the
    // arithmetic being wrong rather than as two objects. That is the 0.109 top
    // bar, one screen over.
    val gridState = rememberRestoredGridState(scroll, "$scrollKey#grid", ordering)
    // The seek target and its keyed effect moved into GridScrollHandle, which
    // owns them for both grid callers now. The reasoning is unchanged and lives
    // there: a `launch { scrollToItem() }` per drag delta queues dozens of
    // scrolls on the grid's own mutex and they run in order, so the grid
    // finishes arriving where the finger was half a second ago. Holding the
    // target in state and scrolling from a keyed effect cancels the superseded
    // one on every new value.

    Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        // A fixed count when the user has set one, otherwise size-driven.
        columns = if (perRow > 0) GridCells.Fixed(perRow)
        else GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        modifier = Modifier
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
                        // Without it CoverRepair never learns about a 404 and
                        // half the cover fix is inert.
                        seriesId = entry.seriesId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .alpha(if (dim) 0.4f else 1f)
                    )

                    Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                        EntryBadges(
                            downloaded = entry.seriesId in downloadedIds,
                            local = badgeLocal &&
                            entry.sourceId.isLocalSourceId(),
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

        // GridScrollHandle, not ScrollHandle: it seeks in ROWS. The cell-index
        // version could not reach the last row, because scrollToItem aligns the
        // row containing an index to the top and so pulls the start back to a
        // row boundary, dropping the final partial row below the fold. See its
        // KDoc — this was latent here since 0.133 and only surfaced on browse.
        GridScrollHandle(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
    } // else (grid)
    } // PullToRefreshBox
}
