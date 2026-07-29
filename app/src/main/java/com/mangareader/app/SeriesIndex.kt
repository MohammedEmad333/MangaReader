package com.mangareader.app

import android.content.Context
import org.json.JSONObject

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
object SeriesIndex {
    private const val KEY = "series_index_json"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    // Same memo as Library.list and Categories.assignments, keyed on the raw
    // pref string so an external write can't leave a stale parse behind.
    @Volatile
    private var cacheRaw: String? = null

    @Volatile
    private var cache: Map<String, SeriesCounts>? = null

    fun all(context: Context): Map<String, SeriesCounts> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyMap()
        val hit = cache
        if (hit != null && cacheRaw == raw) return hit
        return try {
            val root = JSONObject(raw)
            val out = HashMap<String, SeriesCounts>(root.length())
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = root.optJSONObject(id) ?: continue
                out[id] = SeriesCounts(
                    total = o.optInt("t"),
                    read = o.optInt("r"),
                    latestChapterAt = o.optLong("l"),
                    updatedAt = o.optLong("u"),
                    sweptAt = o.optLong("s")
                )
            }
            out.also {
                cache = it
                cacheRaw = raw
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun of(context: Context, seriesId: String): SeriesCounts? = all(context)[seriesId]

    private fun save(context: Context, items: Map<String, SeriesCounts>) {
        val root = JSONObject()
        items.forEach { (id, c) ->
            root.put(
                id,
                JSONObject()
                    .put("t", c.total)
                    .put("r", c.read)
                    .put("l", c.latestChapterAt)
                    .put("u", c.updatedAt)
                    .put("s", c.sweptAt)
            )
        }
        val text = root.toString()
        prefs(context).edit().putString(KEY, text).apply()
        // Seeded rather than cleared, like Library.save: the caller normally
        // reads straight back and this saves re-parsing what was just written.
        cache = items
        cacheRaw = text
    }

    /**
     * Counts [chapters] without writing anything.
     *
     * Split out so a sweep can build up a whole batch and hand it to
     * [recordAll] in one write. Returns null for an empty list: a failed fetch
     * falls back to one, and storing `total = 0` would make the series read as
     * "completed" in every filter that asks.
     */
    fun countsFor(
        context: Context,
        sourceId: String,
        chapters: List<Chapter>,
        sweptAt: Long = 0L
    ): SeriesCounts? {
        if (chapters.isEmpty()) return null
        var read = 0
        var latest = 0L
        chapters.forEach { ch ->
            if (ReadState.isRead(context, chapterKeyOf(sourceId, ch))) read++
            if (ch.dateUploaded > latest) latest = ch.dateUploaded
        }
        return SeriesCounts(
            total = chapters.size,
            read = read,
            latestChapterAt = latest,
            updatedAt = System.currentTimeMillis(),
            sweptAt = sweptAt
        )
    }

    /**
     * True when [candidate] would actually change the *counts* stored for a
     * series.
     *
     * Counts only — neither `updatedAt` nor `sweptAt` belongs here. Including
     * `sweptAt` would make every check differ, which is precisely the
     * "every look is a write" trade this store refuses on the [record] path.
     * [recordAll] tests it separately, because there a hundred checks share one
     * write and the answer changes.
     */
    private fun differs(stored: SeriesCounts?, candidate: SeriesCounts): Boolean =
        stored == null ||
            stored.total != candidate.total ||
            stored.read != candidate.read ||
            stored.latestChapterAt != candidate.latestChapterAt

    /**
     * Counts [chapters] and stores the result, if it differs from what's there.
     *
     * The equality check is load-bearing rather than tidy. Every write here
     * rewrites the whole index, and this is called on every series open — so
     * without it, opening a series you've already opened would serialise
     * several thousand entries to change nothing. With it, a re-open is a map
     * lookup and three integer comparisons.
     *
     * **One series at a time only.** Calling this in a loop over the library is
     * the quadratic write this file exists to avoid — 3567 calls, each
     * serialising a growing 3567-entry object. [recordAll] is the one to use for
     * anything sweeping.
     */
    fun record(context: Context, sourceId: String, seriesId: String, chapters: List<Chapter>) {
        if (seriesId.isBlank()) return
        val stored = all(context)[seriesId]
        val candidate = countsFor(context, sourceId, chapters) ?: return
        if (!differs(stored, candidate)) return
        // The sweep stamp is carried over, not stamped and not dropped: opening
        // a series is not a sweep, and writing 0 here would hand a resumed
        // refresh a series it had already paid for.
        save(context, all(context) + (seriesId to candidate.copy(sweptAt = stored?.sweptAt ?: 0L)))
    }

    /**
     * Stores a whole batch in a single write.
     *
     * This is the shape `Library.mergeAll` has and for the identical reason: the
     * per-item call rewrites the entire store, so a sweep built out of [record]
     * is quadratic in library size and takes minutes on the library this app
     * actually has. A refresh over 3567 series flushing every hundred is 36
     * writes rather than 3567.
     *
     * Entries whose counts didn't move are still written, but only to advance
     * `sweptAt` — and **the stored `updatedAt` is kept** rather than taken from
     * the candidate. Writing the candidate wholesale would be the obvious line
     * and would stamp "the counts changed just now" onto every series in the
     * library on every sweep, quietly flattening the one ordering `updatedAt`
     * exists to provide. The number of writes is unchanged either way: each one
     * serialises the whole index regardless of how many entries in it moved.
     */
    fun recordAll(context: Context, updates: Map<String, SeriesCounts>) {
        if (updates.isEmpty()) return
        val current = all(context)
        val changed = HashMap<String, SeriesCounts>(updates.size)
        updates.forEach { (id, candidate) ->
            if (id.isBlank()) return@forEach
            val stored = current[id]
            if (differs(stored, candidate)) {
                changed[id] = candidate
            } else if (stored != null && candidate.sweptAt > stored.sweptAt) {
                changed[id] = stored.copy(sweptAt = candidate.sweptAt)
            }
        }
        if (changed.isEmpty()) return
        save(context, current + changed)
    }

    /**
     * The ids a sweep begun at [since] has already accounted for.
     *
     * Read once per sweep and never inside its loop: [all] is memoised on the
     * raw pref string and every batch flush replaces it, so a per-series lookup
     * would re-parse a several-thousand-entry object once per flush — the
     * O(n)-work-inside-a-loop-over-n shape §5 collected nine of.
     */
    fun sweptSince(context: Context, since: Long): Set<String> {
        if (since <= 0L) return emptySet()
        val out = HashSet<String>()
        all(context).forEach { (id, c) -> if (c.sweptAt >= since) out.add(id) }
        return out
    }

    /**
     * Drops entries for series that are no longer saved.
     *
     * Called from the library's own removal paths rather than on a timer: this
     * is the only index in the app that would otherwise grow on every series
     * *browsed*, and an entry for a series nobody can see is pure weight in a
     * string that gets rewritten in full.
     */
    fun forget(context: Context, seriesIds: Set<String>) {
        if (seriesIds.isEmpty()) return
        val current = all(context)
        val kept = current.filterKeys { it !in seriesIds }
        if (kept.size != current.size) save(context, kept)
    }

    /** For the "clear cached data" action, which drops every derived store. */
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        cache = null
        cacheRaw = null
    }
}
