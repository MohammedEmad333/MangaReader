package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Persistent aliases from the chapter id a source returns now to the id that
 * was used when the chapter was downloaded.
 *
 * Extension updates sometimes change a chapter URL/id while leaving the chapter
 * itself unchanged. The download index can still list the old id, but the series
 * screen asks Downloads about the new id. Keeping that relationship here lets
 * every existing Downloads API keep using the current id while resolving the
 * already-downloaded directory underneath it.
 */
object Downloads {

    private const val MARKER = ".complete"

    fun downloadsRoot(context: Context): File =
        File(StorageLocation.base(context), StorageLocation.DOWNLOADS)

    /**
     * Where a chapter's pages are.
     *
     * The public/current chapter id is first resolved through DownloadAliases.
     * This is deliberately below every caller: badges, filters, the reader,
     * deletion, and size calculations must all agree on the same folder.
     */
    fun dirFor(context: Context, chapterId: String): File {
        val storedId = DownloadAliases.resolve(context, chapterId)
        val assigned = DownloadPaths.pathFor(context, storedId)
        if (assigned != null) {
            val dir = File(downloadsRoot(context), assigned)
            if (dir.exists()) return dir
            legacyDir(context, storedId)?.let { return it }
            return dir
        }
        return legacyDir(context, storedId)
            ?: File(downloadsRoot(context), hashOf(storedId))
    }

    private fun legacyDir(context: Context, chapterId: String): File? {
        val name = hashOf(chapterId)
        StorageLocation.legacyRoots(context).forEach { root ->
            val dir = File(root, name)
            if (dir.exists()) return dir
        }
        return null
    }

    fun cacheDirFor(context: Context, chapterId: String): File =
        File(context.applicationContext.cacheDir, "pages/${hashOf(chapterId)}")

    private val completion = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val sizes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun forget(chapterId: String) {
        completion.remove(chapterId)
        sizes.remove(chapterId)
        DownloadIndex.invalidate()
    }

    fun invalidateCompletion() {
        completion.clear()
        sizes.clear()
        DownloadIndex.invalidate()
    }

    fun isComplete(context: Context, chapterId: String): Boolean {
        // A positive cached result can become stale when Android storage cleanup,
        // SD-card removal or another process removes a page. Revalidate positive
        // results before treating the downloaded chapter as safe to open offline.
        if (completion[chapterId] == false) {
            val dir = dirFor(context, chapterId)
            if (!File(dir, MARKER).isFile) return false
        }
        return inspectOfflineChapter(dirFor(context, chapterId)).isComplete.also {
            completion[chapterId] = it
        }
    }

    fun markComplete(context: Context, chapterId: String, pageCount: Int) {
        val dir = dirFor(context, chapterId)
        if (!dir.exists()) return
        forget(chapterId)
        runCatching { File(dir, MARKER).writeText(pageCount.toString()) }
        runCatching { File(dir, DownloadPaths.ID_MARKER).writeText(chapterId) }
    }

    fun pages(context: Context, chapterId: String): List<File> {
        val dir = dirFor(context, chapterId)
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter(::isStoredChapterPage)
            .sortedWith(compareBy<File> { it.name.substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.name })
    }

    fun delete(context: Context, chapterId: String) {
        val storedId = DownloadAliases.resolve(context, chapterId)
        val dir = dirFor(context, chapterId)
        forget(chapterId)
        if (storedId != chapterId) forget(storedId)
        runCatching { dir.deleteRecursively() }
        pruneEmptyParents(context, dir)
        DownloadPaths.forget(context, storedId)
        DownloadAliases.forget(context, chapterId)
    }

    /**
     * Deletes a chapter batch without rewriting the path/alias indexes for every
     * item. Resolve every location first, then mutate the indexes once after the
     * filesystem work is complete.
     */
    fun deleteMany(context: Context, chapterIds: Collection<String>) {
        val ids = chapterIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        if (ids.size == 1) {
            delete(context, ids.first())
            return
        }

        data class Target(
            val publicId: String,
            val storedId: String,
            val dir: File,
        )

        val targets = ids.map { chapterId ->
            Target(
                publicId = chapterId,
                storedId = DownloadAliases.resolve(context, chapterId),
                dir = dirFor(context, chapterId),
            )
        }

        // A bulk deletion invalidates the whole inexpensive in-memory cache once,
        // rather than invalidating the same DownloadIndex for every chapter.
        invalidateCompletion()

        targets
            .distinctBy { it.dir.absolutePath }
            .forEach { target ->
                runCatching { target.dir.deleteRecursively() }
            }

        // Prune after all children are gone so a series/source parent can become
        // empty once and be removed without repeated index writes.
        targets
            .distinctBy { it.dir.parentFile?.absolutePath ?: it.dir.absolutePath }
            .forEach { target -> pruneEmptyParents(context, target.dir) }

        DownloadPaths.forgetMany(context, targets.map { it.storedId })
        DownloadAliases.forgetMany(context, targets.map { it.publicId })
    }

    private fun pruneEmptyParents(context: Context, from: File) {
        runCatching {
            val stop = downloadsRoot(context).absolutePath
            var parent = from.parentFile
            var levels = 0
            while (parent != null && levels < 2 && parent.absolutePath != stop) {
                if (parent.listFiles()?.isEmpty() != true) return
                parent.delete()
                parent = parent.parentFile
                levels++
            }
        }
    }

    fun deleteAll(context: Context) {
        invalidateCompletion()
        roots(context).forEach { runCatching { it.deleteRecursively() } }
        DownloadPaths.clear(context)
        DownloadAliases.clear(context)
    }

    private fun roots(context: Context): List<File> =
        (listOf(downloadsRoot(context)) + StorageLocation.legacyRoots(context))
            .distinctBy { it.absolutePath }

    private fun completeDirs(context: Context): Sequence<File> =
        roots(context).asSequence().flatMap { root ->
            if (!root.isDirectory) emptySequence()
            else root.walkTopDown()
                .maxDepth(3)
                .filter { it.isDirectory && File(it, MARKER).exists() }
        }

    fun count(context: Context): Int =
        runCatching { completeDirs(context).count() }.getOrDefault(0)

    fun sizeOf(context: Context, chapterId: String): Long =
        sizes.getOrPut(chapterId) {
            runCatching {
                dirFor(context, chapterId).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }

    fun sizeBytes(context: Context): Long =
        roots(context).sumOf { root ->
            runCatching { root.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
                .getOrDefault(0L)
        }

    private fun hashOf(chapterId: String): String = offlineKey(chapterId)
}


/**
 * Downloaded page directories may contain completion markers, temporary files,
 * and index metadata. Only indexed image files should reach the reader.
 * The downloader writes extensionless zero-padded numeric names; named image
 * extensions are accepted for older imports.
 */
internal fun isStoredChapterPage(file: File): Boolean {
    if (!file.isFile || file.length() <= 0L) return false
    val name = file.name
    if (name.startsWith(".") || name.endsWith(".part", ignoreCase = true)) return false
    val stem = name.substringBeforeLast('.')
    val extension = name.substringAfterLast('.', "")
    val numeric = (if (extension.isEmpty()) name else stem)
    if (numeric.length !in 1..8 || numeric.any { !it.isDigit() }) return false
    return extension.isEmpty() || extension.lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heif", "heic", "bmp")
}

/** Read-only verification of downloaded chapter files; never removes user data. */
internal data class OfflineChapterIntegrity(
    val expectedPages: Int?,
    val availablePages: Int,
    val missingIndices: List<Int>,
    val invalidIndices: List<Int>,
    val isComplete: Boolean,
)

internal fun inspectOfflineChapter(dir: File): OfflineChapterIntegrity {
    val marker = File(dir, ".complete")
    val expected = marker.takeIf { it.isFile }?.let {
        runCatching { it.readText().trim().toIntOrNull() }.getOrNull()
    }?.takeIf { it in 1..2000 }
    val files = dir.listFiles().orEmpty().filter(::isStoredChapterPage)
    val indexed = files.mapNotNull { file ->
        file.name.substringBefore('.').toIntOrNull()?.let { it to file }
    }.groupBy({ it.first }, { it.second })
    val missing = if (expected == null) emptyList() else
        (0 until expected).filter { indexed[it].isNullOrEmpty() }
    val invalid = indexed.entries.filter { (number, entries) ->
        (expected != null && number !in 0 until expected) ||
            entries.size != 1 || entries.any { !isUsableReaderPage(it) }
    }.map { it.key }.sorted()
    return OfflineChapterIntegrity(
        expectedPages = expected,
        availablePages = indexed.values.sumOf { it.size },
        missingIndices = missing,
        invalidIndices = invalid,
        isComplete = expected != null && missing.isEmpty() && invalid.isEmpty() &&
            indexed.size == expected,
    )
}
