package com.mangareader.app

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_STRIP_ZOOM = 3f
private const val DOUBLE_TAP_ZOOM = 2f
private const val ZOOM_ANIM_MS = 200

/**
 * Adds pinch, pan, tap, and animated double-tap zoom to the long-strip reader.
 *
 * This stays separate from [ReaderScreen] because it is a self-contained
 * gesture/state system. Keeping it here makes the screen responsible for reader
 * orchestration while this helper owns the long-strip interaction details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberReaderStripZoomModifier(
    baseModifier: Modifier,
    chapterIndex: Int,
    listState: LazyListState,
    scope: CoroutineScope,
    onToggleControls: () -> Unit,
): Modifier {
    var stripScale by remember(chapterIndex) { mutableFloatStateOf(1f) }
    var stripPanX by remember(chapterIndex) { mutableFloatStateOf(0f) }
    var stripPanY by remember(chapterIndex) { mutableFloatStateOf(0f) }
    var zoomAnim by remember(chapterIndex) { mutableStateOf<Job?>(null) }

    val viewportWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val viewportHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    fun clampX(value: Float): Float =
        value.coerceIn(
            -viewportWidthPx * (stripScale - 1f) / 2f,
            viewportWidthPx * (stripScale - 1f) / 2f,
        )

    fun clampY(value: Float): Float =
        value.coerceIn(
            -viewportHeightPx * (stripScale - 1f) / 2f,
            viewportHeightPx * (stripScale - 1f) / 2f,
        )

    val stripTransform = rememberTransformableState { zoomChange, panChange, _ ->
        zoomAnim?.cancel()
        stripScale = (stripScale * zoomChange).coerceIn(1f, MAX_STRIP_ZOOM)
        stripPanX = clampX(stripPanX + panChange.x)

        val atListEdge = !listState.canScrollBackward || !listState.canScrollForward
        if (atListEdge) {
            stripPanY = clampY(stripPanY + panChange.y)
        } else {
            scope.launch { listState.scrollBy(-panChange.y) }
        }
    }

    return baseModifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onToggleControls() },
                onDoubleTap = { offset ->
                    val toScale: Float
                    val toX: Float
                    val toY: Float
                    if (stripScale > 1f) {
                        toScale = 1f
                        toX = 0f
                        toY = 0f
                    } else {
                        toScale = DOUBLE_TAP_ZOOM
                        toX = (viewportWidthPx / 2f - offset.x) * (DOUBLE_TAP_ZOOM - 1f)
                        toY = (viewportHeightPx / 2f - offset.y) * (DOUBLE_TAP_ZOOM - 1f)
                    }

                    val fromScale = stripScale
                    val fromX = stripPanX
                    val fromY = stripPanY
                    zoomAnim?.cancel()
                    zoomAnim = scope.launch {
                        animate(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = tween(ZOOM_ANIM_MS, easing = LinearOutSlowInEasing),
                        ) { fraction, _ ->
                            stripScale = fromScale + (toScale - fromScale) * fraction
                            stripPanX = clampX(fromX + (toX - fromX) * fraction)
                            stripPanY = clampY(fromY + (toY - fromY) * fraction)
                        }
                    }
                },
            )
        }
        .clipToBounds()
        .graphicsLayer {
            scaleX = stripScale
            scaleY = stripScale
            translationX = stripPanX
            translationY = stripPanY
        }
        .transformable(
            state = stripTransform,
            canPan = { pan ->
                stripScale > 1f && abs(pan.x) > abs(pan.y) * 0.4f
            },
        )
}
