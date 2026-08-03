package com.mangareader.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyAt = 0L

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
        createChannel()
        DownloadQueue.restore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within a few seconds of startForegroundService() or the
        // system kills the process with a ForegroundServiceDidNotStartInTime
        // crash — so it goes first, before any queue work.
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PAUSE -> DownloadQueue.setPaused(this, true)
            ACTION_RESUME -> DownloadQueue.setPaused(this, false)
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
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- the loop ----------

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            acquireWakeLock()
            var wasPaused = false
            try {
                while (isActive) {
                    if (DownloadQueue.paused) {
                        // Only on the transition: this loop spins every second or
                        // so, and neither re-posting the notification nor holding
                        // the CPU awake is worth anything while nothing is being
                        // fetched.
                        if (!wasPaused) {
                            wasPaused = true
                            releaseWakeLock()
                            notifyNow()
                        }
                        delay(PAUSE_POLL_MS)
                        continue
                    }
                    if (wasPaused) {
                        wasPaused = false
                        acquireWakeLock()
                        notifyNow()
                    }
                    val item = DownloadQueue.head() ?: break
                    val job = launch { runItem(item) }
                    itemJob = job
                    job.join()
                    itemJob = null
                }
            } finally {
                releaseWakeLock()
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
        notifyNow()
        var failure: String? = null
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
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception. This calls into an extension, so it can
            // raise a LinkageError rather than an exception — see
            // sourceFailureMessage, which names the missing symbol instead of
            // letting the failure reach the default handler.
            failure = sourceFailureMessage(e, e.javaClass.simpleName)
        } finally {
            DownloadQueue.finish(this, item, failure)
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
            notifyThrottled()
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

    // ---------- wake lock ----------

    /**
     * A foreground service keeps the *process* alive, but not the CPU: with the
     * screen off the device can still suspend mid-transfer. This is what makes
     * "queue ten chapters and pocket the phone" actually work.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            // Timed rather than indefinite: if this service ever dies without
            // running its finally block, the lock still expires on its own.
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    // ---------- notification ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                // LOW: an ongoing progress bar shouldn't buzz or make noise.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chapter download progress"
                setShowBadge(false)
            }
        )
    }

    /** @return false if the system refused the foreground start. */
    private fun goForeground(): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }.isSuccess

    /** Rate-limited, because progress lands once per page batch. */
    private fun notifyThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        notifyNow()
    }

    private fun notifyNow() {
        lastNotifyAt = System.currentTimeMillis()
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val remaining = DownloadQueue.items.size
        val current = DownloadQueue.items.firstOrNull()
        val progress = current?.let { DownloadQueue.progress[it.chapterId] }
        val percent = progress?.percent
        val isPaused = DownloadQueue.paused

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                when {
                    isPaused -> "Downloads paused"
                    current == null -> "Finishing downloads"
                    else -> current.seriesTitle.ifBlank { "Downloading" }
                }
            )
            .setContentText(current?.chapterName ?: "")
            .setSubText(if (remaining > 1) "$remaining chapters left" else null)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Indeterminate while there is no ratio to show — no chapter, no
            // page list yet, or an empty one. Previously this also went
            // indeterminate at a genuine 0 of N, which looked identical to not
            // having asked yet.
            .setProgress(100, percent ?: 0, current == null || percent == null)

        builder.addAction(
            0,
            if (isPaused) "Resume" else "Pause",
            action(if (isPaused) ACTION_RESUME else ACTION_PAUSE, 1)
        )
        builder.addAction(0, "Cancel all", action(ACTION_CANCEL_ALL, 2))

        return builder.build()
    }

    private fun action(name: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, DownloadService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_TAG = "Yomu:downloads"
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
        private const val NOTIFY_INTERVAL_MS = 500L
        private const val PAUSE_POLL_MS = 700L

        const val ACTION_PAUSE = "com.mangareader.app.PAUSE"
        const val ACTION_RESUME = "com.mangareader.app.RESUME"
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
