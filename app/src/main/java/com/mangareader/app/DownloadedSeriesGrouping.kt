package com.mangareader.app

/** Never aggregate downloads from different extensions under the same series ID. */
internal fun groupDownloadedRecords(
    records: List<DownloadIndexRecord>,
    sizeOf: (String) -> Long,
): List<DownloadedSeries> = records
    .groupBy { it.sourceId to it.seriesId }
    .map { (identity, sameSeries) ->
        val first = sameSeries.first()
        DownloadedSeries(
            sourceId = identity.first,
            seriesId = identity.second,
            title = first.title.ifBlank { "Unknown series" },
            cover = sameSeries.firstOrNull { it.cover.isNotBlank() }?.cover ?: "",
            chapters = sameSeries.map {
                DownloadedChapter(it.chapterId, it.chapterName)
            },
            sizeBytes = sameSeries.sumOf { sizeOf(it.chapterId) },
        )
    }
    .sortedByDescending { it.sizeBytes }

/** Legacy series-only covers remain untouched; new covers are source-qualified. */
internal fun downloadCoverCacheKey(sourceId: String, seriesId: String): String =
    offlineKey("${sourceId.length}:$sourceId${seriesId.length}:$seriesId")
