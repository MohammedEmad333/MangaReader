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
 * writes the same pages under [StorageLocation.base], which nothing reclaims
 * but the user — internal storage by default, or whatever folder they picked.
 *
 * **[root] is the only thing in this file that knows where that is.** Everything
 * below it — `dirFor`, `pages`, `count`, `sizeOf`, `deleteAll` — is derived, and
 * a page is still a `java.io.File` wherever it lives, which is what let the
 * storage-location setting land without touching the page pipeline. Keep it that
 * way: anything that resolves a download path itself is a second place to fix.
 *
 * Both live in a directory named after a hash of the chapter id, and a download
 * is only considered complete once every page succeeded and [markComplete] has
 * written the marker. A directory without that marker is a partial download: its
 * files are still useful, because `downloadPage` skips pages already on disk, so
 * an interrupted download resumes rather than restarting.
 */
object Downloads {

    private const val MARKER = ".complete"

    /**
     * The readable tree: `<base>/downloads/<Source>/<Series>/<Chapter>`.
     *
     * Not private only because [reorganiseDownloads] has to build target paths
     * against it. Nothing else should be resolving download paths itself.
     */
    fun downloadsRoot(context: Context): File =
        File(StorageLocation.base(context), StorageLocation.DOWNLOADS)

    /**
     * Where a chapter's pages are.
     *
     * Two layouts, and the order between them is the whole safety story. A
     * chapter [DownloadPaths] knows about uses its readable folder. Anything
     * else falls back to the flat `<md5>` directory, which is where every
     * chapter downloaded before the tree existed still lives — and still reads,
     * indefinitely, with nothing needing to be migrated for the app to keep
     * working. A half-finished reorganisation is therefore a valid state rather
     * than a broken one, because both halves are found.
     *
     * The `exists()` check on the assigned path matters: the path is registered
     * *before* the first page is fetched, so between enqueueing a chapter and
     * writing it the folder is a promise rather than a fact. Returning it
     * anyway would make a re-download of something already in the old layout
     * fetch every page again.
     */
    fun dirFor(context: Context, chapterId: String): File {
        val assigned = DownloadPaths.pathFor(context, chapterId)
        if (assigned != null) {
            val dir = File(downloadsRoot(context), assigned)
            if (dir.exists()) return dir
            legacyDir(context, chapterId)?.let { return it }
            return dir
        }
        return legacyDir(context, chapterId)
            ?: File(downloadsRoot(context), hashOf(chapterId))
    }

    /** The old flat directory, if one is actually there. */
    private fun legacyDir(context: Context, chapterId: String): File? {
        val name = hashOf(chapterId)
        StorageLocation.legacyRoots(context).forEach { root ->
            val dir = File(root, name)
            if (dir.exists()) return dir
        }
        return null
    }

    /** Cache directory for a chapter — same idea, evictable location, still flat. */
    fun cacheDirFor(context: Context, chapterId: String): File =
        File(context.applicationContext.cacheDir, "pages/${hashOf(chapterId)}")

    // Answered from memory after the first look.
    //
    // This is called once per chapter row, and rows are re-composed every time
    // they scroll back into view, so it runs constantly while a long chapter
    // list moves. Each call is several filesystem stats — dirFor() probes the
    // tree path and then every legacy root — against external storage, where
    // every stat crosses the FUSE layer. A 171-chapter list scrolls past
    // hundreds of them a second, on the main thread.
    //
    // Every path that changes a chapter's files invalidates this, and it's
    // deliberately a whole-map drop rather than per-key removal: deleteAll and a
    // reorganise move everything, and being wrong about a download's existence
    // is worse than re-statting.
    private val completion = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Sizes, remembered for the same reason.
     *
     * [sizeOf] walks every page file in a chapter folder, and the Downloads tab
     * asks for one per downloaded chapter before it can draw a single row. A
     * finished chapter's size never changes, so the walk only has to happen
     * once.
     */
    private val sizes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Drops everything remembered about one chapter's files. */
    private fun forget(chapterId: String) {
        completion.remove(chapterId)
        sizes.remove(chapterId)
        DownloadIndex.invalidate()
    }

    /** Drops the memos. For anything that moves or removes files in bulk. */
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
        // Makes the tree self-describing. The folder name is for the user; this
        // is what lets DownloadPaths.rebuild recover the mapping by scanning,
        // which is the difference between losing an index and losing a library.
        runCatching { File(dir, DownloadPaths.ID_MARKER).writeText(chapterId) }
    }

    /** The downloaded pages in reading order, or empty if not downloaded. */
    fun pages(context: Context, chapterId: String): List<File> {
        val dir = dirFor(context, chapterId)
        val files = dir.listFiles() ?: return emptyList()
        // Page files are zero-padded indices, so name order is page order.
        // Dotfiles are the two markers; `.part` is a page still being written.
        return files
            .filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".part") }
            .sortedBy { it.name }
    }

    fun delete(context: Context, chapterId: String) {
        val dir = dirFor(context, chapterId)
        forget(chapterId)
        runCatching { dir.deleteRecursively() }
        pruneEmptyParents(context, dir)
        DownloadPaths.forget(context, chapterId)
    }

    /**
     * Removes the series and source folders once their last chapter goes.
     *
     * An empty `Solo Leveling` folder sitting in the user's storage reads as a
     * download that's still there. Stops at the downloads root and after two
     * levels, so it can never walk up into the folder they picked.
     */
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
    }

    /** Both layouts, deduplicated — they can resolve to the same folder. */
    private fun roots(context: Context): List<File> =
        (listOf(downloadsRoot(context)) + StorageLocation.legacyRoots(context))
            .distinctBy { it.absolutePath }

    /**
     * Every directory holding a finished chapter.
     *
     * Depth 3 is exactly `downloads/<Source>/<Series>/<Chapter>`; the legacy
     * root's chapters sit at depth 1 and are found by the same walk. Capping it
     * keeps the walk out of the page files themselves, which is the difference
     * between reading a few thousand directory entries and a few hundred
     * thousand.
     */
    private fun completeDirs(context: Context): Sequence<File> =
        roots(context).asSequence().flatMap { root ->
            if (!root.isDirectory) emptySequence()
            else root.walkTopDown()
                .maxDepth(3)
                .filter { it.isDirectory && File(it, MARKER).exists() }
        }

    /** How many chapters are fully downloaded. */
    fun count(context: Context): Int =
        runCatching { completeDirs(context).count() }.getOrDefault(0)

    /** On-disk size of one chapter, for the per-series totals in the Downloads tab. */
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
