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

    fun isComplete(context: Context, chapterId: String): Boolean =
        completion.getOrPut(chapterId) {
            File(dirFor(context, chapterId), MARKER).exists()
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
            .filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".part") }
            .sortedBy { it.name }
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
