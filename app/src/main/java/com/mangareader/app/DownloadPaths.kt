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

    @Synchronized
    fun forget(context: Context, chapterId: String) {
        load(context)
        if (chapters.remove(chapterId) != null) save(context)
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
            unique(
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
            unique(
                desired = clean(item.seriesTitle, fallback = "Unknown series"),
                ownerId = seriesKey,
                takenBy = siblings
            )
        }

        val parent = "$sourceFolder/$seriesFolder"
        val siblingChapters = chapters.entries
            .filter { it.value.startsWith("$parent/") }
            .associate { (id, path) -> path.substringAfterLast('/') to id }
        val chapterFolder = unique(
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

    /**
     * A title turned into something every filesystem will accept.
     *
     * The illegal set is Windows', not Linux's, deliberately: these folders are
     * meant to be opened on a PC, and `?` in a name that Android took happily is
     * a folder that can't be copied off the phone. Trailing dots and spaces go
     * for the same reason, and the reserved device names get a prefix because a
     * chapter honestly called "Con" would otherwise be uncreatable.
     */
    fun clean(raw: String, fallback: String): String {
        var name = raw.replace(ILLEGAL, " ")
        name = name.replace(WHITESPACE, " ").trim()
        name = name.trimEnd('.', ' ')
        if (name.length > MAX_NAME) name = name.take(MAX_NAME).trimEnd('.', ' ')
        if (name.uppercase() in RESERVED) name = "_$name"
        return name.ifBlank { fallback }
    }

    /**
     * Keeps the desired name if it's free or already this owner's, otherwise
     * suffixes it with a hash of the owner's id.
     *
     * Deterministic rather than a counter: a counter needs the whole sibling set
     * to be correct at the moment it's assigned, and would hand two different
     * series the same folder if a rebuild ever ran them in a different order.
     */
    private fun unique(desired: String, ownerId: String, takenBy: Map<String, String>): String {
        val holder = takenBy[desired]
        if (holder == null || holder == ownerId) return desired
        return desired + " (" + offlineKey(ownerId).take(6) + ")"
    }

    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F]""")
    private val WHITESPACE = Regex("""\s+""")
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

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
