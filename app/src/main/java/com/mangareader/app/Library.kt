package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved series. Holds enough to reopen it without browsing: which source
 * it came from and its id within that source. Title and cover are cached so
 * the Library grid renders instantly, offline, without hitting the network.
 */
data class LibraryEntry(
    val seriesId: String,
    val sourceId: String,
    val title: String,
    val cover: String,
    val addedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("seriesId", seriesId)
        put("sourceId", sourceId)
        put("title", title)
        put("cover", cover)
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = LibraryEntry(
            seriesId = o.getString("seriesId"),
            sourceId = o.optString("sourceId"),
            title = o.optString("title"),
            cover = o.optString("cover"),
            addedAt = o.optLong("addedAt")
        )
    }
}

object Library {
    private const val KEY = "library_json"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    // Same memo as Categories.assignments, for the same reason: contains() and
    // every screen that filters call this, and re-parsing several thousand
    // entries per call is what a large import turns into.
    @Volatile
    private var listRaw: String? = null

    @Volatile
    private var listCache: List<LibraryEntry>? = null

    fun list(context: Context): List<LibraryEntry> {
        // Two marks on purpose. "Prefs first read" is Android loading and
        // parsing the whole shared_prefs XML — a cost this call pays on behalf
        // of all twelve call sites, and one no amount of memoising here avoids.
        // The parse below is this object's own.
        val raw = StartupTimings.once("Prefs first read") {
            prefs(context).getString(KEY, null)
        } ?: return emptyList()
        val hit = listCache
        if (hit != null && listRaw == raw) return hit
        return try {
            StartupTimings.once("Library parse") {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { LibraryEntry.fromJson(arr.getJSONObject(it)) }
                    .also {
                        listCache = it
                        listRaw = raw
                    }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, items: List<LibraryEntry>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val text = arr.toString()
        prefs(context).edit().putString(KEY, text).apply()
        // Seeded rather than cleared: the caller usually reads straight back,
        // and this saves re-parsing what it just serialised.
        listCache = items
        listRaw = text
    }

    fun contains(context: Context, seriesId: String): Boolean =
        list(context).any { it.seriesId == seriesId }

    /** Upsert by seriesId, newest first. */
    fun add(context: Context, entry: LibraryEntry) {
        val items = list(context).filterNot { it.seriesId == entry.seriesId }.toMutableList()
        items.add(0, entry)
        save(context, items)
    }

    /**
     * Upserts a batch in one write.
     *
     * [add] rewrites the entire library JSON per call, which is fine for one tap
     * and quadratic for an import: 4645 entries would be 4645 growing
     * serialisations. New entries keep their own order and land in front of
     * whatever was already there.
     */
    fun mergeAll(context: Context, entries: List<LibraryEntry>) {
        if (entries.isEmpty()) return
        val incoming = entries.map { it.seriesId }.toHashSet()
        val kept = list(context).filterNot { it.seriesId in incoming }
        save(context, entries + kept)
    }

    /**
     * Fills in a library entry's cover once, if it hasn't got one.
     *
     * Deliberately does nothing when a usable cover is already stored. Every
     * write here rewrites the whole library JSON, and after a large import that
     * is several thousand entries — running it on each series open would be a
     * real cost for no gain. So this is a one-time heal per series, not an
     * update, and it fires in exactly two cases: no cover at all, or one
     * pointing at a loopback address, which an import from another device's
     * self-hosted source leaves behind and which can never load here.
     */
    fun healCover(context: Context, seriesId: String, cover: Any?) {
        // Series.cover is Any? — a URL string from an extension, a File from the
        // local folder — while an entry stores a String. This is the same
        // narrowing LibraryScreens does where it builds one.
        val text = (cover as? String)
            ?: (cover as? java.io.File)?.absolutePath
            ?: return
        if (text.isBlank() || isLoopback(text)) return
        val items = list(context)
        val index = items.indexOfFirst { it.seriesId == seriesId }
        if (index < 0) return
        val current = items[index].cover
        if (current.isNotBlank() && !isLoopback(current)) return
        save(context, items.toMutableList().also { it[index] = it[index].copy(cover = text) })
    }

    /**
     * Removes many series in one write.
     *
     * The per-series [remove] rewrites the whole library JSON each call, so
     * doing this a thousand times over is quadratic — the same reason
     * [mergeAll] exists.
     */
    fun removeAll(context: Context, seriesIds: Set<String>) {
        if (seriesIds.isEmpty()) return
        val present = list(context)
        val kept = present.filterNot { it.seriesId in seriesIds }
        if (kept.size != present.size) save(context, kept)
        // Only the ones that actually have assignments: setCategoriesFor
        // rewrites the whole assignment JSON, so blindly calling it per id
        // would reintroduce the quadratic write this method exists to avoid.
        val assigned = Categories.assignedSeries(context)
        seriesIds.forEach {
            if (it in assigned) Categories.setCategoriesFor(context, it, emptySet())
        }
        // Chapter counts are only ever recorded for series in the library, so
        // leaving these behind is weight in a store that gets rewritten whole.
        SeriesIndex.forget(context, seriesIds)
    }

    /** Removes the series and clears its category assignments. */
    fun remove(context: Context, seriesId: String) {
        save(context, list(context).filterNot { it.seriesId == seriesId })
        Categories.setCategoriesFor(context, seriesId, emptySet())
        SeriesIndex.forget(context, setOf(seriesId))
    }
}
