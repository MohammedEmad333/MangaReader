package com.mangareader.app

/** A loading or empty chapter must not be marked read. */
internal fun isReaderChapterCompleted(pageIndex: Int, loadedPageCount: Int): Boolean =
    loadedPageCount > 0 && pageIndex >= loadedPageCount - 1
