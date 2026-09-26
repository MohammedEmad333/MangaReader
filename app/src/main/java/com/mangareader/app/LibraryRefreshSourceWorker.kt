package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Refreshes the entries belonging to one source during a library sweep.
 *
 * Kept outside the Service so source/network work is separate from lifecycle,
 * foreground-notification and resume-cursor orchestration.
 */
internal class LibraryRefreshSourceWorker(
    private val context: Context,
    private val batches: LibraryRefreshBatchBuffer,
    private val foreground: LibraryRefreshForeground,
    private val isActive: () -> Boolean,
) {
    suspend fun refresh(
        src: Source?,
        entries: List<LibraryEntry>,
        startedAt: Long,
    ) {
        val reportedCovers = CoverRepair.reported(context)

        for (entry in entries) {
            if (!isActive()) return

            if (src == null) {
                LibraryRefresh.skipped++
                LibraryRefresh.done++
                continue
            }

            LibraryRefresh.currentTitle = entry.title
            try {
                val series = src.restoreSeries(entry.seriesId, entry.title)
                    ?: throw IllegalStateException("Couldn't rebuild the series")
                val chapters = src.listChapters(series)

                if (chapters.isNotEmpty()) {
                    // Capture the previous snapshot before replacing it. An
                    // empty snapshot is a baseline, never a signal to download
                    // the whole backlog on first enable/refresh.
                    val previous = if (
                        src.supportsDownload &&
                        AutoDownloadPrefs.enabled(context, entry.sourceId, entry.seriesId)
                    ) {
                        ChapterCache.load(context, entry.seriesId)
                    } else {
                        emptyList()
                    }

                    ChapterCache.save(context, entry.seriesId, chapters)

                    if (previous.isNotEmpty() && AutoDownloadPrefs.networkAllowed(context)) {
                        val fresh = AutoDownloadPrefs.newChapters(
                            context = context,
                            previous = previous,
                            fresh = chapters,
                        )
                        if (fresh.isNotEmpty()) {
                            queueSeriesDownloads(
                                context = context,
                                series = series,
                                source = src,
                                chapters = fresh,
                            )
                        }
                    }

                    val counts = SeriesIndex.countsFor(
                        context,
                        entry.sourceId,
                        chapters,
                        sweptAt = startedAt,
                    )
                    if (counts != null) {
                        batches.putCount(entry.seriesId, counts)
                        LibraryRefresh.counted++
                    }
                }

                if (CoverRepair.needsRepair(entry, reportedCovers)) {
                    try {
                        val fresh = (src.loadDetails(series).cover as? String)
                            ?.takeIf { it.isNotBlank() && !isLoopback(it) }
                        if (fresh != null && fresh != entry.cover) {
                            batches.putCover(entry.seriesId, fresh)
                            LibraryRefresh.coversRepaired.incrementAndGet()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // Leave it on the repair list so a later sweep retries it.
                    }
                    delay(REQUEST_SPACING_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LibraryRefresh.noteFailure(
                    entry.sourceId,
                    "${entry.title} — ${e.message ?: e.javaClass.simpleName}",
                )
            }

            LibraryRefresh.done++
            foreground.notifyThrottled()
            if (batches.shouldFlush()) batches.flush()
            delay(REQUEST_SPACING_MS)
        }
    }

    private companion object {
        /** Between two requests to the same source. */
        const val REQUEST_SPACING_MS = 250L
    }
}
