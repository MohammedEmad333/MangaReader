package com.mangareader.app

/** A streaming chapter is complete only after loading finishes at its final page. */
internal fun isReaderChapterCompleted(
    pageIndex: Int,
    loadedPageCount: Int,
    stillLoading: Boolean = false,
): Boolean =
    !stillLoading && loadedPageCount > 0 && pageIndex == loadedPageCount - 1

/**
 * Completing a chapter requires the final page to be reached AND every
 * advertised page to be a usable local image. A failed stream may leave null
 * slots while still ending at the final page; never mark that chapter read.
 *
 * Inspect files only for the final-page event, not on every scroll update.
 */
internal fun isReaderChapterFullyLoadedAtEnd(
    pageIndex: Int,
    pages: List<java.io.File?>,
    stillLoading: Boolean = false,
): Boolean =
    isReaderChapterCompleted(pageIndex, pages.size, stillLoading) &&
        pages.all(::isUsableReaderPage)

/** File validation is only needed when an unloaded chapter has reached its end. */
internal fun shouldValidateReaderCompletion(
    pageIndex: Int,
    pageCount: Int,
    stillLoading: Boolean,
): Boolean =
    isReaderChapterCompleted(pageIndex, pageCount, stillLoading)
