package com.mangareader.app

/**
 * The pager's currentPage can change during a fling, including passing the
 * last page without stopping there. Persist only its settledPage so resume
 * position and chapter completion reflect where the reader actually landed.
 *
 * The strip has no PagerState; keep its existing visible-page tracking.
 */
internal fun readerPageToPersist(
    mode: ReaderMode,
    visiblePage: Int,
    settledPagerItem: Int,
    headRows: Int,
    pageCount: Int,
): Int {
    val target = if (mode == ReaderMode.LONG_STRIP) {
        visiblePage
    } else {
        settledPagerItem - headRows
    }
    return safeReaderPageIndex(target, pageCount)
}
