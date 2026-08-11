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
    val updatedAt: Long
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
            updatedAt = o.optLong("updatedAt")
        )
    }
}

object History {
    private const val KEY = "history_json"
    private const val CAP = 40

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun list(context: Context): List<HistoryEntry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
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
     * each series. One-off single-file opens carry no seriesId and stay distinct
     * by chapterKey, the same rule [touch] uses.
     */
    fun forDisplay(context: Context): List<HistoryEntry> =
        list(context).distinctBy {
            if (it.seriesId.isBlank()) "chap:${it.chapterKey}" else "series:${it.seriesId}"
        }

    private fun save(context: Context, items: List<HistoryEntry>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
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
     * One-off single-file opens carry a blank seriesId and are kept distinct by
     * chapterKey, exactly as before — collapsing those by their (empty) series
     * would fold every unrelated single file into one row.
     */
    fun touch(context: Context, entry: HistoryEntry) {
        val items = list(context).filterNot {
            it.chapterKey == entry.chapterKey ||
                (entry.seriesId.isNotBlank() && it.seriesId == entry.seriesId)
        }.toMutableList()
        items.add(0, entry)
        while (items.size > CAP) items.removeAt(items.size - 1)
        save(context, items)
    }

    fun remove(context: Context, chapterKey: String) {
        save(context, list(context).filterNot { it.chapterKey == chapterKey })
    }
}
