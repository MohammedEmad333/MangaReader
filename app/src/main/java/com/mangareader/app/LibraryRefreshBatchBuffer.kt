package com.mangareader.app

import android.content.Context

/**
 * Buffers refresh results so a sweep does not rewrite the whole index/library
 * once per series.
 *
 * Counts and covers are intentionally independent batches. A flush must always
 * give each store a chance to write even when the other batch is empty.
 */
internal class LibraryRefreshBatchBuffer(
    private val context: Context,
    private val flushEvery: Int,
) {
    private val pendingCounts = HashMap<String, SeriesCounts>()
    private val countsLock = Any()

    private val pendingCovers = HashMap<String, String>()
    private val coversLock = Any()

    fun putCount(seriesId: String, counts: SeriesCounts) {
        synchronized(countsLock) {
            pendingCounts[seriesId] = counts
        }
    }

    fun putCover(seriesId: String, cover: String) {
        synchronized(coversLock) {
            pendingCovers[seriesId] = cover
        }
    }

    fun shouldFlush(): Boolean =
        synchronized(countsLock) { pendingCounts.size >= flushEvery } ||
            synchronized(coversLock) { pendingCovers.size >= flushEvery }

    fun flush() {
        val counts = synchronized(countsLock) {
            if (pendingCounts.isEmpty()) null
            else HashMap(pendingCounts).also { pendingCounts.clear() }
        }
        if (counts != null) {
            runCatching { SeriesIndex.recordAll(context, counts) }
        }

        val covers = synchronized(coversLock) {
            if (pendingCovers.isEmpty()) null
            else HashMap(pendingCovers).also { pendingCovers.clear() }
        }
        if (covers != null) {
            runCatching {
                Library.setCovers(context, covers)
                CoverRepair.clear(context, covers.keys)
            }
        }
    }
}
