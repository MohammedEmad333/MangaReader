package com.mangareader.app

import android.content.Context

internal object DownloadIndexBuilder {
    private const val RECOVERY_DONE = "download_index_recovered"

    fun build(context: Context): List<DownloadedSeries> {
        val live = DownloadIndexStorage.read(context)
            .filter { Downloads.isComplete(context, it.chapterId) }
        val known = live.map { it.chapterId }.toMutableSet()

        val recovered = mutableListOf<DownloadIndexRecord>()
        val scanned = needsRecovery(context, known)

        if (scanned) {
            for (entry in Library.list(context)) {
                for (chapter in ChapterCache.load(context, entry.seriesId)) {
                    if (chapter.id in known) continue
                    if (!Downloads.isComplete(context, chapter.id)) continue

                    known.add(chapter.id)
                    recovered += DownloadIndexRecord(
                        chapterId = chapter.id,
                        chapterName = chapter.name,
                        sourceId = entry.sourceId,
                        seriesId = entry.seriesId,
                        title = entry.title,
                        cover = entry.cover,
                    )
                }
            }
        }

        if (scanned) {
            runCatching {
                prefs(context)
                    .edit()
                    .putBoolean(RECOVERY_DONE, true)
                    .apply()
            }
        }

        return (live + recovered)
            .groupBy { it.seriesId }
            .map { (seriesId, records) ->
                val first = records.first()
                DownloadedSeries(
                    sourceId = first.sourceId,
                    seriesId = seriesId,
                    title = first.title.ifBlank { "Unknown series" },
                    cover = records.firstOrNull { it.cover.isNotBlank() }?.cover ?: "",
                    chapters = records.map {
                        DownloadedChapter(it.chapterId, it.chapterName)
                    },
                    sizeBytes = records.sumOf {
                        Downloads.sizeOf(context, it.chapterId)
                    },
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    fun clearRecovery(context: Context) {
        runCatching {
            prefs(context)
                .edit()
                .remove(RECOVERY_DONE)
                .apply()
        }
    }

    private fun needsRecovery(
        context: Context,
        known: Set<String>,
    ): Boolean {
        if (prefs(context).getBoolean(RECOVERY_DONE, false)) return false
        if (DownloadPaths.knownChapterIds(context).any { it !in known }) return true

        return StorageLocation.legacyRoots(context).any { root ->
            root.listFiles()?.any { it.isDirectory } == true
        }
    }
}
