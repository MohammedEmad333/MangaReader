package com.mangareader.app

/** Retry only missing pages, using the same viewport and direction priority. */
internal fun readerFailedPageRetryOrder(
    loaded: List<Boolean>,
    currentPage: Int,
    visiblePages: List<Int>,
    direction: Int,
): List<Int> =
    prioritizedPageOrder(
        pageCount = loaded.size,
        visiblePage = currentPage,
        alreadyStarted = loaded.indices.filterTo(mutableSetOf()) { loaded[it] },
        visiblePages = visiblePages,
        direction = direction,
    )
