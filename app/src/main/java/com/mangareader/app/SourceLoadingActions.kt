package com.mangareader.app

import android.content.Context

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

internal suspend fun loadSeriesChapters(
    context: Context,
    source: Source,
    series: Series
): List<Chapter> =
    source.listChapters(series).also {
        ChapterCache.save(context, series.id, it)
    }

internal suspend fun loadAndHealSeriesDetails(
    context: Context,
    source: Source,
    series: Series
): Series? {
    val enriched = runCatching {
        source.loadDetails(series)
    }.getOrNull()

    if (enriched != null) {
        Library.healCover(
            context,
            series.id,
            enriched.cover
        )
    }

    return enriched
}
