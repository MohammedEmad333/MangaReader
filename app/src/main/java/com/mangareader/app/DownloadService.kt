package com.mangareader.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
    private val itemRunner by lazy {
        DownloadItemRunner(this) { foreground.notifyThrottled() }
    }

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
                itemRunner.clearSession()
                stopEverything()
            }
        }
    }

    /**
     * Downloads one chapter.
     *
     * Queue ownership stays in the service so pause/skip/cancel semantics remain
     * tied to the lifecycle. Source resolution and the actual page transfer live
     * in [DownloadItemRunner].
     */
    private suspend fun runItem(item: DownloadItem) {
        DownloadQueue.setActive(item.chapterId)
        DownloadQueue.setProgress(item.chapterId, ready = 0, total = null)
        foreground.notifyNow()

        var failure: String? = null
        var cancelledByPause = false
        try {
            failure = itemRunner.download(item)
        } catch (e: CancellationException) {
            cancelledByPause = pausing
            throw e
        } catch (e: Throwable) {
            failure = sourceFailureMessage(e, e.javaClass.simpleName)
        } finally {
            if (cancelledByPause) {
                DownloadQueue.setActive(null)
                DownloadQueue.setProgress(item.chapterId, ready = 0, total = null)
            } else {
                DownloadQueue.finish(this, item, failure)
            }
        }
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
