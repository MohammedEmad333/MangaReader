package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingDirectAnimeDownload(
    val id: Long,
    val title: String,
    val path: String,
    val sourceUrl: String,
    val quality: String,
    val startedAt: Long,
    val headers: Map<String, String> = emptyMap(),
    val retryCount: Int = 0,
)

/** Persistent metadata for direct-file downloads delegated to Android DownloadManager. */
internal object AnimeDirectDownloadIndex {
    private const val PREFS = "anime_direct_downloads"
    private const val KEY_ITEMS = "items"

    fun list(context: Context): List<PendingDirectAnimeDownload> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optLong("id", -1L)
                    val path = item.optString("path")
                    if (id < 0L || path.isBlank()) continue
                    add(
                        PendingDirectAnimeDownload(
                            id = id,
                            title = item.optString("title", "Yomu episode"),
                            path = path,
                            sourceUrl = item.optString("sourceUrl"),
                            quality = item.optString("quality"),
                            startedAt = item.optLong("startedAt"),
                            headers = item.optJSONObject("headers").toStringMap(),
                            retryCount = item.optInt("retryCount", 0).coerceAtLeast(0),
                        ),
                    )
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(context: Context, item: PendingDirectAnimeDownload) {
        write(context, (list(context).filterNot { it.id == item.id } + item).sortedByDescending { it.startedAt })
    }

    @Synchronized
    fun replace(context: Context, oldId: Long, item: PendingDirectAnimeDownload) {
        write(
            context,
            (list(context).filterNot { it.id == oldId || it.id == item.id } + item)
                .sortedByDescending { it.startedAt },
        )
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        write(context, list(context).filterNot { it.id == id })
    }

    private fun write(context: Context, items: List<PendingDirectAnimeDownload>) {
        val array = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("path", item.path)
                        put("sourceUrl", item.sourceUrl)
                        put("quality", item.quality)
                        put("startedAt", item.startedAt)
                        put("retryCount", item.retryCount)
                        put(
                            "headers",
                            JSONObject().apply {
                                item.headers.forEach { (name, value) -> put(name, value) }
                            },
                        )
                    },
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = optString(key)
            if (key.isNotBlank() && value.isNotBlank()) put(key, value)
        }
    }
}
