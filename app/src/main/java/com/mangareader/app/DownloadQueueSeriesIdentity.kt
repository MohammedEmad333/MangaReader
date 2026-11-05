package com.mangareader.app

/**
 * Queue actions must be scoped to the extension that owns the series.
 * Series IDs are not guaranteed unique between extension sources.
 */
internal fun queuedChapterIdsForSeries(
    items: List<DownloadItem>,
    sourceId: String,
    seriesId: String,
): Set<String> {
    if (sourceId.isBlank() || seriesId.isBlank()) return emptySet()
    return items.asSequence()
        .filter { it.sourceId == sourceId && it.seriesId == seriesId }
        .map { it.chapterId }
        .filter { it.isNotBlank() }
        .toSet()
}
