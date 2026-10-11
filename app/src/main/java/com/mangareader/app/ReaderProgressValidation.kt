package com.mangareader.app

/** Ignore progress events before pages exist or when their index is invalid. */
internal fun isValidReaderPageIndex(pageIndex: Int, loadedPageCount: Int): Boolean =
    pageIndex >= 0 && pageIndex < loadedPageCount
