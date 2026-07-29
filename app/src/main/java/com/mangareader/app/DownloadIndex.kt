package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One downloaded chapter, as shown under its series. */
data class DownloadedChapter(
    val chapterId: String,
    val name: String
)

/** A series with at least one fully downloaded chapter. */
data class DownloadedSeries(
    val sourceId: String,
    val seriesId: String,
    val title: String,
    val cover: String,
    val chapters: List<DownloadedChapter>,
    val sizeBytes: Long
)

/**
 * Which series each downloaded chapter belongs to.
 *
 * `Downloads` stores pages in a directory named after an MD5 of the chapter id,
 * and that hash is one-way — given a folder full of downloads there is no way to
 * work out what series they came from, or even what their chapter ids were. Fine
 * while downloads were only ever reached *through* a series screen; not enough to
 * build a screen that lists them.
 *
 * So the service writes a record here as each chapter completes. Two properties
 * matter:
 *
 * - **It is a cache, not the truth.** The pages on disk are the truth. [list]
 *   drops any record whose chapter is no longer complete, which is what keeps it
 *   correct across the delete paths that don't know this file exists — "Delete
 *   downloads" on a series screen, "Delete all" in More, or the user clearing app
 *   storage.
 * - **It backfills from the library.** Chapters downloaded before this index
 *   existed have no record. For anything saved to the library the mapping can be
 *   recovered anyway: `ChapterCache` is keyed by the same hash of the series id,
 *   so its stored chapter lists can be re-checked against what's on disk.
 *   Downloads of series that were never saved to the library stay invisible here
 *   — nothing short of re-downloading them can recover that.
 */
object DownloadIndex {

    private const val FILE = "downloads_index.json"

    private data class Record(
        val chapterId: String,
        val chapterName: String,
        val sourceId: String,
        val seriesId: String,
        val title: String,
        val cover: String
    )

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, FILE)

    // ---------- writing ----------

    /**
     * Files a finished chapter under its series.
     *
     * A blank [DownloadItem.seriesId] is skipped rather than stored: without it
     * the entry couldn't be grouped or reopened, so a half-record would only put
     * an un-openable row on the screen. That case is limited to queue entries
     * written by 0.20, before the field existed.
     */
    @Synchronized
    fun record(context: Context, item: DownloadItem) {
        invalidate()
        if (item.seriesId.isBlank()) return
        val current = read(context).associateBy { it.chapterId }.toMutableMap()
        current[item.chapterId] = Record(
            chapterId = item.chapterId,
            chapterName = item.chapterName,
            sourceId = item.sourceId,
            seriesId = item.seriesId,
            title = item.seriesTitle,
            cover = item.cover
        )
        write(context, current.values.toList())
    }

    // ---------- reading ----------

    /** Downloaded series, largest first. Self-healing against what's on disk. */
    /**
     * The last result of [list].
     *
     * Everything below is disk work — reading records, sizing folders, and
     * sometimes scanning for orphans — and none of it changes unless a download
     * finishes or something is deleted, both of which clear this. Without it the
     * whole lot ran again every time the tab was opened.
     */
    @Volatile
    private var cached: List<DownloadedSeries>? = null

    fun invalidate() {
        cached = null
    }

    fun list(context: Context): List<DownloadedSeries> {
        cached?.let { return it }
        return build(context).also { cached = it }
    }

    private fun build(context: Context): List<DownloadedSeries> {
        val live = read(context).filter { Downloads.isComplete(context, it.chapterId) }
        val known = live.map { it.chapterId }.toMutableSet()

        val recovered = mutableListOf<Record>()
        if (needsRecovery(context, known)) for (entry in Library.list(context)) {
            for (chapter in ChapterCache.load(context, entry.seriesId)) {
                if (chapter.id in known) continue
                if (!Downloads.isComplete(context, chapter.id)) continue
                known.add(chapter.id)
                recovered += Record(
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    sourceId = entry.sourceId,
                    seriesId = entry.seriesId,
                    title = entry.title,
                    cover = entry.cover
                )
            }
        }

        return (live + recovered)
            .groupBy { it.seriesId }
            .map { (seriesId, records) ->
                val first = records.first()
                DownloadedSeries(
                    sourceId = first.sourceId,
                    seriesId = seriesId,
                    title = first.title.ifBlank { "Unknown series" },
                    // Any one record's cover will do; they all came from the same
                    // series, and a blank one just falls back to the placeholder.
                    cover = records.firstOrNull { it.cover.isNotBlank() }?.cover ?: "",
                    chapters = records.map { DownloadedChapter(it.chapterId, it.chapterName) },
                    sizeBytes = records.sumOf { Downloads.sizeOf(context, it.chapterId) }
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    /**
     * Whether the scan below is worth running at all.
     *
     * That scan reads the chapter cache off disk for every series in the
     * library. At forty series it was free; at several thousand it's thousands
     * of file reads and JSON parses before the Downloads tab can draw, every
     * single time it's opened. It's a repair for an index that has lost track of
     * folders that are still on disk, and normally there's nothing to repair.
     *
     * So: ask the path index whether it knows of any downloaded chapter this
     * one doesn't, and check whether the old flat layout — whose folder names
     * are hashes and can't be mapped back to chapter ids, making a scan the only
     * way to find them — has anything in it. If neither, there is nothing the
     * scan could turn up.
     */
    private fun needsRecovery(context: Context, known: Set<String>): Boolean {
        if (DownloadPaths.knownChapterIds(context).any { it !in known }) return true
        return StorageLocation.legacyRoots(context).any { root ->
            root.listFiles()?.any { it.isDirectory } == true
        }
    }

    // ---------- deleting ----------

    /** Deletes every downloaded chapter of a series, pages included. */
    @Synchronized
    fun deleteSeries(context: Context, series: DownloadedSeries) {
        invalidate()
        series.chapters.forEach { Downloads.delete(context, it.chapterId) }
        val gone = series.chapters.map { it.chapterId }.toSet()
        write(context, read(context).filterNot { it.chapterId in gone })
    }

    // ---------- storage ----------

    private fun read(context: Context): List<Record> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Record(
                chapterId = o.getString("chapterId"),
                chapterName = o.optString("chapterName"),
                sourceId = o.optString("sourceId"),
                seriesId = o.optString("seriesId"),
                title = o.optString("title"),
                cover = o.optString("cover")
            )
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, records: List<Record>) {
        runCatching {
            val arr = JSONArray()
            records.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("chapterId", r.chapterId)
                        put("chapterName", r.chapterName)
                        put("sourceId", r.sourceId)
                        put("seriesId", r.seriesId)
                        put("title", r.title)
                        put("cover", r.cover)
                    }
                )
            }
            file(context).writeText(arr.toString())
        }
    }
}
