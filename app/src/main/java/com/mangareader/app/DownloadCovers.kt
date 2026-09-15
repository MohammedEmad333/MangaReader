package com.mangareader.app

import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Persists one cover per downloaded series for offline use.
 *
 * Covers deliberately live under filesDir rather than inside the readable
 * <Source>/<Series> download tree. A cover file inside that tree would make a
 * series directory look non-empty after its last chapter is deleted, preventing
 * the existing empty-folder cleanup from removing it.
 */
internal object DownloadCovers {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "download_covers").apply { mkdirs() }

    private fun fileFor(context: Context, seriesId: String): File =
        File(dir(context), offlineKey(seriesId) + ".cover")

    /**
     * Ensures the series cover exists locally and returns the local absolute path.
     * Falls back to the original value when the cover cannot be saved; cover
     * failure must never turn a successful chapter download into a failed one.
     */
    fun ensure(context: Context, item: DownloadItem): String {
        if (item.cover.isBlank() || item.seriesId.isBlank()) return item.cover

        val target = fileFor(context, item.seriesId)
        if (target.isFile && target.length() > 0L) return target.absolutePath

        val source = File(item.cover)
        val saved = if (source.isFile) {
            runCatching {
                source.copyTo(target, overwrite = true)
                target.length() > 0L
            }.getOrDefault(false)
        } else {
            download(context, item.cover, target)
        }

        return if (saved) target.absolutePath else item.cover
    }

    fun delete(context: Context, seriesId: String) {
        if (seriesId.isBlank()) return
        runCatching { fileFor(context, seriesId).delete() }
    }

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }

    private fun download(context: Context, url: String, target: File): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false

        return runCatching {
            val client = Injekt.get<NetworkHelper>().client
                .newBuilder()
                .addInterceptor(CoverHeaders.interceptor(context.applicationContext))
                .build()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                val part = File(target.parentFile, target.name + ".part")
                runCatching { part.delete() }
                body.byteStream().use { input ->
                    part.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (part.length() <= 0L) {
                    part.delete()
                    false
                } else {
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) {
                        part.copyTo(target, overwrite = true)
                        part.delete()
                    }
                    target.isFile && target.length() > 0L
                }
            }
        }.getOrDefault(false)
    }
}
