package com.mangareader.app

import android.content.Context

internal object DownloadIndexBuilder {
    private const val RECOVERY_DONE = "download_index_recovered"

    fun build(context: Context): List<DownloadedSeries> {
        val live = DownloadIndexStorage.read(context)
            .filter { Downloads.isComplete(context, it.chapterId) }
        val known = live.map { it.chapterId }.toMutableSet()

        val recovered = mutableListOf<DownloadIndexRecord>()

        // Older readable downloads can predate the per-chapter .chapterid marker.
        // Repair those first while Library + ChapterCache still provide a safe,
        // unambiguous identity, then the ordinary path-index recovery below sees
        // them exactly like a modern download.
        DownloadPaths.recoverUnmarkedFromLibrary(
            context,
            Downloads.downloadsRoot(context),
        )
        Downloads.invalidateCompletion()

        // First recover directly from download_paths.json. This path does not
        // require the series to still be in the library and covers downloads
        // created after readable paths existed but before downloads_index.json
        // was populated reliably.
        DownloadPaths.knownChapterIds(context).forEach { chapterId ->
            if (chapterId in known) return@forEach
            if (!Downloads.isComplete(context, chapterId)) return@forEach
            val identity = DownloadPaths.recoverIdentity(context, chapterId)
                ?: return@forEach
            known.add(chapterId)
            recovered += DownloadIndexRecord(
                chapterId = identity.chapterId,
                chapterName = identity.chapterName,
                sourceId = identity.sourceId,
                seriesId = identity.seriesId,
                title = identity.seriesTitle,
                cover = "",
            )
        }

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

        // Persist recovered records so the expensive repair is one-time and
        // later launches do not depend on the library/cache still being present.
        if (recovered.isNotEmpty()) {
            DownloadIndexStorage.write(context, live + recovered)
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
        // New evidence on disk always wins over an old "recovery done" stamp.
        // A previous build may have stamped recovery before path metadata or a
        // chapter cache was available.
        if (DownloadPaths.knownChapterIds(context).any { it !in known }) return true
        if (prefs(context).getBoolean(RECOVERY_DONE, false)) return false

        return StorageLocation.legacyRoots(context).any { root ->
            root.listFiles()?.any { it.isDirectory } == true
        }
    }
}
