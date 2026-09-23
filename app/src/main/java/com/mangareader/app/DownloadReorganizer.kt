package com.mangareader.app

import android.content.Context
import java.io.File

/** What a reorganise run did, for the message afterwards. */
internal data class ReorganiseReport(
    val moved: Int,
    val alreadyFiled: Int,
    val unidentified: Int
)

/**
 * Files chapters downloaded before the readable tree existed into it.
 *
 * The folder name is an MD5 of the chapter id and that hash is one-way, so the
 * only way back to a name is a record that already knows it. [DownloadIndex] is
 * exactly that record, and it can recover more than it was written with: for any
 * series in the library it re-derives the mapping from [ChapterCache], so
 * chapters downloaded before the index existed are still placeable as long as
 * the series was saved.
 *
 * Whatever it still can't name is **left exactly where it is and keeps working**,
 * because `Downloads.dirFor` reads both layouts. That's the difference between
 * this being a migration and being a gamble — a chapter that can't be identified
 * loses its readable folder, not its pages.
 *
 * Not suspend, but it walks and copies directories: call it off the main thread.
 */
internal fun reorganiseDownloads(context: Context): Result<ReorganiseReport> = runCatching {
    // Every chapter folder is about to move, so anything remembered about where
    // they were is now wrong.
    Downloads.invalidateCompletion()
    // Resolved once. Classloading 26 extension APKs per chapter would dominate
    // the run, and the cache behind this returns the same list anyway.
    val sourceNames = runCatching {
        SourceManager.listAllSources(context).associate { it.id to it.name }
    }.getOrDefault(emptyMap())

    val root = Downloads.downloadsRoot(context)
    var moved = 0
    var alreadyFiled = 0

    DownloadIndex.list(context).forEach { series ->
        series.chapters.forEach { chapter ->
            // Read before register(), which assigns the new path: afterwards
            // dirFor prefers that path and there'd be no way back to the folder
            // the pages are actually in.
            val from = Downloads.dirFor(context, chapter.chapterId)
            if (!from.isDirectory) return@forEach

            val item = DownloadItem(
                sourceId = series.sourceId,
                chapterId = chapter.chapterId,
                chapterName = chapter.name,
                seriesTitle = series.title,
                seriesId = series.seriesId,
                cover = series.cover
            )
            val to = File(
                root,
                DownloadPaths.register(context, item, sourceNames[series.sourceId])
            )
            if (from.absolutePath == to.absolutePath) {
                alreadyFiled++
                return@forEach
            }

            to.parentFile?.mkdirs()
            val ok = if (from.renameTo(to)) true else runCatching {
                // Across mount points rename fails by returning false rather
                // than throwing, so the copy is the real path on an SD card.
                if (to.exists()) to.deleteRecursively()
                from.copyRecursively(to, overwrite = true)
                from.deleteRecursively()
                true
            }.getOrDefault(false)

            if (ok) {
                // The marker the old layout never had. Writing it here is what
                // makes these chapters recoverable by a scan from now on.
                runCatching { File(to, DownloadPaths.ID_MARKER).writeText(chapter.chapterId) }
                moved++
            }
            // A failed move leaves the index pointing at a folder that isn't
            // there, which dirFor resolves by falling back to the one that is.
            // Wrong-but-harmless, and fixed by running this again.
        }
    }

    val legacy = StorageLocation.legacyRoots(context)
    val unidentified = legacy.sumOf { dir ->
        dir.listFiles()?.count { it.isDirectory } ?: 0
    }
    // delete() only succeeds on an empty directory, so this tidies away the old
    // folder when everything moved and leaves it alone when something didn't.
    legacy.forEach { runCatching { it.delete() } }

    ReorganiseReport(moved, alreadyFiled, unidentified)
}
