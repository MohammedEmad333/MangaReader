package com.mangareader.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ReaderPage(
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

/**
 * The row between two chapters, at each end of a strip.
 *
 * Modelled on TachiyomiSY's `ChapterTransition`: two labelled lines, or a single
 * fallback when there is nothing on the other side. It is an *item in the list*
 * rather than an overscroll affordance, which is the whole point — it can be
 * read, it can be tapped, and it cannot misfire.
 *
 * SY goes further and loads the neighbouring chapters' pages into the same list,
 * so scrolling simply continues into them and this row is what you pass through.
 * That needs a reader that holds three chapters at once; this one holds one, so
 * here the row is the destination rather than a divider.
 */
@Composable
internal fun ChapterTransitionRow(
    topLabel: String?,
    topName: String?,
    bottomLabel: String?,
    bottomName: String?,
    fallback: String,
    textColor: Color,
    onClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable { onClick() })
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Both sides absent means there is no neighbour in that direction, which
        // is worth saying rather than leaving a blank gap at the end of a
        // chapter that looks like something failed to load.
        if (topName == null && bottomName == null) {
            Text(fallback, color = textColor, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        listOfNotNull(
            topLabel?.let { it to topName },
            bottomLabel?.let { it to bottomName }
        ).forEach { (label, name) ->
            if (name == null) return@forEach
            Column {
                Text(
                    label,
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(name, color = textColor, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (onClick != null) {
            Text(
                "Keep scrolling, or tap",
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun ReaderTopBar(title: String, subtitle: String, onClose: () -> Unit) {
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
internal fun ReaderBottomBar(
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
internal fun OutlinedText(
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
internal fun VerticalSlider(
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
internal fun ChapterPickerSheet(
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
