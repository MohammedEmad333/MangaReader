package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class DownloadQueueSnapshot(
    val items: List<DownloadItem>,
    val failed: List<FailedDownload>,
    val paused: Boolean,
    val pausedIds: Set<String>,
)

internal object DownloadQueueStore {
    private const val FILE = "download_queue.json"

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE)

    fun save(
        context: Context,
        items: List<DownloadItem>,
        failed: List<FailedDownload>,
        paused: Boolean,
        pausedIds: Set<String>,
    ) {
        runCatching {
            val queuedArr = JSONArray()
            items.forEach { queuedArr.put(it.toJson()) }

            val failedArr = JSONArray()
            failed.forEach { failedArr.put(it.toJson()) }

            val pausedArr = JSONArray()
            pausedIds.forEach { pausedArr.put(it) }

            file(context).writeText(
                JSONObject().apply {
                    put("paused", paused)
                    put("pausedIds", pausedArr)
                    put("items", queuedArr)
                    put("failed", failedArr)
                }.toString(),
            )
        }
    }

    fun restore(context: Context): DownloadQueueSnapshot? = runCatching {
        val file = file(context)
        if (!file.exists()) return@runCatching null

        val root = JSONObject(file.readText())
        val queued = root.optJSONArray("items")
            ?.let { arr ->
                (0 until arr.length())
                    .map { DownloadItem.fromJson(arr.getJSONObject(it)) }
                    .filterNot { Downloads.isComplete(context, it.chapterId) }
            }
            .orEmpty()

        val queuedIds = queued.map { it.chapterId }.toSet()
        val pausedItems = root.optJSONArray("pausedIds")
            ?.let { arr ->
                (0 until arr.length())
                    .map { arr.getString(it) }
                    .toSet()
            }
            .orEmpty()
            .intersect(queuedIds)

        val failures = root.optJSONArray("failed")
            ?.let { arr ->
                (0 until arr.length())
                    .map { FailedDownload.fromJson(arr.getJSONObject(it)) }
                    .filterNot { Downloads.isComplete(context, it.item.chapterId) }
            }
            .orEmpty()

        DownloadQueueSnapshot(
            items = queued,
            failed = failures,
            paused = root.optBoolean("paused", false),
            pausedIds = pausedItems,
        )
    }.getOrNull()
}
