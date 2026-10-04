package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class AnimeOfflineItem(
    val title: String,
    val path: String,
    val sourceUrl: String,
    val quality: String,
    val downloadedAt: Long,
)

/** Small persistent index for anime episodes downloaded by Yomu itself. */
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
            }
                .distinctBy { it.path }
                .sortedByDescending { it.downloadedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(context: Context, item: AnimeOfflineItem) {
        val next = (list(context).filterNot { it.path == item.path } + item)
            .sortedByDescending { it.downloadedAt }
        write(context, next)
    }

    @Synchronized
    fun remove(context: Context, path: String) {
        write(context, list(context).filterNot { it.path == path })
    }

    @Synchronized
    fun deleteFiles(context: Context, item: AnimeOfflineItem): Boolean {
        val file = File(item.path)
        val deleted = runCatching {
            when {
                !file.exists() -> true
                file.name.equals("offline.m3u8", ignoreCase = true) -> {
                    val parent = file.parentFile
                    parent?.deleteRecursively() ?: file.delete()
                }
                file.isDirectory -> file.deleteRecursively()
                else -> file.delete()
            }
        }.getOrDefault(false)

        if (deleted || !file.exists()) remove(context, item.path)
        return deleted
    }

    private fun write(context: Context, items: List<AnimeOfflineItem>) {
        val json = JSONArray().apply {
            items.forEach { entry ->
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
