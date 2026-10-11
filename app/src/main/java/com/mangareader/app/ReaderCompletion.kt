package com.mangareader.app

/** A streaming chapter is complete only after loading finishes at its final page. */
internal fun isReaderChapterCompleted(
    pageIndex: Int,
    loadedPageCount: Int,
    stillLoading: Boolean = false,
): Boolean =
    !stillLoading && loadedPageCount > 0 && pageIndex == loadedPageCount - 1
