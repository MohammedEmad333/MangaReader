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
    val updatedAt: Long
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
                    updatedAt = o.optLong("u")
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
     * Counts [chapters] and stores the result, if it differs from what's there.
     *
     * The equality check is load-bearing rather than tidy. Every write here
     * rewrites the whole index, and this is called on every series open — so
     * without it, opening a series you've already opened would serialise
     * several thousand entries to change nothing. With it, a re-open is a map
     * lookup and three integer comparisons.
     *
     * An empty [chapters] is ignored outright. A failed fetch falls back to an
     * empty list, and writing `total = 0` for it would make the series read as
     * "completed" in every filter that asks.
     */
    fun record(context: Context, sourceId: String, seriesId: String, chapters: List<Chapter>) {
        if (seriesId.isBlank() || chapters.isEmpty()) return
        var read = 0
        var latest = 0L
        chapters.forEach { ch ->
            if (ReadState.isRead(context, chapterKeyOf(sourceId, ch))) read++
            if (ch.dateUploaded > latest) latest = ch.dateUploaded
        }
        val existing = all(context)[seriesId]
        if (existing != null &&
            existing.total == chapters.size &&
            existing.read == read &&
            existing.latestChapterAt == latest
        ) return
        save(
            context,
            all(context) + (seriesId to SeriesCounts(
                total = chapters.size,
                read = read,
                latestChapterAt = latest,
                updatedAt = System.currentTimeMillis()
            ))
        )
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
