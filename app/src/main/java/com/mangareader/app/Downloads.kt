package com.mangareader.app

import android.content.Context
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
    private fun hashOf(chapterId: String): String =
        MessageDigest.getInstance("MD5")
            .digest(chapterId.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** "412 KB" / "1.6 GB" — for the storage row in More. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
