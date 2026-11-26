package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CancellationException

internal suspend fun loadSourcePage(
    source: Source,
    query: String,
    mode: BrowseMode,
    page: Int
): SeriesPage = when {
    query.isNotBlank() -> source.searchSeries(query, page)
    mode == BrowseMode.LATEST -> source.latestSeries(page)
    mode == BrowseMode.FILTER -> source.filteredSeries(page)
    else -> source.browseSeries(page)
}

internal suspend fun loadSeriesContentAndCache(
    context: Context,
    source: Source,
    series: Series
): SeriesLoadResult {
    val loaded = source.loadSeriesContent(series)
    ChapterCache.save(context, source.id, series.id, loaded.chapters)
    Library.healCover(context, source.id, series.id, loaded.series.cover)
    return loaded
}


internal fun resolveSourceConfig(
    context: Context,
    config: SourceConfig
): Source? = SourceManager.build(context, config)

internal suspend fun scanChapterVideos(
    source: Source,
    chapter: Chapter
): VideoScan = try {
    source.scanVideos(chapter)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    VideoScan(
        emptyList(),
        note = sourceFailureMessage(error, "The scan failed")
    )
}
