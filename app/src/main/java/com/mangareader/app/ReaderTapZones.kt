package com.mangareader.app

/** Pure tap-zone routing, independent of zoom gestures and physical layout. */
internal enum class ReaderTapAction { PREVIOUS, NEXT, CONTROLS }

/**
 * The left and right edges turn pages; logical direction mirrors for RTL.
 * Non-finite or out-of-range taps do not unexpectedly advance the chapter.
 */
internal fun readerTapAction(
    fraction: Float,
    enabled: Boolean,
    size: ReaderTapZoneSize,
    rtl: Boolean,
): ReaderTapAction {
    if (!enabled || !fraction.isFinite() || fraction !in 0f..1f) return ReaderTapAction.CONTROLS
    val edge = size.edgeFraction
    return when {
        fraction < edge -> if (rtl) ReaderTapAction.NEXT else ReaderTapAction.PREVIOUS
        fraction > 1f - edge -> if (rtl) ReaderTapAction.PREVIOUS else ReaderTapAction.NEXT
        else -> ReaderTapAction.CONTROLS
    }
}
