package com.mangareader.app

import android.content.Context
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import dalvik.system.PathClassLoader
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- prefs: the same store every other object in this package uses ----------

// ---------- sources tab ----------

// ---------- entry markers, shared by every screen that lists series ----------

/**
 * The corner markers a series cell can carry, gathered **once per screen**.
 *
 * Every field here is a whole-store read: `DownloadIndex.seriesIds` parses an
 * index file, `SeriesIndex.all` parses one JSON string, `Categories.seriesIn`
 * parses the assignment map. Asking any of them per row is §5's "an import is a
 * load test" — the category chips did exactly that and froze the app at 3567
 * entries. So this is built at the top of a screen and handed down.
 *
 * Absent from [unread] means **un-counted, not zero**, which is why the map only
 * ever holds positive counts and the badge draws nothing for a miss. A grid of
 * `0` badges reads as "you have read everything", which is a plausible enough
 * lie to be believed rather than reported.
 */
internal class EntryMarks(
    val readIds: Set<String>,
    val downloadedIds: Set<String>,
    val unread: Map<String, Int>,
    val badgeLocal: Boolean
) {
    fun dim(seriesId: String) = seriesId in readIds
    fun downloaded(seriesId: String) = seriesId in downloadedIds
    fun unreadOf(seriesId: String) = unread[seriesId]

    companion object {
        val NONE = EntryMarks(emptySet(), emptySet(), emptyMap(), false)
    }
}

/**
 * Reads the three stores behind [EntryMarks], honouring the same badge
 * preferences the library screen uses — one setting for "show me download
 * badges" rather than one per screen.
 *
 * [tick] is whatever the calling screen bumps when library state moves.
 */
@Composable
internal fun rememberEntryMarks(tick: Int): EntryMarks {
    val context = LocalContext.current
    return remember(tick) {
        val badgeDl = LibraryPrefs.badgeDownloaded(context)
        val badgeUnread = LibraryPrefs.badgeUnread(context)
        // "Read" is an ordinary user category matched by name, exactly as the
        // library grid matches it. No new field, one parse.
        val readCat = Categories.list(context)
            .firstOrNull { it.name.equals("Read", ignoreCase = true) }
        EntryMarks(
            readIds = if (readCat == null) emptySet()
            else Categories.seriesIn(context, readCat.id),
            downloadedIds = if (badgeDl) DownloadIndex.seriesIds(context) else emptySet(),
            unread = if (badgeUnread) {
                SeriesIndex.all(context)
                    .mapValues { (_, c) -> c.unread }
                    .filterValues { it > 0 }
            } else emptyMap(),
            badgeLocal = LibraryPrefs.badgeLocal(context)
        )
    }
}

/**
 * The corner markers themselves. Nothing is drawn when all are off, so a cell
 * that has none carries no box.
 *
 * Was private to `LibraryScreens.kt` until 0.113. Same rendering everywhere on
 * purpose: a `DL` chip should mean the same thing in Browse as it does in the
 * Library, and two implementations would drift.
 */
@Composable
internal fun EntryBadges(downloaded: Boolean, local: Boolean, unread: Int? = null) {
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
internal fun MiniBadge(text: String, colour: Color) {
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
 * A draggable scroll handle for a long lazy list or grid.
 *
 * **Deliberately takes numbers, not a state object.** A `LazyGridState` and a
 * `LazyListState` share no supertype that exposes what this needs, so a version
 * written against one would have to be duplicated for the other — and the card
 * this comes from is "*all* scrolls should have a scroll handle". Handing it
 * four integers and a callback means the second caller is three lines rather
 * than a second copy of this file.
 *
 * **The thumb is derived from the scroll, never driven alongside it.** Dragging
 * calls [onSeek] and then waits to be told where it ended up, exactly like the
 * library tab row does with `settledPage`. Keeping a private thumb position and
 * a scroll position in step is two things driving one value, which §5 records
 * as a feedback loop that no amount of "is it already equal" fixes.
 *
 * Hidden until something moves. A permanent handle on a screen of cover art is
 * clutter, and on a 3575-entry library it is also a lie about precision — one
 * pixel of track is several series.
 */
/**
 * [ScrollHandle] wired to a [LazyListState], which is every caller but the
 * library grid.
 *
 * The core takes four integers rather than a state object on purpose —
 * `LazyGridState` and `LazyListState` share no supertype exposing what it needs
 * — but that left five lines of identical wiring at each call site: the seek
 * state, the keyed effect, the four readings. Five lines copied six times is
 * five chances to reintroduce the 0.133 drag lag, so the wiring lives here once.
 *
 * **The seek target is held in state and scrolled from a keyed effect**, which
 * is the whole of the 0.133 fix. A `launch { scrollToItem() }` per drag delta
 * queues on the list's scroll mutex and runs in order, so the list is forever
 * arriving where the finger was half a second ago. Keying the effect makes
 * Compose cancel the superseded scroll, and only the newest ever runs.
 *
 * `totalItems` is a count in the LIST's index space, not the data's. A column
 * with a header and a trailing spacer has two items that are not rows, and
 * seeking is `scrollToItem`, which counts them too.
 */
@Composable
internal fun ListScrollHandle(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    // The count comes from the LIST, not from the caller.
    //
    // Every caller used to pass its own arithmetic — `visible.size + 3` for the
    // chapter list, a three-way conditional for Extensions, `shown.size` for
    // global search — and each was a hand-maintained restatement of something
    // the LazyColumn already knows exactly. The chapter list's went wrong the
    // moment 0.157 added two lazy items to that screen and left the `+ 3`
    // alone: the handle then stopped two chapters short, and nothing about the
    // code looked wrong.
    //
    // layoutInfo.totalItemsCount cannot drift from the composition, because it
    // IS the composition's count. Deleting the parameter deletes the whole
    // class of bug rather than this instance of it.
    val totalItems = state.layoutInfo.totalItemsCount
    var seekTo by remember { mutableIntStateOf(-1) }
    LaunchedEffect(seekTo) {
        if (seekTo >= 0) state.scrollToItem(seekTo)
    }
    // visibleItemsInfo.size counts PARTIALLY visible items too, and that was the
    // bug behind every "the handle stops short" report: `span = totalItems -
    // visibleItems` treats a row clipped by the viewport edge as one that fits,
    // so the maximum index it can seek to is short by however many are clipped —
    // one at each end, usually. Measured on device: thumb hard against the
    // bottom of its track, list still 20% short of the last row.
    //
    // Counting only the ones that fit ENTIRELY gives the true capacity, and the
    // true capacity is what makes `totalItems - visibleItems` the index that
    // puts the last item flush with the bottom.
    val info = state.layoutInfo
    val fullyVisible = info.visibleItemsInfo.count {
        it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
    }.coerceAtLeast(1)

    ScrollHandle(
        firstVisibleIndex = state.firstVisibleItemIndex,
        visibleItems = fullyVisible,
        totalItems = totalItems,
        isScrolling = state.isScrollInProgress,
        onSeek = { seekTo = it },
        modifier = modifier
    )
}

/**
 * A [ScrollHandle] for a grid, measured in ROWS rather than cells.
 *
 * **The cell-index version could not reach the last row and that is arithmetic,
 * not layout.** `ScrollHandle` seeks to `totalItems - visibleItems`, which for a
 * LIST puts the last item exactly at the bottom. On a grid `scrollToItem`
 * aligns the ROW CONTAINING that index to the top, so the start is pulled back
 * to a row boundary and the final partial row falls below the fold — by up to
 * `columns - 1` cells. With three columns and a hundred entries, dragging to the
 * bottom stopped at cell 98 of 99.
 *
 * It is invisible whenever the last row happens to be full and the arithmetic
 * happens to land on a boundary, which is why it survived being "verified" on
 * the library in 0.133 and on browse in 0.158.
 *
 * Columns are read off the layout rather than passed in: both callers use
 * `GridCells.Adaptive`, so the count changes with the window and with the
 * display mode, and anything the caller could pass would be a guess about a
 * number the grid already knows.
 */
@Composable
internal fun GridScrollHandle(
    state: LazyGridState,
    modifier: Modifier = Modifier
) {
    val info = state.layoutInfo
    // From the grid itself — see ListScrollHandle for why no caller passes this.
    // Cells, not rows; the conversion below is what makes it a row count.
    val totalItems = info.totalItemsCount
    // maxOf(column) + 1 rather than a span calculation: with Adaptive columns
    // this is the only place the real count exists. Coerced because an empty
    // or not-yet-measured grid reports nothing and a zero would divide.
    val columns = ((info.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1).coerceAtLeast(1)
    // Rows that fit ENTIRELY, not rows with any pixel on screen. A row clipped
    // by the viewport edge counted as one that fits, which made the seekable
    // span short by a row at each end — see ListScrollHandle for the measurement.
    val visibleRows = info.visibleItemsInfo
        .filter {
            it.offset.y >= info.viewportStartOffset &&
                it.offset.y + it.size.height <= info.viewportEndOffset
        }
        .map { it.row }
        .distinct()
        .size
        .coerceAtLeast(1)
    val totalRows = (totalItems + columns - 1) / columns

    var seekRow by remember { mutableIntStateOf(-1) }
    LaunchedEffect(seekRow) {
        // Back into cell units for the grid: the first cell of that row.
        if (seekRow >= 0) state.scrollToItem((seekRow * columns).coerceIn(0, (totalItems - 1).coerceAtLeast(0)))
    }

    ScrollHandle(
        firstVisibleIndex = state.firstVisibleItemIndex / columns,
        visibleItems = visibleRows,
        totalItems = totalRows,
        isScrolling = state.isScrollInProgress,
        onSeek = { seekRow = it },
        modifier = modifier
    )
}

@Composable
internal fun ScrollHandle(
    firstVisibleIndex: Int,
    visibleItems: Int,
    totalItems: Int,
    isScrolling: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Nothing to seek through: the whole list is on screen.
    if (totalItems <= visibleItems || totalItems <= 0) return

    var dragging by remember { mutableStateOf(false) }
    // -1 means "not dragging". Held only for the duration of a drag, to carry
    // the sub-item remainder between deltas — without it a slow drag rounds to
    // the same index every frame and the handle sticks.
    var dragIndex by remember { mutableFloatStateOf(-1f) }

    // Lingers after the scroll stops rather than vanishing with it.
    // `isScrollInProgress` goes false the instant a fling settles, and a handle
    // that disappears at that moment is gone before you can reach for it — the
    // scroll having stopped is usually the point at which someone wants it.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, dragging) {
        if (isScrolling || dragging) {
            settled = true
        } else {
            delay(HANDLE_LINGER_MS)
            settled = false
        }
    }
    val handleAlpha by animateFloatAsState(if (settled) 1f else 0f, label = "handleAlpha")

    // Inset from the top and bottom of whatever it is aligned in.
    //
    // The track used to be fillMaxHeight, which put the thumb's travel flush
    // against both screen edges. Two problems, and only the first was reported:
    // it looks wrong, and the BOTTOM of the travel lands in the system
    // navigation gesture zone — so the last stretch of a drag competes with the
    // back/home gesture and can be intercepted before the list reaches its end.
    // If a handle ever "stops short" without the arithmetic being wrong, this
    // is the first thing to suspect.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            // System bars FIRST, then the fixed inset.
            //
            // 0.165 used the fixed inset alone and it was not enough on the
            // series screen, whose Box runs edge to edge behind the status bar
            // so the cover art can — so 24dp from the top of that Box is still
            // level with the clock. Screens that already sit inside the insets
            // consume them, so this adds nothing there and does not double up.
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(vertical = HANDLE_EDGE_INSET)
            .width(HANDLE_WIDTH)
    ) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { HANDLE_HEIGHT.toPx() }
        val usable = (trackPx - thumbPx).coerceAtLeast(1f)
        val span = (totalItems - visibleItems).coerceAtLeast(1)

        val position = (if (dragIndex >= 0f) dragIndex else firstVisibleIndex.toFloat()) / span
        val offsetY = (position.coerceIn(0f, 1f) * usable).roundToInt()

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, offsetY) }
                .width(HANDLE_WIDTH)
                .height(HANDLE_HEIGHT)
                .alpha(handleAlpha)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val base = if (dragIndex >= 0f) dragIndex else firstVisibleIndex.toFloat()
                        val next = (base + delta / usable * span).coerceIn(0f, span.toFloat())
                        dragIndex = next
                        onSeek(next.roundToInt())
                    },
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        dragIndex = -1f
                    }
                )
        )
    }
}

// Wide enough to be a target rather than a hairline. Material's minimum touch
// target is 48dp and this is well under it, which is why the thumb is tall — the
// finger finds it vertically, and the width only has to be visible.
/**
 * Wider than it looks like it needs to be, and that is the point.
 *
 * 16dp was the visual width and it was also the touch target, which is half
 * Material's 48dp minimum — on a control whose whole job is being grabbed
 * one-handed at the edge of the screen, where the thumb is least accurate.
 */
private val HANDLE_WIDTH = 24.dp

/**
 * Keeps the thumb's travel clear of the top and bottom screen edges.
 *
 * Sized to clear a gesture-navigation bar rather than to look tidy: at the
 * bottom the alternative is a drag the system takes for a back or home swipe.
 */
private val HANDLE_EDGE_INSET = 24.dp
private val HANDLE_HEIGHT = 56.dp
private const val HANDLE_LINGER_MS = 1500L
