package com.mangareader.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
