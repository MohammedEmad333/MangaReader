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
internal data class RecoveredDownloadIdentity(
    val chapterId: String,
    val chapterName: String,
    val sourceId: String,
    val seriesId: String,
    val seriesTitle: String,
)

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

    /**
     * Reconstructs source/series identity from the readable path index.
     *
     * This is specifically for installs that have complete chapter folders and
     * download_paths.json but predate downloads_index.json. The path index
     * already stores sourceId -> folder and sourceId|seriesId -> folder, so
     * throwing that identity away and relying only on Library/ChapterCache made
     * perfectly valid downloads disappear from the Downloads tab.
     */
    @Synchronized
    fun recoverIdentity(context: Context, chapterId: String): RecoveredDownloadIdentity? {
        load(context)
        val path = chapters[chapterId] ?: return null
        val parts = path.split('/')
        if (parts.size < 3) return null
        val sourceFolder = parts[0]
        val seriesFolder = parts[1]
        val chapterFolder = parts.drop(2).joinToString("/")

        val sourceId = sources.entries
            .firstOrNull { it.value == sourceFolder }
            ?.key
            ?: return null
        val prefix = "$sourceId|"
        val seriesId = series.entries
            .firstOrNull { (key, folder) -> key.startsWith(prefix) && folder == seriesFolder }
            ?.key
            ?.removePrefix(prefix)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return RecoveredDownloadIdentity(
            chapterId = chapterId,
            chapterName = chapterFolder,
            sourceId = sourceId,
            seriesId = seriesId,
            seriesTitle = seriesFolder,
        )
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

    private data class RecoveryCandidate(
        val sourceId: String,
        val seriesId: String,
        val seriesTitle: String,
        val chapterId: String,
        val chapterName: String,
    )

    /**
     * Repairs readable downloads created before [.chapterid] existed using the
     * old download index. This also covers downloaded series no longer in the
     * Library, as long as downloads_index.json survived.
     */
    @Synchronized
    fun recoverUnmarkedFromIndex(
        context: Context,
        downloadsRoot: File,
        records: List<DownloadIndexRecord>,
    ): Int = recoverUnmarked(
        context = context,
        downloadsRoot = downloadsRoot,
        candidates = records.map { record ->
            RecoveryCandidate(
                sourceId = record.sourceId,
                seriesId = record.seriesId,
                seriesTitle = record.title,
                chapterId = record.chapterId,
                chapterName = record.chapterName,
            )
        },
    )

    /**
     * Library fallback for folders whose old download-index record is gone.
     * The chapter cache supplies ids while the readable tree supplies the path.
     */
    @Synchronized
    fun recoverUnmarkedFromLibrary(context: Context, downloadsRoot: File): Int {
        val candidates = buildList {
            Library.list(context).forEach { entry ->
                ChapterCache.load(context, entry.seriesId).forEach { chapter ->
                    add(
                        RecoveryCandidate(
                            sourceId = entry.sourceId,
                            seriesId = entry.seriesId,
                            seriesTitle = entry.title,
                            chapterId = chapter.id,
                            chapterName = chapter.name,
                        )
                    )
                }
            }
        }
        return recoverUnmarked(context, downloadsRoot, candidates)
    }

    /**
     * Reconnects only unambiguous Source/Series/Chapter folder matches. A match
     * gets written to download_paths.json and receives .chapterid immediately,
     * so this expensive repair is self-healing and future scans are direct.
     */
    private fun recoverUnmarked(
        context: Context,
        downloadsRoot: File,
        candidates: List<RecoveryCandidate>,
    ): Int {
        load(context)
        if (!downloadsRoot.isDirectory || candidates.isEmpty()) return 0

        val sourceDirs = downloadsRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (sourceDirs.isEmpty()) return 0
        val sourceNames = SourceNames.all(context)
        var recovered = 0

        candidates
            .filter { it.sourceId.isNotBlank() && it.seriesId.isNotBlank() && it.chapterId.isNotBlank() }
            .groupBy { it.sourceId to it.seriesId }
            .forEach { (seriesKey, seriesCandidates) ->
                val first = seriesCandidates.first()
                val sourceDesired = buildSet {
                    add(clean(first.sourceId, first.sourceId))
                    sourceNames[first.sourceId]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(clean(it, first.sourceId)) }
                }
                val sourceDir = uniqueMatchingDir(sourceDirs, sourceDesired) ?: return@forEach
                val seriesDir = uniqueMatchingDir(
                    sourceDir.listFiles()?.filter { it.isDirectory }.orEmpty(),
                    setOf(clean(first.seriesTitle, "Unknown series")),
                ) ?: return@forEach
                val chapterDirs = seriesDir.listFiles()?.filter { it.isDirectory }.orEmpty()

                seriesCandidates.forEach chapterLoop@ { candidate ->
                    if (chapters.containsKey(candidate.chapterId)) return@chapterLoop
                    val chapterDir = uniqueMatchingDir(
                        chapterDirs,
                        setOf(clean(candidate.chapterName, "Chapter")),
                    ) ?: return@chapterLoop
                    if (!File(chapterDir, ".complete").isFile) return@chapterLoop

                    chapters[candidate.chapterId] =
                        "${sourceDir.name}/${seriesDir.name}/${chapterDir.name}"
                    sources[candidate.sourceId] = sourceDir.name
                    series["${seriesKey.first}|${seriesKey.second}"] = seriesDir.name
                    runCatching { File(chapterDir, ID_MARKER).writeText(candidate.chapterId) }
                    recovered++
                }
            }

        if (recovered > 0) save(context)
        return recovered
    }

    private fun uniqueMatchingDir(
        dirs: List<File>,
        desiredNames: Set<String>,
    ): File? {
        val matches = dirs.filter { dir ->
            desiredNames.any { desired ->
                dir.name == desired || stableSuffixedName(dir.name, desired)
            }
        }
        return matches.singleOrNull()
    }

    private fun stableSuffixedName(actual: String, desired: String): Boolean {
        if (!actual.startsWith("$desired (") || !actual.endsWith(")")) return false
        val suffix = actual.removePrefix("$desired (").removeSuffix(")")
        return suffix.length == 6 && suffix.all { it in '0'..'9' || it in 'a'..'f' }
    }
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
