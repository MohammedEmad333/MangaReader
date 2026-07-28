package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One queued chapter.
 *
 * Everything here has to survive being written to disk and read back in a fresh
 * process, which rules out holding a [Source] or a [Chapter] — the chapter's
 * `handle` is the extension's own SChapter and isn't serialisable. The ids are
 * enough: [SourceManager.listAllSources] resolves the source, and
 * [Source.rehydrateChapter] rebuilds the handle from the chapter id, which is
 * the same path the offline chapter cache already relies on.
 */
data class DownloadItem(
    val sourceId: String,
    val chapterId: String,
    val chapterName: String,
    val seriesTitle: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sourceId", sourceId)
        put("chapterId", chapterId)
        put("chapterName", chapterName)
        put("seriesTitle", seriesTitle)
    }

    companion object {
        fun fromJson(o: JSONObject) = DownloadItem(
            sourceId = o.getString("sourceId"),
            chapterId = o.getString("chapterId"),
            chapterName = o.optString("chapterName"),
            seriesTitle = o.optString("seriesTitle")
        )
    }
}

/**
 * The download queue, shared by the UI and [DownloadService].
 *
 * State lives here rather than in `YomuApp` because a download now outlives the
 * Activity: the service keeps running with the app swiped away, and has to be
 * able to publish progress into a UI that may not exist yet. Both live in the
 * same process, so Compose snapshot state is enough — no flows, no binder. The
 * service writes from a background thread, which snapshot state supports, and
 * any composable reading [items] or [progress] recomposes on its own.
 *
 * The queue is mirrored to disk on every change so that being killed mid-run is
 * survivable. Combined with the resume behaviour already in `Downloads` (partial
 * page files are kept and skipped on the next attempt), a kill costs at most the
 * page that was in flight.
 */
object DownloadQueue {

    private const val FILE = "download_queue.json"

    /** Waiting or in progress, head first. The head is what the service works on. */
    var items by mutableStateOf<List<DownloadItem>>(emptyList())
        private set

    /** chapterId -> percent, for the chapter being downloaded right now. */
    var progress by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Chapter id currently being fetched, or null when idle. */
    var activeId by mutableStateOf<String?>(null)
        private set

    var paused by mutableStateOf(false)
        private set

    /**
     * Bumped every time a chapter leaves the queue. Screens that read download
     * state off the filesystem — the tick / check mark in the chapter list, the
     * storage row in More — key their `remember` on this to re-read.
     */
    var tick by mutableIntStateOf(0)
        private set

    /** Last failure, for the queue screen. Cleared when the user dismisses it. */
    var lastError by mutableStateOf<String?>(null)

    private var restored = false

    // ---------- queue edits (UI side) ----------

    /**
     * Adds chapters that aren't already downloaded or already queued.
     * Returns how many were actually added, so the caller can decide whether
     * there's any point starting the service.
     */
    @Synchronized
    fun enqueue(context: Context, newItems: List<DownloadItem>): Int {
        val known = items.map { it.chapterId }.toMutableSet()
        val added = newItems.filter { item ->
            item.chapterId !in known &&
                !Downloads.isComplete(context, item.chapterId) &&
                known.add(item.chapterId)
        }
        if (added.isEmpty()) return 0
        items = items + added
        save(context)
        return added.size
    }

    @Synchronized
    fun remove(context: Context, chapterId: String) {
        items = items.filterNot { it.chapterId == chapterId }
        progress = progress - chapterId
        save(context)
    }

    @Synchronized
    fun clear(context: Context) {
        items = emptyList()
        progress = emptyMap()
        activeId = null
        save(context)
    }

    fun setPaused(context: Context, value: Boolean) {
        paused = value
        save(context)
    }

    /** True when the given chapter is queued but not yet started. */
    fun isQueued(chapterId: String): Boolean = items.any { it.chapterId == chapterId }

    // ---------- service side ----------

    /** The chapter the service should work on next, or null when there's nothing. */
    fun head(): DownloadItem? = items.firstOrNull()

    fun setActive(chapterId: String?) {
        activeId = chapterId
    }

    fun setProgress(chapterId: String, percent: Int) {
        progress = progress + (chapterId to percent)
    }

    /**
     * Drops a chapter from the queue whether it succeeded or not.
     *
     * A failure leaves the queue rather than staying at the head: a chapter whose
     * source is gone, or whose pages 404, would otherwise block everything behind
     * it forever. Its partial files stay on disk, so re-queuing it resumes.
     */
    @Synchronized
    fun finish(context: Context, chapterId: String) {
        items = items.filterNot { it.chapterId == chapterId }
        progress = progress - chapterId
        if (activeId == chapterId) activeId = null
        tick++
        save(context)
    }

    fun reportError(message: String) {
        lastError = message
    }

    // ---------- persistence ----------

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, FILE)

    private fun save(context: Context) {
        val snapshot = items
        val isPaused = paused
        runCatching {
            val arr = JSONArray()
            snapshot.forEach { arr.put(it.toJson()) }
            file(context).writeText(
                JSONObject().apply {
                    put("paused", isPaused)
                    put("items", arr)
                }.toString()
            )
        }
    }

    /**
     * Reloads the queue written by a previous process.
     *
     * Called from `App.onCreate`, so it runs for any entry point — the Activity
     * launching, or the system restarting the service on its own. The guard makes
     * the second call a no-op rather than clobbering a queue that's already live.
     */
    @Synchronized
    fun restore(context: Context) {
        if (restored) return
        restored = true
        runCatching {
            val f = file(context)
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            paused = root.optBoolean("paused", false)
            val arr = root.optJSONArray("items") ?: return
            items = (0 until arr.length())
                .map { DownloadItem.fromJson(arr.getJSONObject(it)) }
                // A chapter that finished after the last save is already on disk.
                .filterNot { Downloads.isComplete(context, it.chapterId) }
        }
    }
}
