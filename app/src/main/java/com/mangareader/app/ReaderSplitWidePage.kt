package com.mangareader.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.io.File

/** Avoid splitting near-square images or generating multiple full-size bitmaps. */
internal fun shouldSplitWideReaderPage(width: Int, height: Int, mode: ReaderWidePageMode): Boolean =
    mode == ReaderWidePageMode.SPLIT_SPREAD &&
        width > 0 && height > 0 &&
        width.toLong() * 10L >= height.toLong() * 14L

/** Logical first half follows the chosen manga reading direction. */
internal fun readerSplitHalfShowsRight(half: Int, rtl: Boolean): Boolean =
    if (rtl) half == 0 else half == 1

/**
 * Display one image through a half-width viewport, without changing the source
 * image or reader page index. Switching halves never writes a progress entry.
 */
@Composable
internal fun ReaderSplitWidePage(
    file: File,
    revision: ReaderImageFileRevision,
    index: Int,
    modifier: Modifier,
    colorFilter: ColorFilter?,
    rtl: Boolean,
    canRedownload: Boolean,
    stillLoading: Boolean,
    onTap: ((Float) -> Unit)?,
    onRetry: () -> Unit,
) {
    var half by remember(index, revision, rtl) { mutableIntStateOf(0) }
    var failed by remember(index, revision) { mutableStateOf(false) }
    var decodeAttempt by remember(index, revision) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val model = remember(revision, decodeAttempt) {
        ImageRequest.Builder(context).data(file)
            .memoryCachePolicy(readerImageMemoryCachePolicy(ReaderImageUsage.FULL_PAGE))
            .memoryCacheKey(revision.cacheKey("split-spread", decodeAttempt))
            .listener(
                onError = { _, _ -> failed = true },
                onSuccess = { _, _ -> failed = false },
            ).build()
    }

    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }
        val right = readerSplitHalfShowsRight(half, rtl)
        ZoomableAsyncImage(
            model = model,
            contentDescription = "Page ${index + 1}, ${if (half == 0) "first" else "second"} half",
            modifier = Modifier
                .offset(x = if (right) -maxWidth else 0.dp)
                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                .width(maxWidth * 2)
                .height(maxHeight),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onClick = { point ->
                onTap?.invoke((point.x - if (right) viewportPx else 0f).coerceIn(0f, viewportPx))
            },
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { half = 0 }, enabled = half != 0) { Text("First half") }
                TextButton(onClick = { half = 1 }, enabled = half != 1) { Text("Second half") }
            }
        }
        if (failed) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Text("Page ${index + 1} could not be displayed")
                    TextButton(onClick = { failed = false; decodeAttempt++ }) { Text("Retry display") }
                    if (canRedownload) {
                        TextButton(onClick = onRetry, enabled = !stillLoading) { Text("Re-download page") }
                    }
                }
            }
        }
    }
}
