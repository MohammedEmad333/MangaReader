package com.mangareader.app

import java.io.File

/**
 * Check cached page presence and reject unmistakable CDN error bodies or
 * incomplete image headers. Unknown binary image formats remain supported.
 */
internal fun isUsableReaderPage(file: File?): Boolean {
    if (file == null || !file.isFile || file.length() == 0L) return false
    return try {
        val prefix = file.inputStream().use { it.readPagePrefix() }
        !isObviouslyInvalidPage(prefix) && !isTruncatedKnownImage(prefix, file.length())
    } catch (_: java.io.IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

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
        !isUsableReaderPage(pages[pageIndex])

/**
 * The source replays cached pages before retrying a failed one.
 * Preserve already displayed pages while those cached results stream in.
 */
internal fun mergeRetriedReaderPages(
    previous: List<File?>,
    update: List<File?>,
): List<File?> {
    require(previous.size == update.size) { "Page count changed; reopen the chapter" }
    return update.mapIndexed { index, file ->
        file?.takeIf(::isUsableReaderPage)
            ?: previous[index]?.takeIf(::isUsableReaderPage)
    }
}
