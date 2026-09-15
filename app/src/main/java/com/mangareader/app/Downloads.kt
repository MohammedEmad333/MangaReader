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
internal object DownloadAliases {
    private const val FILE = "download_id_aliases.json"
    private var aliases = mutableMapOf<String, String>()
    private var loaded = false

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, FILE)

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (!f.exists()) return
        runCatching {
            val json = JSONObject(f.readText())
            val restored = mutableMapOf<String, String>()
            for (key in json.keys()) {
                val value = json.optString(key)
                if (key.isNotBlank() && value.isNotBlank() && key != value) {
                    restored[key] = value
                }
            }
            aliases = restored
        }
    }

    @Synchronized
    fun resolve(context: Context, chapterId: String): String {
        load(context)
        var current = chapterId
        val seen = mutableSetOf<String>()
        while (seen.add(current)) {
            val next = aliases[current] ?: break
            current = next
        }
        return current
    }

    @Synchronized
    fun put(context: Context, currentId: String, downloadedId: String): Boolean {
        load(context)
        if (currentId.isBlank() || downloadedId.isBlank()) return false
        val target = resolve(context, downloadedId)
        if (currentId == target || aliases[currentId] == target) return false
        aliases[currentId] = target
        save(context)
        return true
    }

    /** Removes every alias that points at the same stored chapter. */
    @Synchronized
    fun forget(context: Context, chapterId: String) {
        load(context)
        val target = resolve(context, chapterId)
        val removed = aliases.entries.removeAll { (key, value) ->
            key == chapterId || key == target || resolve(context, value) == target
        }
        if (removed) save(context)
    }

    @Synchronized
    fun clear(context: Context) {
        aliases.clear()
        loaded = true
        runCatching { file(context).delete() }
    }

    private fun save(context: Context) {
        runCatching {
            val json = JSONObject()
            aliases.forEach { (key, value) -> json.put(key, value) }
            file(context).writeText(json.toString())
        }
    }
}

/** Chapters kept on disk for offline reading. */
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

internal fun offlineKey(id: String): String =
    MessageDigest.getInstance("MD5")
        .digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }

/** The last known chapter list for a series, so it can be opened without network. */
object ChapterCache {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "chapterlists").apply { mkdirs() }

    private fun fileFor(context: Context, seriesId: String): File =
        File(dir(context), "${offlineKey(seriesId)}.json")

    /** A conservative identity used only to reconnect downloads after an id change. */
    private fun normalizedName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * Reconnects downloads whose source changed only the chapter id/URL.
     *
     * DownloadIndex still knows the old downloaded id and chapter name. We only
     * create an alias when the normalized name is unique on both sides, so two
     * same-named releases/scanlations are never guessed between. This runs only
     * for a series the download index says has a completed download.
     */
    private fun reconcileDownloadAliases(
        context: Context,
        seriesId: String,
        chapters: List<Chapter>
    ) {
        if (chapters.isEmpty()) return
        if (seriesId !in DownloadIndex.seriesIds(context)) return

        val downloaded = DownloadIndex.list(context)
            .firstOrNull { it.seriesId == seriesId }
            ?.chapters
            .orEmpty()
        if (downloaded.isEmpty()) return

        val currentByName = chapters
            .groupBy { normalizedName(it.name) }
            .filterValues { it.size == 1 }
        val oldByName = downloaded
            .groupBy { normalizedName(it.name) }
            .filterValues { it.size == 1 }

        var changed = false
        for ((name, currentMatches) in currentByName) {
            if (name.isBlank()) continue
            val old = oldByName[name]?.singleOrNull() ?: continue
            val current = currentMatches.single()
            if (old.chapterId == current.id) continue
            if (!Downloads.isComplete(context, old.chapterId)) continue
            if (Downloads.isComplete(context, current.id)) continue
            changed = DownloadAliases.put(context, current.id, old.chapterId) || changed
        }

        // The current ids may already have been memoised as not downloaded above.
        if (changed) Downloads.invalidateCompletion()
    }

    fun save(context: Context, seriesId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        runCatching { reconcileDownloadAliases(context, seriesId, chapters) }
        runCatching {
            val arr = JSONArray()
            chapters.forEach { ch ->
                arr.put(
                    JSONObject().apply {
                        put("id", ch.id)
                        put("name", ch.name)
                        put("date", ch.dateUploaded)
                        put("scanlator", ch.scanlator ?: "")
                        put("number", ch.number.toDouble())
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
                scanlator = o.optString("scanlator").takeIf { it.isNotBlank() },
                number = ChapterRecognition.parse(
                    seriesTitle = "",
                    chapterName = o.optString("name"),
                    fromSource = o.optDouble("number", Chapter.NO_NUMBER.toDouble()).toFloat()
                )
            )
        }
    }.getOrDefault(emptyList())

    fun clearAll(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
