package com.mangareader.app

import android.content.Context
import java.io.File

internal fun queueSeriesDownloads(
    context: Context,
    series: Series?,
    source: Source,
    chapters: List<Chapter>
): Int {
    val seriesTitle = series?.title.orEmpty()
    val seriesId = series?.id.orEmpty()
    val cover = when (val value = series?.cover) {
        is File -> value.absolutePath
        is String -> value
        else -> ""
    }

    val added = DownloadQueue.enqueue(
        context,
        chapters.map { chapter ->
            DownloadItem(
                sourceId = source.id,
                chapterId = chapter.id,
                chapterName = chapter.name,
                seriesTitle = seriesTitle,
                seriesId = seriesId,
                cover = cover
            )
        }
    )

    if (added > 0) {
        if (DownloadQueue.paused) {
            DownloadQueue.setPaused(context, false)
        }
        DownloadService.start(context)
    }

    return added
}

internal fun cancelSeriesDownloadsAction(
    context: Context,
    seriesId: String
): Boolean {
    val active = DownloadQueue.activeId
    val removed = DownloadQueue.removeSeries(context, seriesId)
    if (removed.isEmpty()) return false

    if (active != null && active in removed) {
        DownloadService.start(
            context,
            DownloadService.ACTION_SKIP,
            active
        )
    }

    return true
}
