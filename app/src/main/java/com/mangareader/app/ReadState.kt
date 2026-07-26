package com.mangareader.app

import android.content.Context

/**
 * Per-chapter read/unread flags, keyed by chapter id (the same key
 * used for resume position). Backed by individual boolean prefs.
 */
object ReadState {
    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun isRead(context: Context, chapterKey: String): Boolean =
        prefs(context).getBoolean("read:" + chapterKey, false)

    fun setRead(context: Context, chapterKey: String, read: Boolean) {
        prefs(context).edit().putBoolean("read:" + chapterKey, read).apply()
    }
}
