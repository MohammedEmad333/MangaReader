package com.mangareader.app

/** Display informational hints without cancelling an active page download. */
internal enum class ReaderPageWaitLevel {
    LOADING,
    SLOW,
    VERY_SLOW,
}

internal const val READER_PAGE_SLOW_LOAD_DELAY_MS = 12_000L
internal const val READER_PAGE_LONG_LOAD_DELAY_MS = 30_000L

/** Pure policy shared by the reader UI and its JVM regression tests. */
internal fun readerPageWaitLevel(elapsedMs: Long): ReaderPageWaitLevel = when {
    elapsedMs >= READER_PAGE_LONG_LOAD_DELAY_MS -> ReaderPageWaitLevel.VERY_SLOW
    elapsedMs >= READER_PAGE_SLOW_LOAD_DELAY_MS -> ReaderPageWaitLevel.SLOW
    else -> ReaderPageWaitLevel.LOADING
}
