package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Chapters kept on disk for offline reading.
 *
 * The distinction that matters is **cache vs download**. Reading a chapter writes
 * its pages under `cacheDir`, which Android is free to evict whenever it wants
 * space — that's the right home for something you looked at once. Downloading
 * writes the same pages under `filesDir`, which nothing reclaims but the user.
 *
 * Both live in a directory named after a hash of the chapter id, and a download
 * is only considered complete once every page succeeded and [markComplete] has
 * written the marker. A directory without that marker is a partial download: its
 * files are still useful, because `downloadPage` skips pages already on disk, so
 * an interrupted download resumes rather than restarting.
 */
object Downloads {

    private const val MARKER = ".complete"

    private fun root(context: Context): File =
        File(context.applicationContext.filesDir, "chapters")

    /** Permanent directory for a chapter. Not created here. */
    fun dirFor(context: Context, chapterId: String): File =
        File(root(context), hashOf(chapterId))

    /** Cache directory for a chapter — same layout, evictable location. */
    fun cacheDirFor(context: Context, chapterId: String): File =
        File(context.applicationContext.cacheDir, "pages/${hashOf(chapterId)}")

    fun isComplete(context: Context, chapterId: String): Boolean =
        File(dirFor(context, chapterId), MARKER).exists()

    fun markComplete(context: Context, chapterId: String, pageCount: Int) {
        val dir = dirFor(context, chapterId)
        if (!dir.exists()) return
        runCatching { File(dir, MARKER).writeText(pageCount.toString()) }
    }

    /** The downloaded pages in reading order, or empty if not downloaded. */
    fun pages(context: Context, chapterId: String): List<File> {
        val dir = dirFor(context, chapterId)
        val files = dir.listFiles() ?: return emptyList()
        // Page files are zero-padded indices, so name order is page order.
        return files
            .filter { it.isFile && it.name != MARKER && !it.name.endsWith(".part") }
            .sortedBy { it.name }
    }

    fun delete(context: Context, chapterId: String) {
        runCatching { dirFor(context, chapterId).deleteRecursively() }
    }

    fun deleteAll(context: Context) {
        runCatching { root(context).deleteRecursively() }
    }

    /** How many chapters are fully downloaded. */
    fun count(context: Context): Int =
        root(context).listFiles()?.count { File(it, MARKER).exists() } ?: 0

    fun sizeBytes(context: Context): Long =
        root(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /**
     * Chapter ids are `"<sourceId>:<url>"` — too long and full of characters a
     * filesystem won't take. Hashing gives a stable, safe, collision-free name.
     * (The previous code used `id.hashCode()`, a 32-bit value that collides far
     * too readily to key stored files on.)
     */
    private fun hashOf(chapterId: String): String = offlineKey(chapterId)
}

/**
 * Filesystem-safe, collision-free name for an arbitrary id. Shared by the page
 * store above and the chapter-list cache below.
 */
internal fun offlineKey(id: String): String =
    MessageDigest.getInstance("MD5")
        .digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * The last known chapter list for a series, so it can be opened without network.
 *
 * Downloading a chapter's pages isn't enough to read offline on its own: opening
 * a series calls `listChapters`, which is a network request, and that failed
 * before anything got as far as looking at the downloaded pages. This keeps a
 * copy of whatever the source last returned.
 *
 * `Chapter.handle` (the extension's own SChapter) can't be serialised, so only
 * the fields the app owns are stored. `Source.rehydrateChapter` rebuilds a usable
 * handle from the id on the way back out.
 */
object ChapterCache {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "chapterlists").apply { mkdirs() }

    private fun fileFor(context: Context, seriesId: String): File =
        File(dir(context), "${offlineKey(seriesId)}.json")

    fun save(context: Context, seriesId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        runCatching {
            val arr = JSONArray()
            chapters.forEach { ch ->
                arr.put(
                    JSONObject().apply {
                        put("id", ch.id)
                        put("name", ch.name)
                        put("date", ch.dateUploaded)
                        put("scanlator", ch.scanlator ?: "")
                    }
                )
            }
            fileFor(context, seriesId).writeText(arr.toString())
        }
    }

    fun load(context: Context, seriesId: String): List<Chapter> = runCatching {
        val file = fileFor(context, seriesId)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Chapter(
                id = o.getString("id"),
                name = o.optString("name"),
                handle = null,
                dateUploaded = o.optLong("date", 0L),
                scanlator = o.optString("scanlator").takeIf { it.isNotBlank() }
            )
        }
    }.getOrDefault(emptyList())

    fun clearAll(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }
}

/** "412 KB" / "1.6 GB" — for the storage row in More. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
