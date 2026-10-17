package com.mangareader.app

import java.io.File

/** Only offer retry for a missing page in the chapter currently being read. */
internal fun canRetryReaderPage(
    pages: List<File?>,
    loading: Boolean,
    currentChapterIndex: Int?,
    requestedChapterIndex: Int,
    pageIndex: Int,
): Boolean =
    !loading &&
        currentChapterIndex == requestedChapterIndex &&
        pageIndex in pages.indices &&
        pages[pageIndex] == null

/**
 * The source replays cached pages before retrying a failed one.
 * Preserve already displayed pages while those cached results stream in.
 */
internal fun mergeRetriedReaderPages(
    previous: List<File?>,
    update: List<File?>,
): List<File?> {
    require(previous.size == update.size) { "Page count changed; reopen the chapter" }
    return update.mapIndexed { index, file -> file ?: previous[index] }
}
