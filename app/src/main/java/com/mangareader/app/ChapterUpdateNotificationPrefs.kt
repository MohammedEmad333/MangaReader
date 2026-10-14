package com.mangareader.app

import android.content.Context

/** Explicit opt-in for release alerts, independent of foreground refresh progress. */
internal object ChapterUpdateNotificationPrefs {
    private const val PREFS = "manga_reader"
    private const val ENABLED_KEY = "chapter_update_notifications_enabled"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED_KEY, enabled).apply()
    }
}

/** An update alone is insufficient: the user must opt in and allow notifications. */
internal fun shouldNotifyChapterUpdates(
    optedIn: Boolean,
    permissionGranted: Boolean,
    newlyDetectedCount: Int,
): Boolean = optedIn && permissionGranted && newlyDetectedCount > 0
