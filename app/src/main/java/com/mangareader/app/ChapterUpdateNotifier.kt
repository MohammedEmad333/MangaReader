package com.mangareader.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Notifications are separate from the ongoing library-refresh progress channel. */
internal object ChapterUpdateNotifier {
    private const val CHANNEL = "new_chapter_updates"
    private const val NOTIFICATION_ID = 1042

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, seriesTitle: String, newlyDetectedCount: Int) {
        if (!shouldNotifyChapterUpdates(
                ChapterUpdateNotificationPrefs.enabled(context),
                permissionGranted(context),
                newlyDetectedCount,
            )
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, "New chapters", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "New chapters found during library refresh" }
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = if (newlyDetectedCount == 1) "1 new chapter" else "$newlyDetectedCount new chapters"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(label)
            .setContentText(seriesTitle)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

/** A notification failure must not make a successful chapter check appear failed. */
internal fun safelyPostChapterUpdate(post: () -> Unit): Boolean =
    try {
        post()
        true
    } catch (_: Exception) {
        false
    }
