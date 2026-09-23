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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Owns the Android foreground-service mechanics for [DownloadService].
 *
 * Queue draining and source work stay in DownloadService; notification rendering,
 * wake-lock lifetime and foreground promotion live here so each class has one job.
 */
internal class DownloadServiceForeground(
    private val service: Service,
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyAt = 0L

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = service.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chapter download progress"
                setShowBadge(false)
            }
        )
    }

    /** @return false if Android refused the foreground start. */
    fun goForeground(): Boolean = runCatching {
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }.isSuccess

    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = service.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    fun notifyThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        notifyNow()
    }

    fun notifyNow() {
        lastNotifyAt = System.currentTimeMillis()
        val manager = service.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val remaining = DownloadQueue.items.size
        val current = DownloadQueue.head()
        val progress = current?.let { DownloadQueue.progress[it.chapterId] }
        val percent = progress?.percent
        val isPaused = DownloadQueue.paused
        val allOnHold = DownloadQueue.allItemsPaused()

        val open = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(DownloadService.EXTRA_OPEN_QUEUE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                when {
                    isPaused -> "Downloads paused"
                    allOnHold -> "All chapters on hold"
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
            .setProgress(100, percent ?: 0, current == null || percent == null)

        val resumeAll = !isPaused && allOnHold
        builder.addAction(
            0,
            when {
                isPaused -> "Resume"
                resumeAll -> "Resume all"
                else -> "Pause"
            },
            action(
                when {
                    isPaused -> DownloadService.ACTION_RESUME
                    resumeAll -> DownloadService.ACTION_RESUME_ALL
                    else -> DownloadService.ACTION_PAUSE
                },
                1
            )
        )
        builder.addAction(
            0,
            "Cancel all",
            action(DownloadService.ACTION_CANCEL_ALL, 2)
        )
        return builder.build()
    }

    private fun action(name: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            service,
            requestCode,
            Intent(service, DownloadService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
        const val WAKE_TAG = "Yomu:downloads"
        const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
        const val NOTIFY_INTERVAL_MS = 500L
    }
}
