package com.mangareader.app

import android.content.Context

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
        // Keep the cover with the offline data as soon as the first chapter of
        // the series finishes. ensure() is idempotent, so later chapters reuse
        // the same file instead of fetching the image again.
        val localCover = DownloadCovers.ensure(context, item)
        val current = DownloadIndexStorage.read(context).associateBy { it.chapterId }.toMutableMap()
        current[item.chapterId] = DownloadIndexRecord(
            chapterId = item.chapterId,
            chapterName = item.chapterName,
            sourceId = item.sourceId,
            seriesId = item.seriesId,
            title = item.seriesTitle,
            cover = localCover
        )
        DownloadIndexStorage.write(context, current.values.toList())
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

    /**
     * The answer to [seriesIds], cached separately.
     *
     * Separate because it can be filled by the cheap path *or* by the expensive
     * one. [seriesIds] populates it without sizing or recovering; [list]
     * overwrites it with the fuller answer as a side effect, so opening the
     * Downloads tab once upgrades every later badge lookup for the rest of the
     * process. Both are dropped together by [invalidate].
     */
    @Volatile
    private var cachedIds: Set<String>? = null

    fun invalidate() {
        cached = null
        cachedIds = null
    }

    fun list(context: Context): List<DownloadedSeries> {
        cached?.let { return it }
        return build(context).also {
            cached = it
            cachedIds = it.mapTo(mutableSetOf()) { series -> series.seriesId }
        }
    }

    /**
     * Which series have at least one complete download — and nothing else.
     *
     * The library screen wants a set of ids to decide which covers get a
     * download badge. It used to get them from [list], which meant that drawing
     * the library paid for a size walk per downloaded chapter, a size-descending
     * sort, and — through [needsRecovery] — a `ChapterCache` read for every
     * entry in the library. On a 3571-entry library that measured **19.9
     * seconds of a cold start** (`SESSION_HANDOFF_0.71_RESULT.md` §2). It was the
     * whole of the "app takes some time to open" board item: turning the badge
     * off took the open from about thirty seconds to three.
     *
     * So this is the same question asked without the Downloads tab's answer
     * attached. What it drops:
     *
     * - **Sizes.** `Downloads.sizeOf` walks every page file in a chapter folder.
     *   Nothing on the library screen shows a size.
     * - **The sort.** Ordering by size to build a `Set` is wasted twice over.
     * - **Recovery.** The O(library) scan for downloads made before this index
     *   existed. Its cost belongs to the Downloads tab, which is where the user
     *   would notice something missing. See the note below.
     *
     * **What it keeps, and this is the part not to optimise away:** the
     * `isComplete` check per record. `Downloads.delete` → `forget()` calls
     * [invalidate] but **never prunes the record** from `downloads_index.json` —
     * only [deleteSeries] does, and only `DownloadQueueScreen` calls that. The
     * series-screen delete paths in `MainActivity` are the un-pruning kind. So a
     * version of this that trusted the records without checking disk would leave
     * a download badge on a series whose downloads you had just deleted, and it
     * would stay until something else rebuilt the file. The self-healing filter
     * is load-bearing for an in-app path, not only for out-of-band deletes.
     *
     * **The one behaviour change.** Downloads made before this index existed are
     * only recoverable by the scan, so until the Downloads tab is opened once in
     * a given process, those series get no badge here. That is a badge appearing
     * late rather than a wrong badge, and it is the trade the measurement asks
     * for. Opening the Downloads tab fills [cachedIds] with the full answer.
     *
     * Still O(downloaded chapters) in filesystem stats, because `isComplete`
     * misses hit `dirFor`, which probes the tree path and then every legacy
     * root. That is bounded by how much has been downloaded rather than by the
     * size of the library, which is the difference that matters — and it is
     * marked at the call site so the next report says what it costs.
     */
    fun seriesIds(context: Context): Set<String> {
        cachedIds?.let { return it }
        return DownloadIndexStorage.read(context)
            .filter { Downloads.isComplete(context, it.chapterId) }
            .mapTo(mutableSetOf()) { it.seriesId }
            .also { cachedIds = it }
    }

    private fun build(context: Context): List<DownloadedSeries> {
        val live = DownloadIndexStorage.read(context).filter { Downloads.isComplete(context, it.chapterId) }
        val known = live.map { it.chapterId }.toMutableSet()

        val recovered = mutableListOf<DownloadIndexRecord>()
        val scanned = needsRecovery(context, known)
        if (scanned) for (entry in Library.list(context)) {
            for (chapter in ChapterCache.load(context, entry.seriesId)) {
                if (chapter.id in known) continue
                if (!Downloads.isComplete(context, chapter.id)) continue
                known.add(chapter.id)
                recovered += DownloadIndexRecord(
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    sourceId = entry.sourceId,
                    seriesId = entry.seriesId,
                    title = entry.title,
                    cover = entry.cover
                )
            }
        }

        // The scan has now run to completion, so the one-time migration it
        // performs is done. See RECOVERY_DONE.
        if (scanned) runCatching { prefs(context).edit().putBoolean(RECOVERY_DONE, true).apply() }

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
        if (prefs(context).getBoolean(RECOVERY_DONE, false)) return false
        if (DownloadPaths.knownChapterIds(context).any { it !in known }) return true
        return StorageLocation.legacyRoots(context).any { root ->
            root.listFiles()?.any { it.isDirectory } == true
        }
    }

    /**
     * Marks the recovery scan as having happened, so it never runs again.
     *
     * The gate above leaks, and it leaks permanently. `knownChapterIds` holds
     * every chapter that was ever assigned a path, so **one cancelled download
     * makes the first condition true forever** — that id is known to the path
     * index and will never be a completed download. Behind the gate is a read
     * of `ChapterCache` for every entry in the library, which on this library
     * is thousands of files, on the composition thread, every time the memo is
     * cold.
     *
     * What the scan recovers is downloads made before this index existed. That
     * is a **migration**, not a routine check: there is a fixed, finite set of
     * them and once they are found they are recorded. Re-deciding it on every
     * cold cache is re-running a migration because a boolean happened to be
     * true.
     *
     * Stamped after a build that ran the scan, not after every build, so a
     * process that never reached the scan doesn't claim it happened.
     *
     * **If the download tree ever moves** — a storage-location change, or
     * `DownloadPaths.rebuild` — this stamp should be cleared, because a fresh
     * tree can hold folders this index has never seen. Nothing does that today;
     * `invalidate()` deliberately doesn't, since dropping a memo is not the
     * same event as relocating the files.
     */
    private const val RECOVERY_DONE = "download_index_recovered"

    /** Clears the stamp, so the next [list] scans again. For a moved tree. */
    fun forgetRecovery(context: Context) {
        invalidate()
        runCatching { prefs(context).edit().remove(RECOVERY_DONE).apply() }
    }

    // ---------- deleting ----------

    /** Deletes every downloaded chapter of a series, pages and cover included. */
    @Synchronized
    fun deleteSeries(context: Context, series: DownloadedSeries) {
        invalidate()
        series.chapters.forEach { Downloads.delete(context, it.chapterId) }
        DownloadCovers.delete(context, series.seriesId)
        val gone = series.chapters.map { it.chapterId }.toSet()
        DownloadIndexStorage.write(context, DownloadIndexStorage.read(context).filterNot { it.chapterId in gone })
    }


}
