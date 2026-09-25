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
internal val HANDLE_WIDTH = 24.dp

/**
 * Keeps the thumb's travel clear of the top and bottom screen edges.
 *
 * Sized to clear a gesture-navigation bar rather than to look tidy: at the
 * bottom the alternative is a drag the system takes for a back or home swipe.
 */
internal val HANDLE_EDGE_INSET = 24.dp
internal val HANDLE_HEIGHT = 56.dp
internal const val HANDLE_LINGER_MS = 1500L
