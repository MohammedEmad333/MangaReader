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
