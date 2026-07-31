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

    /**
     * Marking a chapter **unread also forgets the page it was left on**.
     *
     * Those were separate stores and nothing connected them, so a chapter marked
     * unread kept its resume position — which was merely untidy until the
     * Start/Resume button started treating a stored page as progress. After
     * that, marking a chapter unread to read it again left it as the furthest
     * chapter touched, and Resume aimed straight back at the page you had asked
     * it to forget.
     *
     * Read state and resume position are two halves of one answer to "where am
     * I", so they are set together here rather than at each call site.
     */
    fun setRead(context: Context, chapterKey: String, read: Boolean) {
        prefs(context).edit().putBoolean("read:" + chapterKey, read).apply()
        if (!read) clearPage(context, chapterKey)
    }

    /**
     * Marks a batch read in a single edit.
     *
     * One `apply()` per chapter is fine for a tap and not for an import, where
     * it's thousands of separate writes to the same file.
     */
    fun setReadBulk(context: Context, chapterKeys: Collection<String>) {
        if (chapterKeys.isEmpty()) return
        val editor = prefs(context).edit()
        chapterKeys.forEach { editor.putBoolean("read:" + it, true) }
        editor.apply()
    }
}
