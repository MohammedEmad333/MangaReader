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
import androidx.compose.ui.platform.LocalDensity
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
 * The reader.
 *
 * Controls are hidden until a tap. Tapping raises a top bar carrying the series
 * title and chapter, and a bottom bar with chapter navigation, a chapter picker
 * and settings — the shape most readers use, arrived at here because the screen
 * is the content and anything permanent on it is in the way.
 *
 * **On the settings that aren't here.** The set below is the subset that this
 * reader can actually honour today. Crop borders and split-wide-page support
 * still need bitmap-pipeline work. Wide-page rotation is handled in paged mode
 * by reading image bounds only, without decoding the full bitmap, and tap zones
 * are implemented by the page gesture layer rather than as cosmetic toggles.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReaderScreen(
    pages: List<File?>,
    stillLoading: Boolean,
    initialPage: Int,
    sourceId: String,
    seriesId: String,
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
    onVisiblePages: (List<Int>) -> Unit,
    onRetryPage: (Int) -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current
    val context = view.context
    val scope = rememberCoroutineScope()

    val globalSettings = remember(sourceId, seriesId) { ReaderPrefs.load(context) }
    val initialSeriesSettings = remember(sourceId, seriesId) {
        ReaderSeriesPrefs.load(context, sourceId, seriesId)
    }
    var useGlobalDefaults by remember(sourceId, seriesId) {
        mutableStateOf(initialSeriesSettings == null)
    }
    var settings by remember(sourceId, seriesId) {
        mutableStateOf(initialSeriesSettings ?: globalSettings)
    }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }

    LaunchedEffect(
        showControls,
        showSettings,
        showChapters,
        showPages,
        settings.controlsAutoHideSeconds,
    ) {
        if (
            showControls &&
            !showSettings &&
            !showChapters &&
            !showPages &&
            settings.controlsAutoHideSeconds > 0
        ) {
            delay(settings.controlsAutoHideSeconds * 1_000L)
            showControls = false
        }
    }

    // Persist on every edit rather than on dismiss: the sheet can be swiped away
    // and the screen can be left by the system, and losing a setting because it
    // was closed the wrong way is the kind of bug nobody reports.
    fun update(next: ReaderSettings) {
        settings = next
        if (useGlobalDefaults || sourceId.isBlank() || seriesId.isBlank()) {
            ReaderPrefs.save(context, next)
        } else {
            ReaderSeriesPrefs.save(context, sourceId, seriesId, next)
        }
    }

    fun setUseGlobalDefaults(enabled: Boolean) {
        useGlobalDefaults = enabled || sourceId.isBlank() || seriesId.isBlank()
        settings = if (useGlobalDefaults) {
            ReaderSeriesPrefs.clear(context, sourceId, seriesId)
            ReaderPrefs.load(context)
        } else {
            val snapshot = settings
            ReaderSeriesPrefs.save(context, sourceId, seriesId, snapshot)
            snapshot
        }
    }

    // System back and the reader toolbar Back button must share the same
    // dismissal order, otherwise the toolbar exits while a sheet is still open.
    fun navigateBack() {
        if (showPages) {
            showPages = false
            return
        }
        when (readerBackTarget(showSettings, showChapters, showControls)) {
            ReaderBackTarget.SETTINGS -> showSettings = false
            ReaderBackTarget.CHAPTERS -> showChapters = false
            ReaderBackTarget.CONTROLS -> showControls = false
            ReaderBackTarget.READER -> onClose()
        }
    }
    BackHandler { navigateBack() }

    ReaderWindowEffects(settings, showControls)

    val navigation = rememberReaderNavigationState(
        pages = pages,
        stillLoading = stillLoading,
        initialPage = initialPage,
        settings = settings,
        chapterIndex = chapterIndex,
        hasPrev = hasPrev,
        hasNext = hasNext,
        onPrev = onPrev,
        onNext = onNext,
        onProgress = onProgress,
        onVisiblePages = onVisiblePages,
        scope = scope,
    )
    val headRows = navigation.headRows
    val pagerState = navigation.pagerState
    val listState = navigation.listState
    val currentPage = navigation.currentPage
    val lastPage = navigation.lastPage
    var seekTarget by navigation.seekTargetState
    val rtl = navigation.rtl
    val commitSeek = navigation.commitSeek

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
    val configuration = LocalConfiguration.current
    val sidePadding = (configuration.screenWidthDp * settings.sidePadding / 100).dp
    val pageWidthPx = with(LocalDensity.current) {
        (configuration.screenWidthDp.dp - sidePadding * 2).toPx().coerceAtLeast(1f)
    }

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
            ReaderLongStripPages(
                listState = listState,
                modifier = zoomedStripModifier,
                pages = pages,
                stillLoading = stillLoading,
                chapters = chapters,
                chapterIndex = chapterIndex,
                chapterName = chapterName,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onPrev = onPrev,
                onNext = onNext,
                colorFilter = filter,
                textColor = onBackground,
                pageGap = settings.pageGap.dp,
                onRetryPage = onRetryPage,
            )
        } else {
            ReaderPagedPages(
                pagerState = pagerState,
                modifier = pageModifier,
                mode = settings.mode,
                pages = pages,
                stillLoading = stillLoading,
                headRows = headRows,
                chapters = chapters,
                chapterIndex = chapterIndex,
                chapterName = chapterName,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onPrev = onPrev,
                onNext = onNext,
                colorFilter = filter,
                textColor = onBackground,
                onTap = { x ->
                    val fraction = (x / pageWidthPx).coerceIn(0f, 1f)
                    if (!settings.tapZones) {
                        showControls = !showControls
                    } else when {
                        fraction < 1f / 3f -> {
                            if (rtl) navigation.nextPage() else navigation.previousPage()
                        }
                        fraction > 2f / 3f -> {
                            if (rtl) navigation.previousPage() else navigation.nextPage()
                        }
                        else -> showControls = !showControls
                    }
                },
                widePageMode = settings.widePageMode,
                onRetryPage = onRetryPage,
            )
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
            onOpenPages = { showPages = true },
            onOpenSettings = { showSettings = true },
            seriesTitle = seriesTitle,
            chapterName = chapterName,
            onClose = { navigateBack() },
            pageTextColor = onBackground,
            pageOutlineColor = outlineBackground,
        )
    }

    if (showPages) {
        ModalBottomSheet(onDismissRequest = { showPages = false }) {
            Text(
                text = "Chapter pages",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(pages) { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPages = false
                                showControls = false
                                scope.launch {
                                    val target = index + navigation.headRows
                                    if (settings.mode == ReaderMode.LONG_STRIP) {
                                        listState.scrollToItem(target)
                                    } else {
                                        pagerState.scrollToPage(target)
                                    }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (file != null) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Preview page ${index + 1}",
                                modifier = Modifier.size(width = 72.dp, height = 96.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(width = 72.dp, height = 96.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Not loaded", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "Page ${index + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
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
        useGlobalDefaults = useGlobalDefaults,
        canOverrideSeries = sourceId.isNotBlank() && seriesId.isNotBlank(),
        onUseGlobalDefaultsChange = ::setUseGlobalDefaults,
        onSettingsChange = { update(it) },
    )

}
