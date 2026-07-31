package com.mangareader.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

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
@OptIn(ExperimentalMaterial3Api::class)
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

    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

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
            val last = info.visibleItemsInfo.lastOrNull()
            pages.isNotEmpty() &&
                info.totalItemsCount == pages.size &&
                last != null &&
                last.index == pages.lastIndex &&
                last.offset + last.size <= info.viewportEndOffset
        }
    }

    val currentPage = when {
        settings.mode != ReaderMode.LONG_STRIP -> pagerState.currentPage

        // A strip's last page is visible at the bottom of the screen long
        // before it is ever the *first* item on it, so firstVisibleItemIndex
        // tops out one or two short of the end and never reports the last page
        // at all. That's both halves of the same bug: the counter read "66/68"
        // at the bottom of a chapter, and "page >= total - 1" — which is what
        // marks a chapter read — could not fire.
        atStripEnd -> pages.lastIndex

        else -> listState.firstVisibleItemIndex
    }
    LaunchedEffect(currentPage) { onProgress(currentPage) }

    // Where the thumb is mid-drag, null when it isn't. Hoisted because there
    // are now two sliders that mean the same thing — one in the control bar, one
    // standing up at the edge — and the page count above the bar has to follow
    // whichever is being dragged.
    var seekTarget by remember(pages.size) { mutableStateOf<Float?>(null) }
    val lastPage = (pages.size - 1).coerceAtLeast(0)
    val seekPage = seekTarget?.roundToInt()?.coerceIn(0, lastPage) ?: currentPage

    // Committed on release, never during the drag: every intermediate value
    // would be a scroll request, a savePage write and a History.touch, because
    // onProgress fires on every page change.
    fun commitSeek() {
        val target = seekTarget?.roundToInt()?.coerceIn(0, lastPage)
        seekTarget = null
        if (target == null || pages.isEmpty()) return
        scope.launch {
            if (settings.mode == ReaderMode.LONG_STRIP) listState.scrollToItem(target)
            else pagerState.scrollToPage(target)
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

    /**
     * Carries a scroll past the top or bottom of a strip into the neighbouring
     * chapter.
     *
     * `onPostScroll` only sees a non-zero `available` when the list could not
     * consume the gesture, which *is* the at-an-edge condition — so there is no
     * separate "am I at the end" test to get wrong, and in particular none that
     * can be answered before the first measure (§5's `canScrollForward` trap).
     *
     * Positive y is the finger travelling down, which reveals what is above:
     * the previous chapter. Negative is the next one.
     *
     * **Long strip only.** In a paged mode the equivalent gesture belongs to the
     * pager and would have to be mirrored for right-to-left — the exact shape
     * this project has now shipped backwards twice, in 0.63 and again in 0.88.
     * Paged mode has Prev and Next in the control bar.
     */
    val pull = remember(chapterIndex) { floatArrayOf(0f) }
    val edgeTrigger = with(LocalDensity.current) { 140.dp.toPx() }
    val edgeScroll = remember(chapterIndex, hasPrev, hasNext, edgeTrigger) {
        object : NestedScrollConnection {
            /**
             * **`onPreScroll`, not `onPostScroll`, and that was 0.101's bug.**
             *
             * Post-scroll looked ideal because a non-zero `available` there
             * *is* the at-an-edge condition, with no separate test to get
             * wrong. It never fires: the overscroll effect — the stretch at the
             * end of a list — consumes the leftover delta before a parent
             * connection is offered it, so `available` was always zero and the
             * whole feature did nothing.
             *
             * Pre-scroll always fires, which means the edge has to be tested
             * explicitly after all. `atStripEnd` is the one already used to
             * decide the chapter is finished, so the two agree by construction,
             * and it is layout-derived rather than a `canScrollForward` that
             * lies before the first measure. Nothing is consumed here, so
             * ordinary scrolling is untouched.
             */
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                // Drag only. A fling that merely reaches the end of a chapter
                // arrives here carrying whatever momentum is left, which would
                // turn "I flicked to the bottom" into "open the next chapter".
                if (dy == 0f || source != NestedScrollSource.Drag) return Offset.Zero

                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                when {
                    dy > 0f && atTop && hasPrev -> {
                        pull[0] += dy
                        if (pull[0] > edgeTrigger) { pull[0] = 0f; onPrev() }
                    }
                    dy < 0f && atStripEnd && hasNext -> {
                        pull[0] += dy
                        if (pull[0] < -edgeTrigger) { pull[0] = 0f; onNext() }
                    }
                    // Anywhere else in the chapter, including an edge with no
                    // neighbour to go to. Reset rather than let a pull survive
                    // being scrolled away from and resumed later.
                    else -> pull[0] = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // A drag that stopped short must not add to the next one.
                pull[0] = 0f
                return Velocity.Zero
            }
        }
    }

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

        // Long strip can keep one tap detector over the whole list, because
        // nothing inside it handles gestures. Paged mode can't: a zoomable page
        // consumes its own pointer events, so a detector up here would never
        // see a tap on an image. The tap is handed to the pages instead.
        val stripModifier = pageModifier.pointerInput(Unit) {
            detectTapGestures(onTap = { showControls = !showControls })
        }

        if (settings.mode == ReaderMode.LONG_STRIP) {
            LazyColumn(
                state = listState,
                modifier = stripModifier.nestedScroll(edgeScroll)
            ) {
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
            }
        } else {
            HorizontalPager(
                state = pagerState,
                reverseLayout = settings.mode == ReaderMode.PAGED_RTL,
                modifier = pageModifier
            ) { index ->
                ReaderPage(
                    file = pages.getOrNull(index),
                    index = index,
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

        if (settings.showPageNumber && !showControls && pages.isNotEmpty()) {
            OutlinedText(
                text = "${currentPage + 1} / ${pages.size}",
                color = onBackground,
                outline = outlineBackground,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = seriesTitle,
                subtitle = chapterName,
                onClose = onClose
            )
        }

        AnimatedVisibility(
            visible = showControls &&
                settings.sliderPosition == ReaderSliderPosition.VERTICAL &&
                pages.size > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large
            ) {
                VerticalSlider(
                    value = seekTarget ?: currentPage.toFloat(),
                    onValueChange = { seekTarget = it },
                    onValueChangeFinished = { commitSeek() },
                    valueRange = 0f..lastPage.toFloat(),
                    length = verticalSliderLength,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                page = seekPage + 1,
                total = pages.size,
                // The vertical one lives at the screen edge instead; the bar
                // keeps its buttons either way.
                showSlider = settings.sliderPosition == ReaderSliderPosition.HORIZONTAL &&
                    pages.size > 1,
                sliderValue = seekTarget ?: currentPage.toFloat(),
                onSliderChange = { seekTarget = it },
                mirrorSlider = rtl,
                onSliderCommit = { commitSeek() },
                hasPrev = hasPrev,
                hasNext = hasNext,
                onPrev = onPrev,
                onNext = onNext,
                onChapters = { showChapters = true },
                onSettings = { showSettings = true }
            )
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            ChapterPickerSheet(
                chapters = chapters,
                current = chapterIndex,
                onSelect = {
                    showChapters = false
                    showControls = false
                    onSelectChapter(it)
                }
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettingsSheet(settings = settings, onChange = { update(it) })
        }
    }
}

/** Window-level settings: orientation, backlight, system bars. */
@Composable
private fun ReaderWindowEffects(settings: ReaderSettings, showControls: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity

    DisposableEffect(settings.rotation) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = when (settings.rotation) {
            ReaderRotation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderRotation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderRotation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        // Restored on the way out: a reader that locks the whole app to
        // landscape is a bug report, not a setting.
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        val window = activity?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(settings.customBrightness, settings.brightness) {
        val window = activity?.window
        window?.attributes = window?.attributes?.apply {
            screenBrightness = if (settings.customBrightness) {
                settings.brightness.coerceIn(0.01f, 1f)
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Tied to the controls as well as the setting: raising the bars while the
    // status bar stays hidden puts the title under the clock.
    DisposableEffect(settings.fullscreen, showControls) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (settings.fullscreen && !showControls) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun ReaderPage(
    file: File?,
    index: Int,
    stillLoading: Boolean,
    colorFilter: ColorFilter?,
    modifier: Modifier,
    contentScale: ContentScale,
    /** Readable against whatever the reader background is set to. */
    textColor: Color,
    /** Pinch, double-tap and pan. Paged modes only — see the call site. */
    zoomable: Boolean = false,
    /** Non-null when this page is responsible for its own taps. */
    onTap: (() -> Unit)? = null
) {
    // A placeholder handles no gestures of its own, so where the page owns the
    // tap it has to be attached here too — otherwise tapping a page that hasn't
    // loaded would be the one dead spot on the screen.
    val placeholder =
        if (onTap == null) modifier
        else modifier.pointerInput(Unit) { detectTapGestures { onTap() } }

    when {
        file != null && zoomable -> ZoomableAsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter,
            contentScale = contentScale,
            // Its own, because a tap this consumes never reaches the pager.
            onClick = { onTap?.invoke() }
        )
        file != null -> AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter
        )
        // Null while the chapter is still downloading means "not here yet";
        // null once it has finished means that page failed.
        stillLoading -> Box(
            modifier = placeholder.heightIn(min = 240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        else -> Box(
            modifier = placeholder.heightIn(min = 240.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Page ${index + 1} couldn't be loaded",
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReaderTopBar(title: String, subtitle: String, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ui.BackButton, like every other screen. This was the one place
            // still drawing its own arrow as a TextButton, which sat at a
            // different size and alignment to the rest of the app.
            BackButton(onClose)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    page: Int,
    total: Int,
    showSlider: Boolean,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onSliderCommit: () -> Unit,
    /** Lay the slider out right-to-left, for a right-to-left reading mode. */
    mirrorSlider: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (total > 0) "Page $page of $total" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 2.dp)
            )

            // The caller has already ruled out a one-page chapter: a Slider whose
            // range starts and ends at the same value is not a legal Slider.
            if (showSlider) {
                // Scoped to the slider alone. The button row below keeps its
                // reading order — Prev stays on the left, where the hand that
                // has been tapping it expects it, and only the control whose
                // geometry means something is turned over.
                CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (mirrorSlider) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Slider(
                        value = sliderValue,
                        onValueChange = onSliderChange,
                        onValueChangeFinished = onSliderCommit,
                        valueRange = 0f..(total - 1).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrev, enabled = hasPrev) { Text("Prev") }
                TextButton(onClick = onChapters) { Text("Chapters") }
                TextButton(onClick = onSettings) { Text("Settings") }
                TextButton(onClick = onNext, enabled = hasNext) { Text("Next") }
            }
        }
    }
}

/**
 * [text] drawn in [color] over an outline of [outline].
 *
 * Four offset copies underneath a normal one, rather than a stroked
 * [androidx.compose.ui.text.TextStyle]. `drawStyle` would be one parameter
 * instead of five composables and it is the obvious way to write this — the
 * reason it isn't used is that there's no compiler in this loop, `drawStyle`
 * spent a release opted-in behind `@ExperimentalTextApi`, and finding out which
 * side of that line this Compose version falls on costs a CI round trip. Offsets
 * and colours have been stable for the whole life of Compose.
 *
 * The copies are opaque on purpose. A translucent outline under a translucent
 * fill compounds where they overlap, which draws a visible seam around every
 * glyph — the exact artefact this is meant to remove.
 */
@Composable
private fun OutlinedText(
    text: String,
    color: Color,
    outline: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val w = 1.2.dp
        // Diagonals only: at this width the four of them already close the ring,
        // and the axis-aligned four would be four more Text layouts for a
        // difference nobody can see on a label this size.
        listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (dx, dy) ->
            Text(
                text = text,
                color = outline,
                style = style,
                modifier = Modifier.offset(x = w * dx, y = w * dy)
            )
        }
        Text(text = text, color = color, style = style)
    }
}

/**
 * A [Slider] stood on end.
 *
 * Compose has no vertical slider, and rotating one is only half the job:
 * `Modifier.rotate` changes what is drawn and never what was measured, so a
 * rotated slider still claims its whole length horizontally and shoulders the
 * page aside. The `layout` block reports the rotated footprint instead —
 * measure the child as usual, then hand the parent the swapped dimensions and
 * place the child centred inside them.
 *
 * Pointer input travels through the same transform, so the drag runs along the
 * axis it looks like it should.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    length: Dp,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = (placeable.height - placeable.width) / 2,
                        y = (placeable.width - placeable.height) / 2
                    )
                }
            }
            // Clockwise, not anti-: rotating the other way puts the slider's
            // start at the bottom, so dragging down walked *backwards* through
            // the chapter while looking perfectly normal sitting there.
            .rotate(90f)
            .requiredWidth(length)
    )
}

@Composable
private fun ChapterPickerSheet(
    chapters: List<Chapter>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    // Opens on the chapter being read instead of at the top of the series. A
    // 400-chapter list in a 420dp window shows about eight rows, so the picker
    // was landing hundreds of rows from anything the reader could want, and the
    // one thing it is for — stepping to a neighbouring chapter — was the hardest
    // thing to do with it.
    //
    // Seeded into the state rather than scrolled to from an effect: an effect
    // runs after the first composition, so the list would be drawn at the top
    // and then jump. Two rows of lead-in, so the current chapter isn't jammed
    // against the top edge with nothing above it to show there is more.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                val selected = index == current
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Layout") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Screen") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Colour") })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            when (tab) {
                0 -> {
                    ChipRow(
                        label = "Reading mode",
                        options = ReaderMode.entries.map { it.label },
                        selected = ReaderMode.entries.indexOf(settings.mode),
                        onSelect = { onChange(settings.copy(mode = ReaderMode.entries[it])) }
                    )
                    ChipRow(
                        label = "Rotation",
                        options = ReaderRotation.entries.map { it.label },
                        selected = ReaderRotation.entries.indexOf(settings.rotation),
                        onSelect = { onChange(settings.copy(rotation = ReaderRotation.entries[it])) }
                    )
                    ChipRow(
                        label = "Page slider",
                        options = ReaderSliderPosition.entries.map { it.label },
                        selected = ReaderSliderPosition.entries.indexOf(settings.sliderPosition),
                        onSelect = {
                            onChange(
                                settings.copy(sliderPosition = ReaderSliderPosition.entries[it])
                            )
                        }
                    )
                    SliderRow(
                        label = "Side padding",
                        value = settings.sidePadding.toFloat(),
                        valueLabel = "${settings.sidePadding}%",
                        range = 0f..25f,
                        steps = 4,
                        onChange = { onChange(settings.copy(sidePadding = it.toInt())) }
                    )
                }
                1 -> {
                    ChipRow(
                        label = "Background",
                        options = ReaderBackground.entries.map { it.label },
                        selected = ReaderBackground.entries.indexOf(settings.background),
                        onSelect = {
                            onChange(settings.copy(background = ReaderBackground.entries[it]))
                        }
                    )
                    SwitchRow("Show page number", settings.showPageNumber) {
                        onChange(settings.copy(showPageNumber = it))
                    }
                    SwitchRow("Fullscreen", settings.fullscreen) {
                        onChange(settings.copy(fullscreen = it))
                    }
                    SwitchRow("Keep screen on", settings.keepScreenOn) {
                        onChange(settings.copy(keepScreenOn = it))
                    }
                }
                else -> {
                    SwitchRow("Grayscale", settings.grayscale) {
                        onChange(settings.copy(grayscale = it))
                    }
                    SwitchRow("Invert colours", settings.inverted) {
                        onChange(settings.copy(inverted = it))
                    }
                    SwitchRow("Custom brightness", settings.customBrightness) {
                        onChange(settings.copy(customBrightness = it))
                    }
                    if (settings.customBrightness) {
                        SliderRow(
                            label = "Brightness",
                            value = settings.brightness,
                            valueLabel = "${(settings.brightness * 100).toInt()}%",
                            range = 0.01f..1f,
                            steps = 0,
                            onChange = { onChange(settings.copy(brightness = it)) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
    // Horizontal scroll rather than a wrap: FlowRow is still experimental on
    // this Compose version, which is the same reason the genre chips on the
    // series screen scroll.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        valueRange = range,
        steps = steps
    )
}

@Composable
private fun ReaderBackground.toColor(): Color = when (this) {
    ReaderBackground.BLACK -> Color.Black
    ReaderBackground.GRAY -> Color(0xFF2B2B2B)
    ReaderBackground.WHITE -> Color.White
    ReaderBackground.THEME -> MaterialTheme.colorScheme.background
}

/**
 * Grayscale and inversion as a single colour matrix.
 *
 * The two-filter case is a precomputed matrix rather than one applied after the
 * other: composing matrices needs an operator whose argument order is easy to
 * get backwards, and inverted luminance is short enough to write out.
 */
private fun readerColorFilter(grayscale: Boolean, inverted: Boolean): ColorFilter? = when {
    grayscale && inverted -> ColorFilter.colorMatrix(ColorMatrix(GRAY_INVERT_MATRIX))
    grayscale -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    inverted -> ColorFilter.colorMatrix(ColorMatrix(INVERT_MATRIX))
    else -> null
}

private val INVERT_MATRIX = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** Rec. 709 luminance weights, negated, with the offset that inverts them. */
private val GRAY_INVERT_MATRIX = floatArrayOf(
    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
    -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)
