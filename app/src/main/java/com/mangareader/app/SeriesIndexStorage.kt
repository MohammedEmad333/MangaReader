package com.mangareader.app

import android.content.Context
import org.json.JSONObject

/**
 * JSON persistence and parse memo for [SeriesIndex].
 *
 * High-level read/modify/write synchronization stays in [SeriesIndex]; this
 * object only owns the serialized form and its lock-free reader memo.
 */
internal object SeriesIndexStorage {
    private const val KEY = "series_index_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    private class Memo(
        val raw: String,
        val items: Map<String, SeriesCounts>,
    )

    @Volatile
    private var memo: Memo? = null

    fun all(context: Context): Map<String, SeriesCounts> {
        val raw = StartupTimings.once("Prefs first read") {
            prefs(context).getString(KEY, null)
        } ?: return emptyMap()

        memo?.let { cached ->
            if (cached.raw == raw) return cached.items
        }

        return try {
            StartupTimings.once("SeriesIndex parse") {
                val root = JSONObject(raw)
                val out = HashMap<String, SeriesCounts>(root.length())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val value = root.optJSONObject(id) ?: continue
                    out[id] = SeriesCounts(
                        total = value.optInt("t"),
                        read = value.optInt("r"),
                        latestChapterAt = value.optLong("l"),
                        updatedAt = value.optLong("u"),
                        sweptAt = value.optLong("s"),
                    )
                }
                out.also { memo = Memo(raw, it) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(
        context: Context,
        items: Map<String, SeriesCounts>,
    ) {
        val root = JSONObject()
        items.forEach { (id, counts) ->
            root.put(
                id,
                JSONObject()
                    .put("t", counts.total)
                    .put("r", counts.read)
                    .put("l", counts.latestChapterAt)
                    .put("u", counts.updatedAt)
                    .put("s", counts.sweptAt),
            )
        }

        val text = root.toString()
        prefs(context).edit().putString(KEY, text).apply()
        memo = Memo(text, items)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        memo = null
    }
}
