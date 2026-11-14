package com.mangareader.app

import kotlin.math.roundToInt

/**
 * Page-picker thumbnails should never decode full-resolution manga pages.
 * The thumbnail is 72 x 96 dp on screen, and its decode request is capped
 * at 512 pixels per edge even on unusually high-density displays.
 */
internal fun readerPreviewDecodeSize(density: Float): Pair<Int, Int> {
    val scale = if (density.isFinite() && density > 0f) density else 1f
    return (72f * scale).coerceIn(1f, 512f).roundToInt() to
        (96f * scale).coerceIn(1f, 512f).roundToInt()
}
