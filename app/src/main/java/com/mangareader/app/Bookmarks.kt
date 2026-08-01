package com.mangareader.app

import android.content.Context

/**
 * Per-chapter bookmarks, keyed by the same `chapterKeyOf(sourceId, chapter)`
 * string that read state and resume position use.
 *
 * **The backing field three features were blocked on.** SY's chapter filter has
 * a Bookmarked row and its download menu has a Bookmarked action; §4 of the
 * project handoff has listed Bookmarked since 0.65 as blocked on a data model
 * decision rather than on an index. This is that decision, and it is the
 * cheapest one available: a boolean pref per chapter, the same shape as
 * [ReadState], so nothing new has to be backed up, migrated or invalidated —
 * `Backup` copies every SharedPreferences entry verbatim and picks this up for
 * free.
 *
 * **Deliberately independent of read state.** A bookmark says "come back to
 * this"; reading it, or marking it unread, says nothing about whether you still
 * want to. [ReadState.setRead] clears the resume position because those two are
 * halves of one answer — a bookmark is a third thing and is left alone by both.
 *
 * Not scoped to a series: the key already carries the source and chapter, so
 * this is uncapped and survives a series being removed from the library and
 * added back, exactly as read state does.
 */
object Bookmarks {
    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun isBookmarked(context: Context, chapterKey: String): Boolean =
        prefs(context).getBoolean("bm:" + chapterKey, false)

    fun setBookmarked(context: Context, chapterKey: String, value: Boolean) {
        // Removed rather than stored false. This pref file already holds a
        // library, categories, read flags and resume positions for a 3575-entry
        // library; a `false` per chapter anyone ever un-bookmarked is a row that
        // means exactly what its absence means.
        val editor = prefs(context).edit()
        if (value) editor.putBoolean("bm:" + chapterKey, true)
        else editor.remove("bm:" + chapterKey)
        editor.apply()
    }

    /** One edit for a whole selection, the same reason [ReadState.setReadBulk] exists. */
    fun setBookmarkedBulk(
        context: Context,
        chapterKeys: Collection<String>,
        value: Boolean
    ) {
        if (chapterKeys.isEmpty()) return
        val editor = prefs(context).edit()
        chapterKeys.forEach {
            if (value) editor.putBoolean("bm:" + it, true) else editor.remove("bm:" + it)
        }
        editor.apply()
    }
}
