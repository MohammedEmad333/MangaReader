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

    fun list(context: Context): List<LibraryEntry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { LibraryEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, items: List<LibraryEntry>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun contains(context: Context, seriesId: String): Boolean =
        list(context).any { it.seriesId == seriesId }

    /** Upsert by seriesId, newest first. */
    fun add(context: Context, entry: LibraryEntry) {
        val items = list(context).filterNot { it.seriesId == entry.seriesId }.toMutableList()
        items.add(0, entry)
        save(context, items)
    }

    /** Removes the series and clears its category assignments. */
    fun remove(context: Context, seriesId: String) {
        save(context, list(context).filterNot { it.seriesId == seriesId })
        Categories.setCategoriesFor(context, seriesId, emptySet())
    }
}
