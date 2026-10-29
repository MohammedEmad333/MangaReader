package com.mangareader.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
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
    onTap: ((Float) -> Unit)? = null,
    widePageMode: ReaderWidePageMode = ReaderWidePageMode.FIT,
    onRetry: () -> Unit,
) {
    // A placeholder handles no gestures of its own, so where the page owns the
    // tap it has to be attached here too — otherwise tapping a page that hasn't
    // loaded would be the one dead spot on the screen.
    val placeholder =
        if (onTap == null) modifier
        else modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onTap(offset.x)
            }
        }

    val usableFile = file?.takeIf(::isUsableReaderPage)
    val rotateWide = remember(usableFile, widePageMode) {
        if (usableFile == null) {
            false
        } else {
            val (width, height) = imageBounds(usableFile)
            shouldRotateWidePage(width, height, widePageMode)
        }
    }

    if (usableFile != null && rotateWide) {
        var rotatedRenderFailed by remember(usableFile) { mutableStateOf(false) }
        BoxWithConstraints(modifier = placeholder, contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val rotatedModifier = Modifier
                .width(maxHeight)
                .height(maxWidth)
                .rotate(90f)

            if (zoomable) {
                ZoomableAsyncImage(
                    model = usableFile,
                    contentDescription = null,
                    modifier = rotatedModifier,
                    colorFilter = colorFilter,
                    contentScale = contentScale,
                    onClick = { offset ->
                        // After a clockwise quarter-turn, the original Y axis
                        // becomes screen X in reverse.
                        onTap?.invoke((screenWidthPx - offset.y).coerceIn(0f, screenWidthPx))
                    },
                )
            } else {
                AsyncImage(
                    model = usableFile,
                    contentDescription = null,
                    modifier = rotatedModifier,
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                    onError = { rotatedRenderFailed = true },
                    onSuccess = { rotatedRenderFailed = false },
                )
            }
            if (rotatedRenderFailed) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                    Text(
                        text = "Page ${index + 1} could not be displayed.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        return
    }

    when {
        usableFile != null && zoomable -> ZoomableAsyncImage(
            model = usableFile,
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter,
            contentScale = contentScale,
            // Its own, because a tap this consumes never reaches the pager.
            onClick = { offset ->
                onTap?.invoke(offset.x)
            }
        )
        usableFile != null -> {
            var renderFailed by remember(usableFile) { mutableStateOf(false) }
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = usableFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                    onError = { renderFailed = true },
                    onSuccess = { renderFailed = false },
                )
                if (renderFailed) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp),
                        ) {
                            Text(
                                text = "Page ${index + 1} could not be displayed. Try reopening this chapter.",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Reopen the chapter to try loading the image again.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Page ${index + 1} couldn't be loaded. Check your connection and try again.",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRetry) {
                    Text("Retry page ${index + 1}")
                }
            }
        }
    }
}


internal fun shouldRotateWidePage(
    width: Int,
    height: Int,
    mode: ReaderWidePageMode,
): Boolean =
    mode == ReaderWidePageMode.ROTATE_RIGHT &&
        width > 0 &&
        height > 0 &&
        width > height

private fun imageBounds(file: File): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth to options.outHeight
    }.getOrDefault(0 to 0)
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
