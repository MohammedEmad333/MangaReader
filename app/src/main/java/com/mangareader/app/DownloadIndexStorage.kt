package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class DownloadIndexRecord(
    val chapterId: String,
    val chapterName: String,
    val sourceId: String,
    val seriesId: String,
    val title: String,
    val cover: String,
)

internal object DownloadIndexStorage {
    private const val FILE = "downloads_index.json"

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE)

    fun read(context: Context): List<DownloadIndexRecord> = runCatching {
        val target = file(context)
        if (!target.exists()) return emptyList()

        val arr = JSONArray(target.readText())
        (0 until arr.length()).map { index ->
            val obj = arr.getJSONObject(index)
            DownloadIndexRecord(
                chapterId = obj.getString("chapterId"),
                chapterName = obj.optString("chapterName"),
                sourceId = obj.optString("sourceId"),
                seriesId = obj.optString("seriesId"),
                title = obj.optString("title"),
                cover = obj.optString("cover"),
            )
        }
    }.getOrDefault(emptyList())

    fun write(
        context: Context,
        records: List<DownloadIndexRecord>,
    ) {
        runCatching {
            val arr = JSONArray()
            records.forEach { record ->
                arr.put(
                    JSONObject().apply {
                        put("chapterId", record.chapterId)
                        put("chapterName", record.chapterName)
                        put("sourceId", record.sourceId)
                        put("seriesId", record.seriesId)
                        put("title", record.title)
                        put("cover", record.cover)
                    },
                )
            }
            file(context).writeText(arr.toString())
        }
    }
}
