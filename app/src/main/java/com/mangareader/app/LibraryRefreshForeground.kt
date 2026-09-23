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
 * Owns Android foreground-service mechanics for [LibraryRefreshService].
 *
 * Refresh orchestration stays in the service; wake-lock and notification state
 * live here so the worker code does not also have to be Android UI plumbing.
 */
internal class LibraryRefreshForeground(
    private val service: Service,
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyAt = 0L

    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = service.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_TAG,
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = service.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Library refresh",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress while chapter counts are updated"
                setShowBadge(false)
            },
        )
    }

    fun goForeground(): Boolean = runCatching {
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }.isSuccess

    fun notifyThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        notifyNow()
    }

    fun notifyNow() {
        lastNotifyAt = System.currentTimeMillis()
        val manager = service.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val total = LibraryRefresh.total
        val done = LibraryRefresh.done

        val open = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Refreshing library")
            .setContentText(
                if (total == 0) "Starting" else "$done of $total",
            )
            .setSubText(LibraryRefresh.currentTitle.ifBlank { null })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .addAction(
                0,
                "Stop",
                PendingIntent.getService(
                    service,
                    1,
                    Intent(
                        service,
                        LibraryRefreshService::class.java,
                    ).setAction(LibraryRefreshService.ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "library_refresh"
        const val NOTIFICATION_ID = 1002
        const val WAKE_TAG = "Yomu:refresh"
        const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
        const val NOTIFY_INTERVAL_MS = 500L
    }
}
