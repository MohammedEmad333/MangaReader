package com.mangareader.app

import android.content.Context

/** Lightweight persisted resume point for in-app video playback. */
object VideoPlaybackProgress {
    private const val PREFS = "video_playback_progress"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun position(context: Context, key: String): Long =
        if (key.isBlank()) 0L else prefs(context).getLong(key, 0L)

    fun save(context: Context, key: String, positionMs: Long) {
        if (key.isBlank() || positionMs <= 0L) return
        prefs(context).edit().putLong(key, positionMs).apply()
    }

    fun clear(context: Context, key: String) {
        if (key.isBlank()) return
        prefs(context).edit().remove(key).apply()
    }
}
