package com.mangareader.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Downloads queued chapters in the background.
 *
 * This exists because downloads used to run in `YomuApp`'s coroutine scope, which
 * dies with the Activity — swiping the app away mid-chapter stopped it. A
 * foreground service is bound to the *process*, not the UI, so the queue keeps
 * draining with the app in the background or closed, and Android won't reclaim
 * the process while the notification is showing.
 *
 * WorkManager would be the other option. It isn't used here because the work is
 * long-running and user-visible rather than deferrable — the user is watching a
 * progress notification and expects it to start *now*, which is exactly the case
 * where WorkManager ends up wrapping a foreground service anyway. This also keeps
 * the dependency list where it is.
 *
 * The actual page fetching is unchanged: [Source.loadPagesProgressively] with
 * `persist = true`, the same call the old in-app path made.
 */
class DownloadService : Service() {

    /**
     * Note the handler: this is the scope the crash of 0.75 came out of.
     *
     * `scope.launch` here makes a `StandaloneCoroutine` on `Dispatchers.IO`,
     * which is exactly what the crash report named. An extension built against
     * a newer API throws a [LinkageError], not an [Exception], so the
     * `catch (e: Exception)` in [runItem] let it past, nothing else was
     * listening, and the process died — from a *download*, with no download
     * screen open, because the queue restarts itself from `MainActivity`.
     *
     * The catch in [runItem] is the real fix. This is the net under it: any
     * throwable that escapes a `launch` in this scope stops the service instead
     * of taking the app with it.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, _ -> runCatching { stopEverything() } }
    )
    private var worker: Job? = null
    private var itemJob: Job? = null
    private val foreground by lazy { DownloadServiceForeground(this) }

    /**
     * Set while a pause is cancelling the chapter in flight.
     *
     * Volatile because it is written from `onStartCommand` on the main thread
     * and read from `runItem`'s finally on a worker one. It distinguishes the
     * two reasons `itemJob` gets cancelled: SKIP and CANCEL_ALL mean the
     * chapter is going away, PAUSE means it is coming back.
     */
    @Volatile
    private var pausing = false

    /**
     * seriesId -> its real chapter list, keyed by chapter id. See [genuineChapter].
     *
     * One entry per series per drain, including a failed lookup (stored empty),
     * so queueing forty chapters of one series costs one extra request rather
     * than forty. Cleared when the loop ends, because a list fetched during the
     * last drain may be hours old by the next one.
     */
    private val chaptersBySeries = mutableMapOf<String, Map<String, Chapter>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        foreground.createChannel()
        DownloadQueue.restore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within a few seconds of startForegroundService() or the
        // system kills the process with a ForegroundServiceDidNotStartInTime
        // crash — so it goes first, before any queue work.
        if (!foreground.goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PAUSE -> {
                DownloadQueue.setPaused(this, true)
                // Cancel the chapter in flight as well as stopping the loop.
                //
                // Without this, pause is only checked between chapters: the
                // loop's `paused` test sits above a `job.join()` that waits for
                // the whole current chapter. A 30-page chapter against a dead
                // host burns 30s per page, so Pause did nothing visible for
                // fifteen minutes and read as a broken button.
                //
                // `pausing` tells runItem's finally that this cancellation must
                // NOT drop the item — see there.
                pausing = true
                itemJob?.cancel()
            }
            ACTION_RESUME -> {
                pausing = false
                DownloadQueue.setPaused(this, false)
            }
            ACTION_PAUSE_ITEM -> {
                val id = intent.getStringExtra(EXTRA_CHAPTER_ID)
                if (id != null) {
                    DownloadQueue.setItemPaused(this, id, true)
                    // Only the chapter in flight needs interrupting; a queued
                    // one is not running, so the flag alone is the whole job.
                    // Same `pausing` contract as the queue-wide pause: the item
                    // must be left in the queue, not finished.
                    if (id == DownloadQueue.activeId) {
                        pausing = true
                        itemJob?.cancel()
                    }
                }
            }
            ACTION_RESUME_ITEM -> {
                val id = intent.getStringExtra(EXTRA_CHAPTER_ID)
                if (id != null) DownloadQueue.setItemPaused(this, id, false)
            }
            ACTION_RESUME_ALL -> {
                // Offered only when individual holds are the ONLY thing
                // stopping the queue. Clearing them then has one meaning, and
                // it is the one thing the user can want from a notification
                // saying nothing is happening.
                pausing = false
                DownloadQueue.setPaused(this, false)
                DownloadQueue.clearItemPauses(this)
            }
            ACTION_SKIP -> {
                val id = intent.getStringExtra(EXTRA_CHAPTER_ID)
                if (id == null || id == DownloadQueue.activeId) itemJob?.cancel()
            }
            ACTION_CANCEL_ALL -> {
                DownloadQueue.clear(this)
                itemJob?.cancel()
                worker?.cancel()
                stopEverything()
                return START_NOT_STICKY
            }
        }

        ensureWorker()
        // Sticky so a process kill under memory pressure brings the service back;
        // the queue itself was already restored from disk in onCreate, so the
        // null intent that comes with a sticky restart is enough to resume.
        return START_STICKY
    }

    override fun onDestroy() {
        foreground.releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- the loop ----------

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            foreground.acquireWakeLock()
            var wasPaused = false
            try {
                while (isActive) {
                    if (DownloadQueue.paused || DownloadQueue.allItemsPaused()) {
                        // Only on the transition: this loop spins every second or
                        // so, and neither re-posting the notification nor holding
                        // the CPU awake is worth anything while nothing is being
                        // fetched.
                        //
                        // allItemsPaused() is here rather than left to head()
                        // returning null: that would break out of the loop and
                        // stop the service, so resuming a chapter would need the
                        // worker rebuilt. Idling keeps it ready.
                        if (!wasPaused) {
                            wasPaused = true
                            foreground.releaseWakeLock()
                            foreground.notifyNow()
                        }
                        delay(PAUSE_POLL_MS)
                        continue
                    }
                    if (wasPaused) {
                        wasPaused = false
                        foreground.acquireWakeLock()
                        foreground.notifyNow()
                    }
                    val item = DownloadQueue.head() ?: break
                    val job = launch { runItem(item) }
                    itemJob = job
                    job.join()
                    itemJob = null
                }
            } finally {
                foreground.releaseWakeLock()
                chaptersBySeries.clear()
                stopEverything()
            }
        }
    }

    /**
     * Downloads one chapter.
     *
     * Every exit path goes through `finish`, including failure: a chapter whose
     * source has been uninstalled, or whose pages have gone, must not sit at the
     * head of the queue blocking everything behind it. Partial pages are left on
     * disk, so queuing it again picks up where this left off.
     */
    private suspend fun runItem(item: DownloadItem) {
        DownloadQueue.setActive(item.chapterId)
        // total null, not 0: the page list request has not gone out yet, and
        // "I don't know how many pages" is not "no pages have arrived".
        DownloadQueue.setProgress(item.chapterId, ready = 0, total = null)
        foreground.notifyNow()
        var failure: String? = null
        var cancelledByPause = false
        try {
            if (Downloads.isComplete(this, item.chapterId)) {
                DownloadIndex.record(this, item)
                return
            }

            val src = SourceManager.listAllSources(this)
                .firstOrNull { it.id == item.sourceId }
                ?: throw IllegalStateException("\"${item.seriesTitle}\" — source is no longer installed")

            // Claims <Source>/<Series>/<Chapter> before the first page is
            // fetched, because the download needs somewhere to go. It has to
            // happen here specifically: the queue stores ids, and this line is
            // the only point at which the source's display name is known. By
            // the time anything else asks for the folder — the Downloads tab,
            // a delete, a size — the extension may have been uninstalled.
            DownloadPaths.register(this, item, src.name)

            // The queue only stores ids, so the extension's own SChapter has to
            // be rebuilt before the source can fetch anything.
            val rebuilt = src.rehydrateChapter(
                Chapter(id = item.chapterId, name = item.chapterName, handle = null)
            )

            // A rebuilt handle carries url and name and nothing else. Some
            // extensions need more than that from the object they were handed —
            // see genuineChapter — so a failure here is retried once against a
            // chapter the extension produced itself before it is called a
            // failure.
            try {
                fetchPages(src, item, rebuilt)
            } catch (e: CancellationException) {
                throw e
            } catch (rebuiltFailure: Throwable) {
                if (!blamesTheHandle(rebuiltFailure)) throw rebuiltFailure
                val genuine = genuineChapter(src, item)
                if (genuine == null || genuine.handle == null) throw rebuiltFailure
                Log.w(
                    TAG,
                    "Rebuilt handle rejected for ${item.chapterId}; " +
                        "retrying with the source's own chapter",
                    rebuiltFailure
                )
                fetchPages(src, item, genuine)
            }

            // The adapter throws ChapterDownloadException when pages are missing,
            // so reaching here normally means it's done. The check stays as a
            // backstop for sources using the default loadPagesProgressively,
            // which marks nothing.
            if (Downloads.isComplete(this, item.chapterId)) {
                DownloadIndex.record(this, item)
            } else {
                failure = "Finished without marking the chapter complete"
            }
        } catch (e: CancellationException) {
            // Recorded before rethrowing, because the finally cannot tell a
            // pause from a skip and the two must not do the same thing.
            cancelledByPause = pausing
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception. This calls into an extension, so it can
            // raise a LinkageError rather than an exception — see
            // sourceFailureMessage, which names the missing symbol instead of
            // letting the failure reach the default handler.
            failure = sourceFailureMessage(e, e.javaClass.simpleName)
        } finally {
            if (cancelledByPause) {
                // Paused mid-chapter. The item STAYS at the head of the queue —
                // finish() would remove it, and with no failure recorded it
                // would vanish as though it had succeeded. Its partial pages are
                // already on disk, so resuming re-runs this and picks up where
                // it stopped.
                DownloadQueue.setActive(null)
                DownloadQueue.setProgress(item.chapterId, ready = 0, total = null)
            } else {
                DownloadQueue.finish(this, item, failure)
            }
        }
    }

    /**
     * Whether a failure could plausibly be the rebuilt handle's fault, and is
     * therefore worth one retry against a real one.
     *
     * The discriminator is whether a page list was ever obtained.
     * [ChapterDownloadException] with pages counted means the extension accepted
     * the chapter, produced its list, and then some of the images failed — a
     * network problem, and re-running the whole fetch for it is the mistake §5
     * records against the manhwatoon retries. Anything else failed at or before
     * `getPageList`, which is where a handle missing the extension's own state
     * shows up — on Asura that is `getChapterUrl` refusing an empty `memo` with
     * "Refresh Chapter List", which is exactly a chapter this app rebuilt.
     */
    private fun blamesTheHandle(t: Throwable): Boolean =
        t !is ChapterDownloadException || t.totalPages == 0

    /** One download attempt, reporting progress into the queue as pages land. */
    private suspend fun fetchPages(src: Source, item: DownloadItem, chapter: Chapter) {
        src.loadPagesProgressively(chapter, persist = true) { partial ->
            // The first callback carries the whole list, so partial.size IS
            // the page count and its arrival is what "the page list came back"
            // means. Stored rather than divided away.
            DownloadQueue.setProgress(
                item.chapterId,
                ready = partial.count { it != null },
                total = partial.size
            )
            foreground.notifyThrottled()
        }
    }

    /**
     * The chapter as the *extension* built it, rather than as this app rebuilt it.
     *
     * [Source.rehydrateChapter] reconstructs an SChapter from the id, which is
     * url and name and nothing else. That was enough for as long as extensions
     * only read the url, and it stopped being enough when they started keeping
     * their own state on the object. The confirmed case is Asura Scans, whose
     * `getPageList` builds its URL from `chapter.memo["mangaSlug"]` — a value
     * only its own chapter-list parse produces, so no rebuild can supply it and
     * `chapter_number` and `date_upload` are lost the same way. The only way to
     * get one is to ask the source for its chapter list again and take the
     * matching entry.
     *
     * Deliberately a fallback rather than the normal path: it costs a request per
     * series, and the overwhelming majority of sources never need it. Returns
     * null when there is nothing better to offer — an item queued before
     * `seriesId` was stored, a series that no longer resolves, or a source that
     * has since dropped the chapter — and the caller then reports the original
     * failure rather than inventing a second one.
     */
    private suspend fun genuineChapter(src: Source, item: DownloadItem): Chapter? {
        if (item.seriesId.isBlank()) return null
        chaptersBySeries[item.seriesId]?.let { return it[item.chapterId] }

        val fetched = try {
            val series = src.restoreSeries(item.seriesId, item.seriesTitle)
            if (series == null) emptyList() else src.listChapters(series)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Swallowed on purpose: this runs while an earlier failure is already
            // in hand, and that one is what the user gets told about. Logged
            // because a source failing here is worth seeing in logcat.
            Log.w(TAG, "Could not re-list chapters for ${item.seriesId}", e)
            emptyList()
        }

        // Cached even when empty, so a source that fails this lookup is asked
        // once per drain rather than once per queued chapter.
        return fetched.associateBy { it.id }
            .also { chaptersBySeries[item.seriesId] = it }[item.chapterId]
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val PAUSE_POLL_MS = 700L

        /** Set on the notification's tap intent; read by MainActivity. */
        const val EXTRA_OPEN_QUEUE = "com.mangareader.app.OPEN_QUEUE"

        const val ACTION_PAUSE = "com.mangareader.app.PAUSE"
        const val ACTION_RESUME = "com.mangareader.app.RESUME"
        const val ACTION_PAUSE_ITEM = "com.mangareader.app.PAUSE_ITEM"
        const val ACTION_RESUME_ITEM = "com.mangareader.app.RESUME_ITEM"
        const val ACTION_RESUME_ALL = "com.mangareader.app.RESUME_ALL"
        const val ACTION_SKIP = "com.mangareader.app.SKIP"
        const val ACTION_CANCEL_ALL = "com.mangareader.app.CANCEL_ALL"
        const val EXTRA_CHAPTER_ID = "chapterId"

        /**
         * Starts (or nudges) the service.
         *
         * Always called from a user action in a visible Activity — tapping Save,
         * Download all, or Resume. That matters on Android 12+, where starting a
         * foreground service from the background throws.
         */
        fun start(context: Context, action: String? = null, chapterId: String? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                if (action != null) setAction(action)
                if (chapterId != null) putExtra(EXTRA_CHAPTER_ID, chapterId)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
