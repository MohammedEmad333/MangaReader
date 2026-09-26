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
    onClose: () -> Unit
) {
    val view = LocalView.current
    val context = view.context
    val scope = rememberCoroutineScope()

    val globalSettings = remember(seriesId) { ReaderPrefs.load(context) }
    val initialSeriesSettings = remember(seriesId) {
        ReaderSeriesPrefs.load(context, seriesId)
    }
    var useGlobalDefaults by remember(seriesId) {
        mutableStateOf(initialSeriesSettings == null)
    }
    var settings by remember(seriesId) {
        mutableStateOf(initialSeriesSettings ?: globalSettings)
    }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }

    // Persist on every edit rather than on dismiss: the sheet can be swiped away
    // and the screen can be left by the system, and losing a setting because it
    // was closed the wrong way is the kind of bug nobody reports.
    fun update(next: ReaderSettings) {
        settings = next
        if (useGlobalDefaults || seriesId.isBlank()) {
            ReaderPrefs.save(context, next)
        } else {
            ReaderSeriesPrefs.save(context, seriesId, next)
        }
    }

    fun setUseGlobalDefaults(enabled: Boolean) {
        useGlobalDefaults = enabled || seriesId.isBlank()
        settings = if (useGlobalDefaults) {
            ReaderSeriesPrefs.clear(context, seriesId)
            ReaderPrefs.load(context)
        } else {
            val snapshot = settings
            ReaderSeriesPrefs.save(context, seriesId, snapshot)
            snapshot
        }
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

    val navigation = rememberReaderNavigationState(
        pages = pages,
        initialPage = initialPage,
        settings = settings,
        chapterIndex = chapterIndex,
        hasPrev = hasPrev,
        hasNext = hasNext,
        onPrev = onPrev,
        onNext = onNext,
        onProgress = onProgress,
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
                onTap = { showControls = !showControls },
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
        useGlobalDefaults = useGlobalDefaults,
        canOverrideSeries = seriesId.isNotBlank(),
        onUseGlobalDefaultsChange = ::setUseGlobalDefaults,
        onSettingsChange = { update(it) },
    )

}
