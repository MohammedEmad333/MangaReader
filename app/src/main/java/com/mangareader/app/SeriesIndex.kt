package com.mangareader.app

import android.content.Context

/**
 * What the library knows about one series' chapters without opening it.
 *
 * [total] and [read] are counts at the moment they were last recorded, not a
 * live view — nothing here re-reads `ReadState`. That is the entire point: the
 * question "how many unread" has to be answerable for several thousand series
 * during one grid draw, and the honest live answer needs a chapter list per
 * series, which is a file read per series.
 */
data class SeriesCounts(
    val total: Int,
    val read: Int,
    val latestChapterAt: Long,
    /**
     * When these counts last *changed*, not when they were last checked.
     *
     * [SeriesIndex.record] refuses a write that wouldn't alter anything, so a
     * timestamp meaning "last looked at" would be the one field guaranteeing
     * every look is a write — and each write rewrites the whole index. This is
     * the useful half of that trade: it still orders "recently moved" correctly
     * and costs nothing.
     */
    val updatedAt: Long,
    /**
     * When a **library refresh** last fetched a chapter list for this series, or
     * 0 if one never has.
     *
     * This is the field [updatedAt] deliberately isn't, and it exists for one
     * reason: a refresh that is stopped has to be able to resume. [updatedAt]
     * cannot answer "has this been swept yet" — a sweep that finds nothing
     * changed writes nothing at all, so *swept and unchanged* and *never swept*
     * are the same entry, and a resume that trusted it would re-fetch the whole
     * library minus the handful that moved. That is the third invisible state
     * from §5 arriving from the other side.
     *
     * It is affordable here and wasn't affordable for [updatedAt] because only
     * [SeriesIndex.recordAll] ever sets it — a hundred series to one write.
     * [record], which is one series per user action and one whole-index rewrite,
     * carries the stored value forward untouched, so opening a series is still
     * not a write.
     */
    val sweptAt: Long = 0L
) {
    val unread: Int get() = (total - read).coerceAtLeast(0)

    /** Some progress, but not finished. Mihon's "Started". */
    val started: Boolean get() = read > 0 && read < total

    val completed: Boolean get() = total > 0 && read >= total
}

/**
 * One aggregate index over the library, keyed by series id.
 *
 * **Why this exists as one store rather than one per feature.** Unread counts,
 * the Unread / Started / Completed filters, sorting by chapter count or unread
 * count, and the badge over a cover are four features that look unrelated and
 * are all blocked on the same missing thing: a per-series chapter total and read
 * count that can be had without touching disk per series. `ChapterCache` holds
 * a real chapter list, but as a file per series and only for series that have
 * actually been opened — so answering any of the above across a 3567-entry
 * library through it means thousands of file reads on every draw. Read as one
 * JSON string with the same memo as `Library.list`, all four become a map
 * lookup at once.
 *
 * **It is a cache, and a lagging one.** An entry is written when a series
 * screen resolves its chapter list, and that is the only moment the app has the
 * chapter list in hand. A series never opened since this index shipped has no
 * entry, and the UI must treat "no entry" as *unknown* rather than as zero —
 * a series with no entry is not "0 unread", it is un-counted, and filtering it
 * out of an Unread view would hide most of a freshly imported library. Every
 * consumer here takes the null branch deliberately.
 *
 * The invalidation path, per the cache table in the handoff: entries are keyed
 * on series id and rewritten whenever the counts move, so the only way to hold
 * a wrong value is to change read state somewhere that never reaches a series
 * screen. [record] is therefore called on read-state edits too, not only on the
 * chapter fetch.
 */
