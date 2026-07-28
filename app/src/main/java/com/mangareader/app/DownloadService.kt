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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var itemJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyAt = 0L

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
        DownloadQueue.setProgress(item.chapterId, 0)
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

            // The queue only stores ids, so the extension's own SChapter has to
            // be rebuilt before the source can fetch anything.
            val chapter = src.rehydrateChapter(
                Chapter(id = item.chapterId, name = item.chapterName, handle = null)
            )

            src.loadPagesProgressively(chapter, persist = true) { partial ->
                val total = partial.size
                val ready = partial.count { it != null }
                DownloadQueue.setProgress(
                    item.chapterId,
                    if (total == 0) 0 else ready * 100 / total
                )
                notifyThrottled()
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
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
        } finally {
            DownloadQueue.finish(this, item, failure)
        }
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
        val percent = current?.let { DownloadQueue.progress[it.chapterId] } ?: 0
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
            .setProgress(100, percent, current == null || percent == 0)

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
