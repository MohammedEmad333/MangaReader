package com.mangareader.app

import android.content.Context

internal suspend fun findInstalledSource(
    context: Context,
    sourceId: String
): Source = SourceManager.listAllSources(context)
    .firstOrNull { it.id == sourceId }
    ?: throw IllegalStateException("That source is no longer installed")

internal fun loadCachedChapters(
    context: Context,
    source: Source,
    seriesId: String
): List<Chapter> = ChapterCache.load(context, seriesId)
    .map { source.rehydrateChapter(it) }

private suspend fun loadChaptersWithFallback(
    context: Context,
    source: Source,
    series: Series
): List<Chapter> =
    runCatching { source.listChapters(series) }
        .onSuccess { ChapterCache.save(context, series.id, it) }
        .getOrElse { error ->
            val cached = loadCachedChapters(
                context,
                source,
                series.id
            )
            if (cached.isNotEmpty()) {
                cached
            } else {
                throw IllegalStateException(
                    "Couldn't load the chapter list — ${error.message}"
                )
            }
        }

internal suspend fun resolveLibrarySeries(
    context: Context,
    source: Source,
    entry: LibraryEntry
): Pair<Series, List<Chapter>> {
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

    return series to loadChaptersWithFallback(
        context,
        source,
        series
    )
}

internal suspend fun resolveDownloadedSeries(
    context: Context,
    source: Source,
    entry: DownloadedSeries
): Pair<Series, List<Chapter>>? {
    val fetched = runCatching {
        source.restoreSeries(entry.seriesId, entry.title)
    }.getOrNull() ?: return null

    val series = fetched.copy(
        title = fetched.title.ifBlank { entry.title },
        cover = fetched.cover ?: entry.cover.ifBlank { null }
    )

    return series to loadChaptersWithFallback(
        context,
        source,
        series
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
