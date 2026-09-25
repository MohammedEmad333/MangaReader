package com.mangareader.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * How long a transition row must sit still on screen before the chapter turns.
 *
 * The row used to fire the moment it qualified, which made reaching the end of
 * a chapter feel like being thrown into the next one — you never saw what you
 * were being told. A short dwell makes the row a thing you arrive at and then
 * pass, and it costs nothing to back out of: scrolling away cancels the
 * LaunchedEffect that is waiting, so leaving is free until the moment it fires.
 *
 * Long enough to read "Next: Chapter 12", short enough that a deliberate scroll
 * to the end does not feel stuck.
 */
private const val TRANSITION_DWELL_MS = 550L

/**
 * The reader.
 *
 * Controls are hidden until a tap. Tapping raises a top bar carrying the series
 * title and chapter, and a bottom bar with chapter navigation, a chapter picker
 * and settings — the shape most readers use, arrived at here because the screen
 * is the content and anything permanent on it is in the way.
 *
 * **On the settings that aren't here.** The set below is the subset that this
 * reader can actually honour today. Crop borders, split/rotate wide pages, and
 * tap-zone layouts all need work in the page pipeline rather than a switch — the
 * first two need to inspect and cut the bitmap, and tap zones need a gesture
 * model this screen doesn't have. They were deliberately left out rather than
 * shipped as switches that do nothing, which is worse than an absent feature
 * because it costs a build cycle to discover.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReaderScreen(
    pages: List<File?>,
    stillLoading: Boolean,
    initialPage: Int,
    seriesTitle: String,
    chapterName: String,
    chapters: List<Chapter>,
    chapterIndex: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onProgress: (Int) -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current
    val context = view.context
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(ReaderPrefs.load(context)) }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }

    // Persist on every edit rather than on dismiss: the sheet can be swiped away
    // and the screen can be left by the system, and losing a setting because it
    // was closed the wrong way is the kind of bug nobody reports.
    fun update(next: ReaderSettings) {
        settings = next
        ReaderPrefs.save(context, next)
    }

    BackHandler {
        when {
            showSettings -> showSettings = false
            showChapters -> showChapters = false
            showControls -> showControls = false
            else -> onClose()
        }
    }

    ReaderWindowEffects(settings, showControls)

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
    var seekTarget by remember(pages.size) { mutableStateOf<Float?>(null) }
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

    // A gesture that carried a scroll past the end of a strip into the next
    // chapter lived here across 0.101 and 0.102 and never fired once, on two
    // different nested-scroll phases. It is deleted rather than attempted a
    // third time.
    //
    // The reason it was the wrong shape is worth keeping: **TachiyomiSY does not
    // detect an edge at all.** Its webtoon adapter builds one list spanning
    // three chapters — previous pages, a transition row, current pages, a
    // transition row, next pages — so scrolling simply continues into the
    // neighbour. There is no gesture to get right because there is no gesture.
    // The transition rows below are the visible half of that design; the other
    // half needs a reader that holds three chapters at once, which this one
    // does not. See §7 of the handoff.
    val sidePadding = (LocalConfiguration.current.screenWidthDp * settings.sidePadding / 100).dp

    // Half the screen, so the whole chapter is a comfortable thumb-sweep. A
    // fixed 240dp was a quarter of a tall phone and read as a stub.
    val verticalSliderLength = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

    val backgroundColor = settings.background.toColor()
    // White page number on a white page is invisible, and "Theme" can be either
    // colour depending on the app theme. Taken from the background's own
    // luminance rather than assuming the reader is dark.
    val lightBackground = backgroundColor.luminance() > 0.5f
    val onBackground = if (lightBackground) Color.Black else Color.White
    // The page number sits over the *page*, not the background, so matching the
    // background is only half an answer — a dark panel in a bright scan swallows
    // black text as readily as a white page swallows white. The outline is the
    // opposite colour, so whichever of the two the artwork happens to match,
    // the other one is still there to read the digits against.
    val outlineBackground = if (lightBackground) Color.White else Color.Black
    val filter = readerColorFilter(settings.grayscale, settings.inverted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val pageModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePadding)

        // Long strip can keep its tap detector over the whole list, because
        // nothing inside it handles gestures. Paged mode can't: a zoomable page
        // consumes its own pointer events, so a detector up here would never
        // see a tap on an image. The tap is handed to the pages instead.
        //
        // The strip's detector now lives further down, on the zoomed modifier,
        // because it also has to handle double-tap-to-zoom and therefore needs
        // the zoom state.
        val stripModifier = pageModifier

        val zoomedStripModifier = rememberReaderStripZoomModifier(
            baseModifier = stripModifier,
            chapterIndex = chapterIndex,
            listState = listState,
            scope = scope,
            onToggleControls = { showControls = !showControls },
        )

        if (settings.mode == ReaderMode.LONG_STRIP) {
            LazyColumn(
                state = listState,
                modifier = zoomedStripModifier
            ) {
                item {
                    ChapterTransitionRow(
                        topLabel = if (hasPrev) "Previous" else null,
                        topName = if (hasPrev) chapters.getOrNull(chapterIndex - 1)?.name else null,
                        bottomLabel = "Current",
                        bottomName = chapterName,
                        fallback = "There's no previous chapter",
                        textColor = onBackground,
                        onClick = if (hasPrev) onPrev else null
                    )
                }
                itemsIndexed(pages) { index, file ->
                    ReaderPage(
                        file = file,
                        index = index,
                        stillLoading = stillLoading,
                        colorFilter = filter,
                        // Height is left to the image in a strip: a fixed one
                        // would letterbox every page to the screen and reinstate
                        // the gaps this mode exists to remove. A *minimum* is
                        // not that, and it's load-bearing — an AsyncImage with
                        // an unbounded height measures zero until its bitmap
                        // decodes, so every undecoded page was a zero-height
                        // row. The whole chapter collapsed into a few hundred
                        // pixels, then shoved itself apart page by page as the
                        // images arrived, which is the reader "scrolling down
                        // on its own" while untouched. It also made the list
                        // briefly unscrollable, which the end-of-chapter test
                        // above would otherwise read as "at the last page".
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        contentScale = ContentScale.FillWidth,
                        textColor = onBackground
                    )
                }
                item {
                    ChapterTransitionRow(
                        topLabel = "Finished",
                        topName = chapterName,
                        bottomLabel = if (hasNext) "Next" else null,
                        bottomName = if (hasNext) chapters.getOrNull(chapterIndex + 1)?.name else null,
                        fallback = "There's no next chapter",
                        textColor = onBackground,
                        onClick = if (hasNext) onNext else null
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                reverseLayout = settings.mode == ReaderMode.PAGED_RTL,
                modifier = pageModifier
            ) { index ->
                // Index 0 and the last index are transition pages, not pages of
                // the chapter. Under RTL the pager is reversed, so the previous
                // -chapter transition sits on the right — which is where a
                // right-to-left reader starts, so it needs no special case.
                when (index) {
                    0 -> ChapterTransitionRow(
                        topLabel = if (hasPrev) "Previous" else null,
                        topName = if (hasPrev) chapters.getOrNull(chapterIndex - 1)?.name else null,
                        bottomLabel = "Current",
                        bottomName = chapterName,
                        fallback = "There's no previous chapter",
                        textColor = onBackground,
                        onClick = if (hasPrev) onPrev else null
                    )

                    pages.size + headRows -> ChapterTransitionRow(
                        topLabel = "Finished",
                        topName = chapterName,
                        bottomLabel = if (hasNext) "Next" else null,
                        bottomName = if (hasNext) chapters.getOrNull(chapterIndex + 1)?.name
                        else null,
                        fallback = "There's no next chapter",
                        textColor = onBackground,
                        onClick = if (hasNext) onNext else null
                    )

                    else -> ReaderPage(
                        file = pages.getOrNull(index - headRows),
                        index = index - headRows,
                        stillLoading = stillLoading,
                        colorFilter = filter,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        textColor = onBackground,
                        // Paged only. In a strip the same gestures already belong to
                        // the list, and a pinch that also scrolls is neither.
                        zoomable = true,
                        onTap = { showControls = !showControls }
                    )
                }
            }
        }

        ReaderControlsOverlay(
            settings = settings,
            showControls = showControls,
            currentPage = currentPage,
            totalPages = pages.size,
            seekTarget = seekTarget,
            onSeekTargetChange = { seekTarget = it },
            onSeekCommit = { commitSeek() },
            verticalSliderLength = verticalSliderLength,
            rtl = rtl,
            hasPrev = hasPrev,
            hasNext = hasNext,
            onPrev = onPrev,
            onNext = onNext,
            onOpenChapters = { showChapters = true },
            onOpenSettings = { showSettings = true },
            seriesTitle = seriesTitle,
            chapterName = chapterName,
            onClose = onClose,
            pageTextColor = onBackground,
            pageOutlineColor = outlineBackground,
        )
    }

    ReaderSheets(
        showChapters = showChapters,
        onDismissChapters = { showChapters = false },
        chapters = chapters,
        chapterIndex = chapterIndex,
        onSelectChapter = {
            showChapters = false
            showControls = false
            onSelectChapter(it)
        },
        showSettings = showSettings,
        onDismissSettings = { showSettings = false },
        settings = settings,
        onSettingsChange = { update(it) },
    )

}
