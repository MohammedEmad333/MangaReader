package com.mangareader.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Executes one queued chapter download.
 *
 * This class owns source lookup, handle recovery, page transfer and the
 * per-drain chapter-list cache. [DownloadService] remains responsible for queue
 * lifecycle, pause/skip/cancel semantics and foreground-service state.
 */
internal class DownloadItemRunner(
    private val context: Context,
    private val notifyProgress: () -> Unit,
) {
    private val chaptersBySeries = mutableMapOf<String, Map<String, Chapter>>()

    fun clearSession() {
        chaptersBySeries.clear()
    }

    suspend fun download(item: DownloadItem): String? {
        if (Downloads.isComplete(context, item.chapterId)) {
            DownloadIndex.record(context, item)
            return null
        }

        val src = SourceManager.listAllSources(context)
            .firstOrNull { it.id == item.sourceId }
            ?: throw IllegalStateException(
                "\"${item.seriesTitle}\" — source is no longer installed"
            )

        DownloadPaths.register(context, item, src.name)

        val rebuilt = src.rehydrateChapter(
            Chapter(id = item.chapterId, name = item.chapterName, handle = null)
        )

        try {
            fetchPages(src, item, rebuilt)
        } catch (e: CancellationException) {
            throw e
        } catch (rebuiltFailure: Throwable) {
            if (!blamesTheHandle(rebuiltFailure)) throw rebuiltFailure
            val genuine = genuineChapter(src, item)
            if (genuine == null || genuine.handle == null) throw rebuiltFailure
            Log.w(
                TAG,
                "Rebuilt handle rejected for ${item.chapterId}; " +
                    "retrying with the source's own chapter",
                rebuiltFailure
            )
            fetchPages(src, item, genuine)
        }

        return if (Downloads.isComplete(context, item.chapterId)) {
            DownloadIndex.record(context, item)
            null
        } else {
            "Finished without marking the chapter complete"
        }
    }

    private fun blamesTheHandle(t: Throwable): Boolean =
        t !is ChapterDownloadException || t.totalPages == 0

    private suspend fun fetchPages(src: Source, item: DownloadItem, chapter: Chapter) {
        src.loadPagesProgressively(chapter, persist = true) { partial ->
            DownloadQueue.setProgress(
                item.chapterId,
                ready = partial.count { it != null },
                total = partial.size
            )
            notifyProgress()
        }
    }

    private suspend fun genuineChapter(src: Source, item: DownloadItem): Chapter? {
        if (item.seriesId.isBlank()) return null
        chaptersBySeries[item.seriesId]?.let { return it[item.chapterId] }

        val fetched = try {
            val series = src.restoreSeries(item.seriesId, item.seriesTitle)
            if (series == null) emptyList() else src.listChapters(series)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Could not re-list chapters for ${item.seriesId}", e)
            emptyList()
        }

        return fetched.associateBy { it.id }
            .also { chaptersBySeries[item.seriesId] = it }[item.chapterId]
    }

    private companion object {
        const val TAG = "DownloadItemRunner"
    }
}
