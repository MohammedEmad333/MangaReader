package com.mangareader.app

import org.json.JSONObject

data class DownloadItem(
    val sourceId: String,
    val chapterId: String,
    val chapterName: String,
    val seriesTitle: String,
    val seriesId: String = "",
    val cover: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sourceId", sourceId)
        put("chapterId", chapterId)
        put("chapterName", chapterName)
        put("seriesTitle", seriesTitle)
        put("seriesId", seriesId)
        put("cover", cover)
    }

    companion object {
        fun fromJson(o: JSONObject) = DownloadItem(
            sourceId = o.getString("sourceId"),
            chapterId = o.getString("chapterId"),
            chapterName = o.optString("chapterName"),
            seriesTitle = o.optString("seriesTitle"),
            seriesId = o.optString("seriesId"),
            cover = o.optString("cover"),
        )
    }
}

data class FailedDownload(
    val item: DownloadItem,
    val reason: String,
) {
    fun toJson(): JSONObject = item.toJson().apply {
        put("reason", reason)
    }

    companion object {
        fun fromJson(o: JSONObject) = FailedDownload(
            item = DownloadItem.fromJson(o),
            reason = o.optString("reason").ifBlank { "Download failed" },
        )
    }
}
