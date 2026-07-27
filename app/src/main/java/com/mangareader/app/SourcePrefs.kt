package com.mangareader.app

import android.content.Context

/**
 * Per-source UI state for the Sources list: which sources are pinned, and which
 * one was opened most recently.
 *
 * Both are keyed by `Source.id`, which for extensions is "tachi:<sourceId>" and
 * stays stable across reinstalls of the same extension. That means a pin
 * survives an extension update, and an uninstalled source's pin simply stops
 * matching anything rather than corrupting the list.
 *
 * Stored in the same SharedPreferences file as everything else in the app.
 */
object SourcePrefs {

    private const val KEY_PINNED = "pinned_sources"
    private const val KEY_LAST_USED = "last_used_source"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    /**
     * Read with a defensive copy. getStringSet's docs are explicit that the
     * returned set must not be mutated, and that the instance may be shared —
     * toSet() here keeps a stale reference from ever leaking back in.
     */
    fun pinned(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()

    fun isPinned(context: Context, id: String): Boolean = id in pinned(context)

    /** Flips the pin and returns the new full set, so callers can update state. */
    fun togglePin(context: Context, id: String): Set<String> {
        val next = pinned(context).toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs(context).edit().putStringSet(KEY_PINNED, next).apply()
        return next
    }

    /** Id of the source opened most recently, or null on a fresh install. */
    fun lastUsed(context: Context): String? =
        prefs(context).getString(KEY_LAST_USED, null)?.takeIf { it.isNotBlank() }

    fun setLastUsed(context: Context, id: String) {
        prefs(context).edit().putString(KEY_LAST_USED, id).apply()
    }
}
