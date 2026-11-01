package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One "continue reading" entry. Enough to reopen the exact chapter:
 * which source (sourceId), which series (seriesId), which chapter
 * (chapterKey — also the pos: key used for resume), plus display bits.
 * sourceId/seriesId are blank for one-off single-file opens.
 */
data class HistoryEntry(
    val chapterKey: String,
    val title: String,
    val sourceId: String,
    val seriesId: String,
    val coverPath: String,
    val page: Int,
    val total: Int,
    val updatedAt: Long,
    val mediaType: String = "manga",
    val detail: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("chapterKey", chapterKey)
        put("title", title)
        put("sourceId", sourceId)
        put("seriesId", seriesId)
        put("coverPath", coverPath)
        put("page", page)
        put("total", total)
        put("updatedAt", updatedAt)
        put("mediaType", mediaType)
        put("detail", detail)
    }

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(
            chapterKey = o.getString("chapterKey"),
            title = o.optString("title"),
            sourceId = o.optString("sourceId"),
            seriesId = o.optString("seriesId"),
            coverPath = o.optString("coverPath"),
            page = o.optInt("page"),
            total = o.optInt("total"),
            updatedAt = o.optLong("updatedAt"),
            mediaType = o.optString("mediaType", "manga"),
            detail = o.optString("detail"),
        )
    }
}

object History {
    private const val KEY = "history_json"
    private const val CAP = 40

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    // Reader progress touches history on every settled page. Re-parsing the
    // same capped JSON array for every page is pure main/IO churn, so keep the
    // decoded list beside the raw value exactly like Library does.
    @Volatile
    private var listRaw: String? = null

    @Volatile
    private var listCache: List<HistoryEntry>? = null

    @Synchronized
    fun list(context: Context): List<HistoryEntry> {
        val raw = prefs(context).getString(KEY, null)
        val hit = listCache
        if (hit != null && listRaw == raw) return hit
        if (raw == null) {
            return emptyList<HistoryEntry>().also {
                listRaw = null
                listCache = it
            }
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
                .also {
                    listRaw = raw
                    listCache = it
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * One entry per series — the latest chapter read — for the History screen.
     *
     * [touch] keeps the store per-series going forward, but a file written
     * before that upsert existed can still hold several chapters of one series,
     * so the screen collapses them here too. [list] stays the raw store, because
     * `remove` and `touch` key off every stored row.
     *
     * `list` is newest-first, so `distinctBy` keeps the most recent chapter of
     * each (sourceId, seriesId) pair. Two extensions may use the same seriesId,
     * and must never collapse into one history row. Standalone files use chapterKey.
     */
    fun forDisplay(context: Context): List<HistoryEntry> =
        list(context).distinctBy(::historyEntryIdentity)

    private fun save(context: Context, items: List<HistoryEntry>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val text = arr.toString()
        prefs(context).edit().putString(KEY, text).apply()
        listRaw = text
        listCache = items
    }

    /**
     * Upsert by series, move to front, cap the list.
     *
     * History is a per-series "continue reading" list: reading a new chapter of
     * a series already present REPLACES that series' row rather than adding
     * beside it, so the entry always shows the latest chapter and page. This is
     * also what makes the 40-entry cap hold 40 *series* rather than filling up
     * with one heavily-read series.
     *
     * Source-qualified series IDs are required: same IDs coming from two
     * different sources stay separate. One-off files stay keyed by chapterKey.
     */
    @Synchronized
    fun touch(context: Context, entry: HistoryEntry) {
        val items = list(context).filterNot {
            shouldReplaceHistoryEntry(it, entry)
        }.toMutableList()
        items.add(0, entry)
        while (items.size > CAP) items.removeAt(items.size - 1)
        save(context, items)
    }

    /**
     * Applies many history touches in memory and persists the final capped list once.
     *
     * This preserves the exact sequential [touch] semantics while avoiding one full
     * JSON serialization / SharedPreferences write per imported entry.
     */
    @Synchronized
    fun touchAll(context: Context, entries: List<HistoryEntry>) {
        if (entries.isEmpty()) return
        val items = list(context).toMutableList()
        entries.forEach { entry ->
            items.removeAll { shouldReplaceHistoryEntry(it, entry) }
            items.add(0, entry)
            while (items.size > CAP) items.removeAt(items.size - 1)
        }
        save(context, items)
    }

    @Synchronized
    fun remove(context: Context, chapterKey: String) {
        save(context, list(context).filterNot { it.chapterKey == chapterKey })
    }

    /** Clears the capped history store with a single write. */
    @Synchronized
    fun clear(context: Context) {
        if (list(context).isEmpty()) return
        save(context, emptyList())
    }
}
