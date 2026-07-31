package com.mangareader.app

import android.content.Context

/**
 * Which library covers are wrong, and how each one came to be known.
 *
 * `LibraryEntry` caches a cover string at add time so the grid renders instantly
 * and offline. Nothing ever re-asks, so the string rots in two different ways,
 * and the two are not equally cheap to find:
 *
 * - **Absent.** Blank, or pointing at a loopback address a self-hosted source
 *   handed out on somebody else's device. Both are visible in the stored data
 *   with no network at all, so finding these costs nothing.
 * - **Stale.** Present, well-formed, and 404 — the source moved or re-encoded
 *   the file, which a content hash in the filename makes routine. Nothing in the
 *   stored string says so. The only cheap way to know is to notice the failure
 *   at the moment the grid tries to draw it, which is what [report] is for.
 *
 * `Library.healCover` covers neither case in bulk: it fires once per series, on
 * open, and only on the absent kind. That is the whole of both board items —
 * a 3571-entry library repaired one tap at a time.
 *
 * The repair itself belongs to the library refresh sweep, which is the only
 * thing in this app that walks the whole library on a schedule the user chose.
 * This object is just the list of what to fix when it gets there.
 */
object CoverRepair {
    private const val KEY = "cover_repair_ids"

    /**
     * A bound on how many stale covers are remembered at once.
     *
     * A source that moves its whole CDN would otherwise report every entry it
     * has, and this is one SharedPreferences string set — the same shape the
     * library itself is, with the same rewrite-everything cost. Repairs drain
     * the set as they land, so hitting the cap delays a repair rather than
     * losing it: the next scroll past that cover reports it again.
     */
    private const val LIMIT = 2000

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    // Read on every sweep candidate check and written from the grid's error
    // callback, so it is worth not going to disk each time. Same memo shape as
    // Library.list, minus the parse — a string set needs none.
    @Volatile
    private var cached: Set<String>? = null

    fun reported(context: Context): Set<String> =
        cached ?: (prefs(context).getStringSet(KEY, emptySet()) ?: emptySet())
            .toSet()
            .also { cached = it }

    /**
     * Records that [seriesId]'s stored cover is gone.
     *
     * Called from a composable's error callback, so it has to be cheap on the
     * common path: a cover that fails twice, or a grid cell recycled over the
     * same failing entry, must not be a write. The membership test is what makes
     * that true — the disk write happens once per newly discovered failure.
     */
    fun report(context: Context, seriesId: String) {
        if (seriesId.isBlank()) return
        val current = reported(context)
        if (seriesId in current || current.size >= LIMIT) return
        val next = current + seriesId
        cached = next
        // A fresh set each time. SharedPreferences does not copy the set it is
        // given and returns the same instance to the next reader, so mutating a
        // stored one edits a value that has already been handed out.
        prefs(context).edit().putStringSet(KEY, next).apply()
    }

    /** Drops ids whose covers have been rewritten. */
    fun clear(context: Context, seriesIds: Set<String>) {
        if (seriesIds.isEmpty()) return
        val current = reported(context)
        val next = current - seriesIds
        if (next.size == current.size) return
        cached = next
        prefs(context).edit().putStringSet(KEY, next).apply()
    }

    /**
     * Whether this entry's cover is worth spending a request on.
     *
     * [reported] is passed in rather than read here because this is asked once
     * per series across a whole sweep, and the answer cannot change while one is
     * running — the sweep is the only thing that clears the set.
     */
    fun needsRepair(entry: LibraryEntry, reported: Set<String>): Boolean =
        entry.cover.isBlank() ||
            isLoopback(entry.cover) ||
            entry.seriesId in reported
}
