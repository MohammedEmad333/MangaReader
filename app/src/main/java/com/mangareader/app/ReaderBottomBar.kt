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
