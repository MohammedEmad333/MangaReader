package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    /** Waiting or in progress, head first. The head is what the service works on. */
    var items by mutableStateOf<List<DownloadItem>>(emptyList())
        private set

    /**
     * Chapters that stopped short, newest first, kept until dismissed.
     *
     * They deliberately don't stay in [items]: a chapter whose source has been
     * uninstalled would block everything behind it forever. Holding them here
     * instead means the queue keeps draining and the user still gets told, with
     * a Retry that puts the chapter back rather than making them find it again
     * in the series screen.
     */
    var failed by mutableStateOf<List<FailedDownload>>(emptyList())
        private set

    /**
     * How far the chapter being downloaded right now has got.
     *
     * **Two integers rather than a percent, deliberately.** A ratio cannot say
     * "I don't know the denominator yet", and that was the bug: the queue wrote
     * 0 both while the page list request was still out *and* once it had come
     * back with nothing downloaded, so the one screen that exists to explain a
     * stalled download could not tell "the source hasn't answered" from "the
     * source answered and no images are arriving". Those have different causes
     * and different fixes. [total] is null until the page list lands, which is
     * the distinction the old percent could not carry.
     */
    data class DownloadProgress(val ready: Int, val total: Int?) {
        /**
         * Whole percent, or null when there is no honest ratio to show — no
         * page list yet, or a page list with nothing in it. Callers with room
         * for only a number use this; callers with room for words should say
         * [ready] of [total] instead, which is strictly more informative.
         */
        val percent: Int?
            get() = if (total == null || total == 0) null else ready * 100 / total
    }

    /** chapterId -> progress, for the chapter being downloaded right now. */
    var progress by mutableStateOf<Map<String, DownloadProgress>>(emptyMap())
        private set

    /** Chapter id currently being fetched, or null when idle. */
    var activeId by mutableStateOf<String?>(null)
        private set

    var paused by mutableStateOf(false)
        private set

    /**
     * Chapters the user has paused individually, as opposed to [paused] which
     * stops the whole queue.
     *
     * **Kept as ids rather than a flag on [DownloadItem]** so the item list
     * stays the plain ordered thing [head] walks, and so a pause survives the
     * item being rewritten. Persisted alongside the queue: a chapter paused
     * before the process died must come back paused, or resuming happens by
     * itself and the user is not the one who asked for it.
     */
    var pausedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Bumped every time a chapter leaves the queue. Screens that read download
     * state off the filesystem — the tick / check mark in the chapter list, the
     * storage row in More, the Downloads tab — key their `remember` on this.
     */
    var tick by mutableIntStateOf(0)
        private set

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
        // Re-queuing something clears its old failure, so the same chapter can't
        // sit in both lists at once.
        failed = failed.filterNot { f -> added.any { it.chapterId == f.item.chapterId } }
        save(context)
        return added.size
    }

    @Synchronized
    fun remove(context: Context, chapterId: String) {
        items = items.filterNot { it.chapterId == chapterId }
        progress = progress - chapterId
        // A pause on a chapter that is no longer queued is dead state, and it
        // would come back to life if the same chapter were queued again.
        pausedIds = pausedIds - chapterId
        save(context)
    }

    /**
     * Drops every queued chapter belonging to one series, and reports which.
     *
     * The series screen's Stop used to call [clear], which emptied the whole
     * queue — stopping one series' downloads also threw away every other
     * series waiting behind it, with no warning and no undo. The caller needs
     * the returned ids to work out whether the chapter currently being fetched
     * was one of them, since that one is held by the service and has to be
     * skipped rather than merely delisted.
     *
     * A blank [seriesId] matches nothing on purpose: items queued before the
     * field was stored carry "", and those must not all be treated as one
     * series. They are unreachable from this path and only [clear] removes them.
     */
    @Synchronized
    fun removeSeries(context: Context, seriesId: String): Set<String> {
        if (seriesId.isBlank()) return emptySet()
        val hit = items.filter { it.seriesId == seriesId }.map { it.chapterId }.toSet()
        if (hit.isEmpty()) return emptySet()
        items = items.filterNot { it.chapterId in hit }
        progress = progress - hit
        save(context)
        return hit
    }

    /** True when any queued chapter belongs to [seriesId]. */
    fun hasSeries(seriesId: String): Boolean =
        seriesId.isNotBlank() && items.any { it.seriesId == seriesId }

    @Synchronized
    fun clear(context: Context) {
        items = emptyList()
        progress = emptyMap()
        pausedIds = emptySet()
        activeId = null
        save(context)
    }

    fun setPaused(context: Context, value: Boolean) {
        paused = value
        save(context)
    }

    /** Pauses or resumes one chapter, independently of the queue-wide [paused]. */
    fun setItemPaused(context: Context, chapterId: String, value: Boolean) {
        pausedIds = if (value) pausedIds + chapterId else pausedIds - chapterId
        save(context)
    }

    /** True when this chapter is paused on its own. */
    fun isItemPaused(chapterId: String): Boolean = chapterId in pausedIds

    /**
     * Releases every individual hold.
     *
     * Separate from [setPaused] on purpose. The queue-wide pause and a set of
     * per-chapter holds are different intents, and resuming one must not
     * silently discard the other — globally pausing while three chapters are
     * held, then resuming, has to leave those three held. This is only for the
     * case where holds are the ONLY thing stopping the queue, where "resume"
     * has no other possible meaning.
     */
    fun clearItemPauses(context: Context) {
        pausedIds = emptySet()
        save(context)
    }

    /** True when the given chapter is queued but not yet started. */
    fun isQueued(chapterId: String): Boolean = items.any { it.chapterId == chapterId }

    // ---------- failures ----------

    @Synchronized
    fun retry(context: Context, chapterIds: Set<String>): Int {
        val edit = DownloadQueueFailures.retry(items, failed, chapterIds)
        if (edit.changed == 0) return 0
        items = edit.items
        failed = edit.failed
        save(context)
        return edit.changed
    }

    fun retryAll(context: Context): Int =
        retry(context, failed.map { it.item.chapterId }.toSet())

    @Synchronized
    fun dismissFailed(context: Context, chapterId: String) {
        failed = DownloadQueueFailures.dismiss(items, failed, chapterId).failed
        save(context)
    }

    @Synchronized
    fun clearFailed(context: Context) {
        failed = DownloadQueueFailures.clear(items, failed).failed
        save(context)
    }

    // ---------- service side ----------

    /**
     * The chapter the service should work on next, or null when there's nothing.
     *
     * **Skips individually paused items rather than stopping at them.** The
     * head used to be simply the first item; once a single chapter can be
     * paused, stopping at it would let one paused row hold up every runnable
     * chapter behind it — a pause that reads as a freeze. Skipping leaves the
     * paused item exactly where it is in [items], so resuming it does not send
     * it to the back of the queue.
     */
    fun head(): DownloadItem? = items.firstOrNull { it.chapterId !in pausedIds }

    /** True when every queued chapter is individually paused. */
    fun allItemsPaused(): Boolean = items.isNotEmpty() && items.all { it.chapterId in pausedIds }

    fun setActive(chapterId: String?) {
        activeId = chapterId
    }

    /**
     * Records progress for [chapterId].
     *
     * [total] null means the page list has not come back yet. Pass it null
     * rather than 0 — 0 is a page list that arrived empty, which is a different
     * and much rarer thing.
     */
    fun setProgress(chapterId: String, ready: Int, total: Int?) {
        progress = progress + (chapterId to DownloadProgress(ready, total))
    }

    /**
     * Takes a chapter out of the queue, whether it succeeded or not.
     *
     * A non-null [failure] files it under [failed] on the way out. Its partial
     * pages stay on disk either way, so a retry resumes rather than restarts.
     */
    @Synchronized
    fun finish(context: Context, item: DownloadItem, failure: String? = null) {
        items = items.filterNot { it.chapterId == item.chapterId }
        progress = progress - item.chapterId
        pausedIds = pausedIds - item.chapterId
        if (activeId == item.chapterId) activeId = null
        if (failure != null) {
            failed = listOf(FailedDownload(item, failure)) +
                failed.filterNot { it.item.chapterId == item.chapterId }
        }
        tick++
        save(context)
    }

    // ---------- persistence ----------

    private fun save(context: Context) {
        DownloadQueueStore.save(
            context = context,
            items = items,
            failed = failed,
            paused = paused,
            pausedIds = pausedIds,
        )
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
        DownloadQueueStore.restore(context)?.let { snapshot ->
            paused = snapshot.paused
            pausedIds = snapshot.pausedIds
            items = snapshot.items
            failed = snapshot.failed
        }
    }
}
