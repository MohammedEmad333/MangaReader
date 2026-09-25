package com.mangareader.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The readable folder each downloaded chapter lives in.
 *
 * `Downloads` names a chapter directory after an MD5 of its id, which is safe,
 * collision-free and completely opaque — the handoff's own words for it are
 * "that hash is one-way". That was fine while downloads were only ever reached
 * through the app. Once they live in a folder the user picked and can open in a
 * file manager, a directory called `9f2a1c…` is useless to them.
 *
 * So a chapter gets `<Source>/<Series>/<Chapter>` instead, and this is what
 * remembers which. It has to be a stored mapping rather than something computed
 * on demand, because the reverse — folder name back to chapter id — isn't
 * recoverable from a sanitised title, and `Downloads.dirFor` is handed nothing
 * but a chapter id.
 *
 * **Three things keep this from becoming a single point of failure.**
 *
 * 1. Every chapter directory carries a [ID_MARKER] file containing its own
 *    chapter id, so the tree describes itself. [rebuild] walks the folders and
 *    restores the whole mapping from those markers — losing this file costs a
 *    directory scan, not the library.
 * 2. It lives in `filesDir`, not in the user's folder, so a file manager can't
 *    delete it by accident and moving the storage location doesn't disturb it.
 * 3. `Downloads` falls back to the old hash layout whenever there's no entry
 *    here, so a chapter this doesn't know about is still found and still reads.
 *
 * Names are assigned once and then never recomputed. A source that renames
 * itself, or a series whose title is corrected by a details fetch, must not
 * strand a folder full of pages under the old name.
 */
internal object DownloadPaths {

    private const val FILE = "download_paths.json"

    /** Written inside each chapter directory. Makes [rebuild] possible. */
    const val ID_MARKER = ".chapterid"

    /** Folder names longer than this get cut; the id suffix keeps them unique. */
    private const val MAX_NAME = 60

    // sourceId -> folder, "<sourceId>|<seriesId>" -> folder, chapterId -> full relative path
    private var sources = mutableMapOf<String, String>()
    private var series = mutableMapOf<String, String>()
    private var chapters = mutableMapOf<String, String>()
    private var loaded = false

    // ---------- lookup ----------

    /** `Source/Series/Chapter`, relative to the downloads root, or null. */
    @Synchronized
    fun pathFor(context: Context, chapterId: String): String? {
        load(context)
        return chapters[chapterId]
    }

    /** Every chapter this index has a folder for. */
    @Synchronized
    fun knownChapterIds(context: Context): Set<String> {
        load(context)
        return chapters.keys.toSet()
    }

    @Synchronized
    fun forget(context: Context, chapterId: String) {
        forgetMany(context, listOf(chapterId))
    }

    /**
     * Drops many chapter paths and persists the mapping once.
     *
     * The series screen can delete hundreds of chapters at once; rewriting the
     * whole path index once per chapter makes that operation quadratic.
     */
    @Synchronized
    fun forgetMany(context: Context, chapterIds: Collection<String>) {
        if (chapterIds.isEmpty()) return
        load(context)
        var changed = false
        chapterIds.forEach { chapterId ->
            if (chapters.remove(chapterId) != null) changed = true
        }
        if (changed) save(context)
    }

    @Synchronized
    fun clear(context: Context) {
        sources = mutableMapOf()
        series = mutableMapOf()
        chapters = mutableMapOf()
        loaded = true
        save(context)
    }

    // ---------- assignment ----------

    /**
     * Picks the folder this chapter will live in, or returns the one it already
     * has. Called before the first page is fetched, because the download needs
     * somewhere to go.
     *
     * [sourceName] is the extension's display name, resolved by the service just
     * before this. When the extension has since been uninstalled there's nothing
     * to resolve and the source id is used, which is ugly and still correct.
     */
    @Synchronized
    fun register(context: Context, item: DownloadItem, sourceName: String?): String {
        load(context)
        chapters[item.chapterId]?.let { return it }

        val sourceFolder = sources.getOrPut(item.sourceId) {
            DownloadPathNaming.unique(
                desired = clean(sourceName.orEmpty(), fallback = item.sourceId),
                ownerId = item.sourceId,
                takenBy = sources.entries.associate { (id, folder) -> folder to id }
            )
        }

        val seriesKey = item.sourceId + "|" + item.seriesId.ifBlank { item.seriesTitle }
        val seriesFolder = series.getOrPut(seriesKey) {
            // Only siblings compete for a name: two sources may each have a
            // "Solo Leveling" and they don't collide, because they're in
            // different parents.
            val siblings = series.entries
                .filter { it.key.startsWith(item.sourceId + "|") }
                .associate { (key, folder) -> folder to key }
            DownloadPathNaming.unique(
                desired = clean(item.seriesTitle, fallback = "Unknown series"),
                ownerId = seriesKey,
                takenBy = siblings
            )
        }

        val parent = "$sourceFolder/$seriesFolder"
        val siblingChapters = chapters.entries
            .filter { it.value.startsWith("$parent/") }
            .associate { (id, path) -> path.substringAfterLast('/') to id }
        val chapterFolder = DownloadPathNaming.unique(
            desired = clean(item.chapterName, fallback = "Chapter"),
            ownerId = item.chapterId,
            takenBy = siblingChapters
        )

        val path = "$parent/$chapterFolder"
        chapters[item.chapterId] = path
        save(context)
        return path
    }

    /**
     * Rebuilds the chapter map by walking the tree and reading [ID_MARKER].
     *
     * The source and series maps are rebuilt as a side effect of what's found,
     * which is why they're derived from the paths rather than read from markers
     * of their own: a folder that exists is proof of the name it was given.
     */
    @Synchronized
    fun rebuild(context: Context, downloadsRoot: File): Int {
        load(context)
        val found = scan(downloadsRoot)
        if (found > 0) save(context)
        return found
    }

    /** The walk itself, without the load/save around it, so [load] can reuse it. */
    private fun scan(downloadsRoot: File): Int {
        if (!downloadsRoot.isDirectory) return 0
        var found = 0
        runCatching {
            downloadsRoot.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
                sourceDir.listFiles()?.filter { it.isDirectory }?.forEach { seriesDir ->
                    seriesDir.listFiles()?.filter { it.isDirectory }?.forEach { chapterDir ->
                        val marker = File(chapterDir, ID_MARKER)
                        if (!marker.isFile) return@forEach
                        val id = runCatching { marker.readText().trim() }.getOrNull()
                        if (id.isNullOrBlank()) return@forEach
                        chapters[id] =
                            "${sourceDir.name}/${seriesDir.name}/${chapterDir.name}"
                        found++
                    }
                }
            }
        }
        return found
    }

    // ---------- naming ----------

    // ---------- naming ----------

    /**
     * Public test/call surface retained; naming policy lives in
     * [DownloadPathNaming].
     */
    fun clean(raw: String, fallback: String): String =
        DownloadPathNaming.clean(raw, fallback)

    // ---------- persistence ----------

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, FILE)

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val root = JSONObject(f.readText())
                sources = readMap(root.optJSONObject("sources"))
                series = readMap(root.optJSONObject("series"))
                chapters = readMap(root.optJSONObject("chapters"))
            }
            return
        }
        // No index file: either a fresh install, or someone cleared app storage
        // while the downloads sat safely in a folder outside it. The second case
        // is the one worth handling, and the markers make it recoverable — so
        // scan once per process rather than quietly reporting nothing downloaded.
        scan(File(StorageLocation.base(context), StorageLocation.DOWNLOADS))
        if (chapters.isNotEmpty()) save(context)
    }

    private fun readMap(obj: JSONObject?): MutableMap<String, String> {
        val out = mutableMapOf<String, String>()
        if (obj == null) return out
        for (key in obj.keys()) out[key] = obj.optString(key)
        return out
    }

    private fun save(context: Context) {
        runCatching {
            val root = JSONObject()
                .put("sources", writeMap(sources))
                .put("series", writeMap(series))
                .put("chapters", writeMap(chapters))
            file(context).writeText(root.toString())
        }
    }

    private fun writeMap(map: Map<String, String>): JSONObject {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return obj
    }
}
