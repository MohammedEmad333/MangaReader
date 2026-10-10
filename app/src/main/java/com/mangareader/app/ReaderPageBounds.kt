package com.mangareader.app

/**
 * Reader pages can be empty while a chapter is still loading.
 * Never pass a negative upper bound to coerceIn.
 */
internal fun safeReaderPageIndex(savedIndex: Int, pageCount: Int): Int =
    savedIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
