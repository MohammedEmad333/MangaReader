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
            LibraryGridEntryCard(
                entry = entry,
                display = display,
                isSelected = isSelected,
                dim = entry.seriesId in readIds && !isSelected,
                downloaded = entry.seriesId in downloadedIds,
                badgeLocal = badgeLocal && entry.sourceId.isLocalSourceId(),
                unread = unreadCounts[entry.seriesId],
                selecting = selecting,
                onOpen = { onOpen(entry) },
                onToggle = { onToggle(entry.seriesId) },
            )
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
