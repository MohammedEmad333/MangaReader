package com.mangareader.app

import android.content.Context

internal data class StoredSeriesLoad(
    val source: Source,
    val cachedChapters: List<Chapter>,
    val series: Series,
    val chapters: List<Chapter>
)

internal data class DownloadedSeriesLoad(
    val source: Source,
    val cachedChapters: List<Chapter>,
    val resolved: Pair<Series, List<Chapter>>?
)

internal suspend fun findInstalledSource(
    context: Context,
    sourceId: String
): Source = SourceManager.listAllSources(context)
    .firstOrNull { it.id == sourceId }
    ?: throw IllegalStateException("That source is no longer installed")

private suspend fun loadChaptersWithFallback(
    context: Context,
    source: Source,
    series: Series
): List<Chapter> =
    runCatching { source.listChapters(series) }
        .onSuccess { ChapterCache.save(context, series.id, it) }
        .getOrElse { error ->
            val cached = ChapterCache.load(context, series.id)
                .map { source.rehydrateChapter(it) }

            if (cached.isNotEmpty()) {
                cached
            } else {
                throw IllegalStateException(
                    "Couldn't load the chapter list — ${error.message}"
                )
            }
        }

internal suspend fun loadStoredLibrarySeries(
    context: Context,
    entry: LibraryEntry
): StoredSeriesLoad {
    val source = findInstalledSource(context, entry.sourceId)
    val cached = ChapterCache.load(context, entry.seriesId)
        .map { source.rehydrateChapter(it) }

    val fetched = runCatching {
        source.restoreSeries(entry.seriesId, entry.title)
    }.getOrElse {
        throw IllegalStateException(
            "Couldn't load series details — ${it.message}"
        )
    } ?: throw IllegalStateException(
        "That series is no longer available from its source"
    )

    val series = fetched.copy(
        title = fetched.title.ifBlank { entry.title },
        cover = fetched.cover ?: entry.cover.ifBlank { null }
    )
    val chapters = loadChaptersWithFallback(
        context,
        source,
        series
    )

    return StoredSeriesLoad(
        source = source,
        cachedChapters = cached,
        series = series,
        chapters = chapters
    )
}

internal suspend fun loadDownloadedSeries(
    context: Context,
    entry: DownloadedSeries
): DownloadedSeriesLoad {
    val source = findInstalledSource(context, entry.sourceId)
    val cached = ChapterCache.load(context, entry.seriesId)
        .map { source.rehydrateChapter(it) }

    val fetched = runCatching {
        source.restoreSeries(entry.seriesId, entry.title)
    }.getOrNull()

    val resolved = if (fetched == null) {
        null
    } else {
        val series = fetched.copy(
            title = fetched.title.ifBlank { entry.title },
            cover = fetched.cover ?: entry.cover.ifBlank { null }
        )
        series to loadChaptersWithFallback(
            context,
            source,
            series
        )
    }

    if (resolved == null && cached.isEmpty()) {
        throw IllegalStateException(
            "No chapter list cached for this series — open it once online"
        )
    }

    return DownloadedSeriesLoad(
        source = source,
        cachedChapters = cached,
        resolved = resolved
    )
}

internal suspend fun loadHistoryResumeTarget(
    context: Context,
    entry: HistoryEntry
): ResumeTarget {
    val source = SourceManager.listAllSources(context)
        .firstOrNull { it.id == entry.sourceId }
        ?: throw IllegalStateException("That source no longer exists")

    val fetched = runCatching {
        source.restoreSeries(entry.seriesId, entry.title)
    }.getOrElse {
        throw IllegalStateException(
            "Couldn't load series details — ${it.message}"
        )
    } ?: throw IllegalStateException(
        "That series is no longer in the library"
    )

    val series = fetched.copy(
        title = fetched.title.ifBlank { entry.title },
        cover = fetched.cover ?: entry.coverPath.ifBlank { null }
    )
    val chapters = loadChaptersWithFallback(
        context,
        source,
        series
    )
    val index = chapters.indexOfFirst {
        chapterKeyOf(entry.sourceId, it) == entry.chapterKey
    }

    if (index < 0) {
        throw IllegalStateException("That chapter is gone")
    }

    return ResumeTarget(
        source,
        series,
        chapters,
        index
    )
}
