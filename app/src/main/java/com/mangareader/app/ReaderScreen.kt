package com.mangareader.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import java.io.File

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

    val currentPage = if (settings.mode == ReaderMode.LONG_STRIP) {
        listState.firstVisibleItemIndex
    } else {
        pagerState.currentPage
    }
    LaunchedEffect(currentPage) { onProgress(currentPage) }

    val sidePadding = (LocalConfiguration.current.screenWidthDp * settings.sidePadding / 100).dp
    val filter = readerColorFilter(settings.grayscale, settings.inverted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(settings.background.toColor())
    ) {
        val pageModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePadding)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = !showControls })
            }

        if (settings.mode == ReaderMode.LONG_STRIP) {
            LazyColumn(state = listState, modifier = pageModifier) {
                itemsIndexed(pages) { index, file ->
                    ReaderPage(
                        file = file,
                        index = index,
                        stillLoading = stillLoading,
                        colorFilter = filter,
                        // Height is left to the image in a strip: a fixed one
                        // would letterbox every page to the screen and reinstate
                        // the gaps this mode exists to remove.
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
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
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (settings.showPageNumber && !showControls && pages.isNotEmpty()) {
            Text(
                text = "${currentPage + 1} / ${pages.size}",
                color = Color.White.copy(alpha = 0.75f),
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
            visible = showControls,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                page = currentPage + 1,
                total = pages.size,
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
    contentScale: ContentScale
) {
    when {
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
            modifier = modifier.heightIn(min = 240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        else -> Box(
            modifier = modifier.heightIn(min = 240.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Page ${index + 1} couldn't be loaded",
                color = Color.White,
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
            TextButton(onClick = onClose) { Text("\u2190") }
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

@Composable
private fun ChapterPickerSheet(
    chapters: List<Chapter>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
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
