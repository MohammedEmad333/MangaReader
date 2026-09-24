package com.mangareader.app

import android.content.Context

/** Lightweight persisted resume/completion state for in-app video playback. */
object VideoPlaybackProgress {
    private const val PREFS = "video_playback_progress"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun position(context: Context, key: String): Long {
        if (key.isBlank()) return 0L
        val p = prefs(context)
        return p.getLong("pos:$key", p.getLong(key, 0L))
    }

    fun duration(context: Context, key: String): Long =
        if (key.isBlank()) 0L else prefs(context).getLong("dur:$key", 0L)

    fun isCompleted(context: Context, key: String): Boolean =
        key.isNotBlank() && prefs(context).getBoolean("done:$key", false)

    fun save(context: Context, key: String, positionMs: Long, durationMs: Long) {
        if (key.isBlank() || positionMs <= 0L) return
        prefs(context).edit()
            .remove(key)
            .putLong("pos:$key", positionMs)
            .putLong("dur:$key", durationMs.coerceAtLeast(0L))
            .putBoolean("done:$key", false)
            .apply()
    }

    fun markCompleted(context: Context, key: String, durationMs: Long = 0L) {
        if (key.isBlank()) return
        prefs(context).edit()
            .remove(key)
            .remove("pos:$key")
            .putLong("dur:$key", durationMs.coerceAtLeast(0L))
            .putBoolean("done:$key", true)
            .apply()
    }

    fun markIncomplete(context: Context, key: String) {
        if (key.isBlank()) return
        prefs(context).edit()
            .remove("done:$key")
            .apply()
    }

    fun clear(context: Context, key: String) {
        if (key.isBlank()) return
        prefs(context).edit()
            .remove(key)
            .remove("pos:$key")
            .remove("dur:$key")
            .remove("done:$key")
            .apply()
    }
}
