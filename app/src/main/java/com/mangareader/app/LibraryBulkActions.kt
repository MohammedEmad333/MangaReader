package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class BulkReadResult(
    val changed: Int,
    val skipped: Int
)

internal data class BulkDownloadResult(
    val added: Int,
    val skipped: Int
)

private suspend fun chaptersForBulk(
    context: Context,
    entry: LibraryEntry,
    source: Source?
): List<Chapter> = withContext(Dispatchers.IO) {
    val cached = ChapterCache.load(context, entry.seriesId)
        .map { source?.rehydrateChapter(it) ?: it }

    if (cached.isNotEmpty()) {
        return@withContext cached
    }
    if (source == null) {
        return@withContext emptyList()
    }

    val series = runCatching {
        source.restoreSeries(entry.seriesId, entry.title)
    }.getOrNull() ?: return@withContext emptyList()

    runCatching { source.listChapters(series) }
        .onSuccess {
            if (it.isNotEmpty()) {
                ChapterCache.save(context, entry.seriesId, it)
            }
        }
        .getOrDefault(emptyList())
}

internal suspend fun setLibrarySeriesRead(
    context: Context,
    ids: Set<String>,
    value: Boolean
): BulkReadResult = withContext(Dispatchers.IO) {
    val sources = SourceManager.listAllSources(context).associateBy { it.id }
    val entries = Library.list(context).filter { it.seriesId in ids }

    var changed = 0
    var skipped = 0

    for (entry in entries) {
        val chapters = chaptersForBulk(context, entry, sources[entry.sourceId])
        if (chapters.isEmpty()) {
            skipped++
            continue
        }

        chapters.forEach {
            val key = chapterKeyOf(entry.sourceId, it)
            ReadState.setRead(context, key, value)
            if (entry.sourceId.isAnimeExtensionSourceId() || sources[entry.sourceId]?.isAnime == true) {
                if (value) {
                    VideoPlaybackProgress.markCompleted(
                        context,
                        key,
                        VideoPlaybackProgress.duration(context, key),
                    )
                } else {
                    VideoPlaybackProgress.markIncomplete(context, key)
                }
            }
        }
        SeriesIndex.record(
            context,
            entry.sourceId,
            entry.seriesId,
            chapters
        )
        changed++
    }

    BulkReadResult(changed, skipped)
}

internal suspend fun queueLibraryDownloads(
    context: Context,
    ids: Set<String>
): BulkDownloadResult {
    val (items, skipped) = withContext(Dispatchers.IO) {
        val sources = SourceManager.listAllSources(context).associateBy { it.id }
        val entries = Library.list(context).filter { it.seriesId in ids }
        val items = ArrayList<DownloadItem>()
        var skipped = 0

        for (entry in entries) {
            val source = sources[entry.sourceId]
            if (source == null || !source.supportsDownload) {
                skipped++
                continue
            }

            val chapters = chaptersForBulk(context, entry, source)
            if (chapters.isEmpty()) {
                skipped++
                continue
            }

            chapters.forEach { chapter ->
                items.add(
                    DownloadItem(
                        sourceId = entry.sourceId,
                        chapterId = chapter.id,
                        chapterName = chapter.name,
                        seriesTitle = entry.title,
                        seriesId = entry.seriesId,
                        cover = entry.cover
                    )
                )
            }
        }

        items to skipped
    }

    val appContext = context.applicationContext
    val added = withContext(Dispatchers.IO) {
        val changed = DownloadQueue.enqueue(appContext, items)
        if (changed > 0 && DownloadQueue.paused) {
            DownloadQueue.setPaused(appContext, false)
        }
        changed
    }
    if (added > 0) {
        DownloadService.start(context)
    }

    return BulkDownloadResult(added, skipped)
}

internal suspend fun migrateLibrarySeries(
    context: Context,
    from: MigrateFrom,
    toSource: Source,
    toSeries: Series
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val readNumbers = ChapterCache.load(context, from.seriesId)
            .filter {
                it.number > Chapter.NO_NUMBER &&
                    ReadState.isRead(
                        context,
                        chapterKeyOf(from.sourceId, it)
                    )
            }
            .map { it.number }
            .toSet()

        val targetChapters = runCatching {
            toSource.listChapters(toSeries)
        }.getOrDefault(emptyList())

        if (targetChapters.isNotEmpty()) {
            ChapterCache.save(context, toSeries.id, targetChapters)

            val readKeys = targetChapters
                .filter {
                    it.number > Chapter.NO_NUMBER &&
                        it.number in readNumbers
                }
                .map { chapterKeyOf(toSource.id, it) }

            ReadState.setReadBulk(context, readKeys)
            SeriesIndex.record(
                context,
                toSource.id,
                toSeries.id,
                targetChapters
            )
        }

        val categories = Categories.categoriesFor(context, from.seriesId)
        val cover = when (val value = toSeries.cover) {
            is String -> value
            is File -> value.absolutePath
            else -> ""
        }

        Library.add(
            context,
            LibraryEntry(
                seriesId = toSeries.id,
                sourceId = toSource.id,
                title = toSeries.title,
                cover = cover,
                addedAt = System.currentTimeMillis()
            )
        )
        Categories.setCategoriesFor(
            context,
            toSeries.id,
            categories
        )

        Library.remove(context, from.seriesId)
        Categories.setCategoriesFor(
            context,
            from.seriesId,
            emptySet()
        )
        SeriesIndex.forget(context, setOf(from.seriesId))
    }.isSuccess
}
