package com.mangareader.app

import android.content.Context
import android.util.AtomicFile
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

            val payload = JSONObject().apply {
                put("paused", paused)
                put("pausedIds", pausedArr)
                put("items", queuedArr)
                put("failed", failedArr)
            }.toString().toByteArray(Charsets.UTF_8)

            // A process kill must never leave a half-written JSON queue.
            // AtomicFile also recovers a backup on the next openRead().
            val atomic = AtomicFile(file(context))
            val output = atomic.startWrite()
            try {
                output.write(payload)
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
    }

    fun restore(context: Context): DownloadQueueSnapshot? = runCatching {
        val atomic = AtomicFile(file(context))
        val target = file(context)
        if (!target.exists() && !File(target.path + ".bak").exists() &&
            !File(target.path + ".new").exists()
        ) return@runCatching null

        val root = JSONObject(atomic.openRead().bufferedReader(Charsets.UTF_8).use {
            it.readText()
        })
        val queued = root.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).map { DownloadItem.fromJson(arr.getJSONObject(it)) }
        }.orEmpty()
        val failures = root.optJSONArray("failed")?.let { arr ->
            (0 until arr.length()).map { FailedDownload.fromJson(arr.getJSONObject(it)) }
        }.orEmpty()
        val pausedItems = root.optJSONArray("pausedIds")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.orEmpty()

        normalizeRestoredDownloadQueue(
            items = queued,
            failed = failures,
            paused = root.optBoolean("paused", false),
            pausedIds = pausedItems,
            isComplete = { Downloads.isComplete(context, it) },
        )
    }.getOrNull()
}
