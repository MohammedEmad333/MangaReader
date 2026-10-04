package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class AnimeOfflineItem(
    val title: String,
    val path: String,
    val sourceUrl: String,
    val quality: String,
    val downloadedAt: Long,
)

/** Small persistent index for HLS packages downloaded by Yomu itself. */
internal object AnimeOfflineIndex {
    private const val PREFS = "anime_offline_downloads"
    private const val KEY_ITEMS = "items"

    fun list(context: Context): List<AnimeOfflineItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString("path")
                    if (path.isBlank()) continue
                    add(
                        AnimeOfflineItem(
                            title = item.optString("title", "Yomu episode"),
                            path = path,
                            sourceUrl = item.optString("sourceUrl"),
                            quality = item.optString("quality"),
                            downloadedAt = item.optLong("downloadedAt"),
                        ),
                    )
                }
            }.distinctBy { it.path }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(context: Context, item: AnimeOfflineItem) {
        val next = (list(context).filterNot { it.path == item.path } + item)
            .sortedByDescending { it.downloadedAt }
        val json = JSONArray().apply {
            next.forEach { entry ->
                put(
                    JSONObject().apply {
                        put("title", entry.title)
                        put("path", entry.path)
                        put("sourceUrl", entry.sourceUrl)
                        put("quality", entry.quality)
                        put("downloadedAt", entry.downloadedAt)
                    },
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, json.toString())
            .apply()
    }

    @Synchronized
    fun remove(context: Context, path: String) {
        val next = list(context).filterNot { it.path == path }
        val json = JSONArray().apply {
            next.forEach { entry ->
                put(
                    JSONObject().apply {
                        put("title", entry.title)
                        put("path", entry.path)
                        put("sourceUrl", entry.sourceUrl)
                        put("quality", entry.quality)
                        put("downloadedAt", entry.downloadedAt)
                    },
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, json.toString())
            .apply()
    }
}
