package com.mangareader.app

/** Tablet-only, opt-in spreads, never used by vertical strip or phone layouts. */
internal fun readerTwoPageTabletEnabled(
    enabled: Boolean,
    mode: ReaderMode,
    widthDp: Int,
    heightDp: Int,
): Boolean = enabled && mode != ReaderMode.LONG_STRIP &&
    widthDp >= 840 && widthDp > heightDp && heightDp > 0

internal fun readerSpreadCount(pageCount: Int): Int =
    if (pageCount <= 0) 0 else pageCount / 2 + pageCount % 2

internal fun readerSpreadForPage(page: Int): Int = page.coerceAtLeast(0) / 2

internal fun readerSpreadFirstPage(spread: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return (spread.coerceAtLeast(0).toLong() * 2).coerceAtMost((pageCount - 1).toLong()).toInt()
}

internal fun readerSpreadLastPage(spread: Int, pageCount: Int): Int {
    if (pageCount <= 0 || spread < 0) return 0
    return (spread.toLong() * 2 + 1).coerceAtMost((pageCount - 1).toLong()).toInt()
}
