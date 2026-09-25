package com.mangareader.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TRANSITION_DWELL_MS = 550L

internal data class ReaderNavigationState(
    val headRows: Int,
    val pagerState: PagerState,
    val listState: LazyListState,
    val currentPage: Int,
    val lastPage: Int,
    val seekTargetState: MutableState<Float?>,
    val rtl: Boolean,
    val commitSeek: () -> Unit,
)

@Composable
internal fun rememberReaderNavigationState(
    pages: List<java.io.File?>,
    initialPage: Int,
    settings: ReaderSettings,
    chapterIndex: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Int) -> Unit,
    scope: CoroutineScope,
): ReaderNavigationState {
    /**
     * How many rows sit above page 0 in the strip.
     *
     * The strip carries a transition row at each end — the shape TachiyomiSY's
     * webtoon adapter uses, where they are real items in the list rather than a
     * gesture to detect. That shifts every list index one past its page index,
     * and **four places depend on the two being the same**: the seeded scroll
     * position, the current-page read, seeking, and the end-of-chapter test that
     * marks a chapter read. Each of them adds this, and the last one is the one
     * that fails silently — get it wrong and chapters simply stop being marked
     * read, which is §5's 0.57 bug arriving from a new direction.
     *
     * **As of 0.130 this is 1 in every mode, and that is the change to be
     * careful about.** It was strip-only, so paged mode was reading
     * `pagerState.currentPage` as a page index directly. Now the pager carries
     * transition pages too, so the same offset applies there — and paged mode is
     * where read-marking has always worked, which makes it the mode with
     * something to lose. Every arithmetic site below is a place this can fail
     * silently.
     */
    val headRows = 1

    // Two extra pages in the pager: a transition at each end, the same pair the
    // strip has carried since 0.104. Both counts come from `headRows` rather
    // than a literal, so the offset has one source — a second copy of it drifts
    // the moment either is edited, which is the shape 0.100 fixed for the
    // refresh cursor.
    val pagerState = rememberPagerState(initialPage = initialPage + headRows) {
        if (pages.isEmpty()) 0 else pages.size + headRows * 2
    }

    // Seeded past the header so a chapter opens on its first page, with the
    // previous-chapter row above it rather than in front of it.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage + headRows
    )

    // Whether the bottom of the last page is actually on screen.
    //
    // `!canScrollForward`, which this replaces, was wrong in a way that only
    // showed at runtime: a LazyListState reports it as false until its first
    // measure, and the effect below runs before that. So opening any chapter in
    // this mode reported the last page immediately, which saved the wrong
    // resume position and marked the chapter read before a single page had been
    // looked at. Reading a *derived* fact about layout means waiting for layout
    // to exist — an empty `visibleItemsInfo` is the proof it doesn't yet.
    val atStripEnd by remember(pages.size) {
        derivedStateOf {
            val info = listState.layoutInfo
            // The *last page*, which is no longer the last item — a transition
            // row follows it. Matching on the index rather than taking the last
            // visible one is what keeps this honest as rows are added around the
            // pages.
            val last = info.visibleItemsInfo.lastOrNull { it.index == pages.lastIndex + headRows }
            pages.isNotEmpty() &&
                last != null &&
                last.offset + last.size <= info.viewportEndOffset
        }
    }

    /**
     * Whether a transition row has been scrolled fully into view.
     *
     * This is how scrolling reaches the next chapter, and it is **not** a
     * gesture. Two attempts at detecting an overscroll — one on each
     * nested-scroll phase — never fired once. Asking the layout whether a
     * particular item is fully on screen is the same question `atStripEnd`
     * answers, and that one demonstrably works: it is what marks a chapter read.
     *
     * TachiyomiSY reaches the same place from the other end. Its list already
     * holds the neighbouring chapters' pages, so scrolling *into* them is the
     * whole mechanism. Here the row is the end of the list, so its arrival is
     * the signal instead.
     */
    fun rowFullyVisible(index: Int): Boolean {
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.lastOrNull { it.index == index }
        return row != null &&
            row.offset >= info.viewportStartOffset &&
            row.offset + row.size <= info.viewportEndOffset
    }

    // Paged mode asks the pager instead of the layout, and asks **settledPage**
    // rather than currentPage. A fling from page 3 to the tail reports every
    // page it passes through on `currentPage`; settledPage moves once, when the
    // scroll stops. Reading the live value here would advance the chapter mid-
    // fling from a page the user never stopped on — the same trap §5 records
    // against the library tab row, where two effects drove one position.
    //
    // **The strip needs the same thing and did not have it until 0.148.**
    // `rowFullyVisible` queries the live layout, so a fling that carried the
    // row on screen turned the chapter while the list was still moving — from
    // a position nobody stopped at, which is exactly what settledPage exists to
    // prevent one mode over. `!isScrollInProgress` is the strip's settledPage:
    // it says the same thing to a LazyColumn that settledPage says to a pager.
    val atTailRow by remember(pages.size, headRows, settings.mode) {
        derivedStateOf {
            pages.isNotEmpty() && if (settings.mode == ReaderMode.LONG_STRIP) {
                rowFullyVisible(pages.size + headRows) && !listState.isScrollInProgress
            } else {
                pagerState.settledPage == pages.size + headRows
            }
        }
    }
    val atHeadRow by remember(pages.size, headRows, settings.mode) {
        derivedStateOf {
            pages.isNotEmpty() && if (settings.mode == ReaderMode.LONG_STRIP) {
                rowFullyVisible(0) && !listState.isScrollInProgress
            } else {
                pagerState.settledPage == 0
            }
        }
    }

    /**
     * A row must have been *off* screen before its arrival counts.
     *
     * Without this, a chapter short enough to fit its transition row on the
     * first frame would open and immediately jump onward, and a one-page chapter
     * would be unreadable. Requiring the false state first makes the trigger a
     * scroll rather than a coincidence of layout. Reset per chapter, because
     * each one starts the argument again.
     */
    val leftTail = remember(chapterIndex) { booleanArrayOf(false) }
    val leftHead = remember(chapterIndex) { booleanArrayOf(false) }

    LaunchedEffect(atTailRow) {
        if (!atTailRow) {
            leftTail[0] = true
        } else if (leftTail[0] && hasNext) {
            // The dwell is a delay inside the effect rather than a timer beside
            // it, and that is the whole trick: LaunchedEffect is cancelled the
            // moment its key changes, so scrolling off the row before the wait
            // is up cancels the turn with no bookkeeping and no flag to get
            // wrong. Nothing has to remember that a turn was pending.
            delay(TRANSITION_DWELL_MS)
            onNext()
        }
    }
    LaunchedEffect(atHeadRow) {
        if (!atHeadRow) {
            leftHead[0] = true
        } else if (leftHead[0] && hasPrev) {
            delay(TRANSITION_DWELL_MS)
            onPrev()
        }
    }

    // Declared here rather than beside the slider below, because `currentPage`
    // needs it and Kotlin locals must be declared before use — the 0.110 CI
    // failure exactly.
    val lastPage = (pages.size - 1).coerceAtLeast(0)

    val currentPage = when {
        // Minus headRows, because index 0 is now the previous-chapter
        // transition. Settling on either transition clamps to the nearest real
        // page, which is what makes the tail count as "finished" for
        // read-marking rather than as a page past the end.
        settings.mode != ReaderMode.LONG_STRIP ->
            (pagerState.currentPage - headRows).coerceIn(0, lastPage)

        // A strip's last page is visible at the bottom of the screen long
        // before it is ever the *first* item on it, so firstVisibleItemIndex
        // tops out one or two short of the end and never reports the last page
        // at all. That's both halves of the same bug: the counter read "66/68"
        // at the bottom of a chapter, and "page >= total - 1" — which is what
        // marks a chapter read — could not fire.
        atStripEnd -> pages.lastIndex

        // `lastPage`, not `pages.lastIndex`: on an empty list the latter is -1
        // and coerceIn(0, -1) throws. Latent before this release and free to
        // close now that the bounded value exists a few lines up.
        else -> (listState.firstVisibleItemIndex - headRows).coerceIn(0, lastPage)
    }
    LaunchedEffect(currentPage) { onProgress(currentPage) }

    // Where the thumb is mid-drag, null when it isn't. Hoisted because there
    // are now two sliders that mean the same thing — one in the control bar, one
    // standing up at the edge — and the page count above the bar has to follow
    // whichever is being dragged.
    val seekTargetState = remember(pages.size) { mutableStateOf<Float?>(null) }
    var seekTarget by seekTargetState
    val seekPage = seekTarget?.roundToInt()?.coerceIn(0, lastPage) ?: currentPage

    // Committed on release, never during the drag: every intermediate value
    // would be a scroll request, a savePage write and a History.touch, because
    // onProgress fires on every page change.
    fun commitSeek() {
        val target = seekTarget?.roundToInt()?.coerceIn(0, lastPage)
        seekTarget = null
        if (target == null || pages.isEmpty()) return
        scope.launch {
            if (settings.mode == ReaderMode.LONG_STRIP) listState.scrollToItem(target + headRows)
            else pagerState.scrollToPage(target + headRows)
        }
    }

    // Right-to-left runs the pager the other way (`reverseLayout` below), but
    // `currentPage` stays logical — page 0 is still the first page, it is simply
    // drawn on the right. Left alone, the horizontal slider then increases
    // rightwards while the chapter advances leftwards, so dragging the thumb the
    // way the pages are going walks *backwards*. That is the 0.63 vertical
    // slider one axis over, and it is invisible until someone drags it.
    //
    // 0.88 mirrored the value and got the direction right and the fill wrong:
    // page 1 arrived as the maximum, so the track was full at the start of the
    // chapter and empty at the end. Mirroring the *widget* is the whole fix —
    // Material3's Slider reads the layout direction for its track, its thumb and
    // its drag-to-value mapping alike, so under RTL all three turn over together
    // and the value handed to it stays an ordinary page number.
    //
    // Only the horizontal one. The vertical slider means "further into the
    // chapter" downwards in every mode, which right-to-left doesn't change.
    val rtl = settings.mode == ReaderMode.PAGED_RTL


    return ReaderNavigationState(
        headRows = headRows,
        pagerState = pagerState,
        listState = listState,
        currentPage = currentPage,
        lastPage = lastPage,
        seekTargetState = seekTargetState,
        rtl = rtl,
        commitSeek = { commitSeek() },
    )
}
