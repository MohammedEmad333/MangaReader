package com.mangareader.app

import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Persists one cover per downloaded series beside its chapter folders.
 *
 * The queue already carries the series cover URL/path with every chapter. The
 * first completed chapter stores the original bytes under the series folder and
 * later chapters simply reuse that file, so downloading ten chapters does not
 * download the same cover ten times.
 */
internal object DownloadCovers {

    private const val FILE_NAME = ".cover"

    /**
     * Ensures the series cover exists locally and returns the local absolute path.
     * Falls back to the original value when the cover cannot be saved; cover
     * failure must never turn a successful chapter download into a failed one.
     */
    fun ensure(context: Context, item: DownloadItem): String {
        if (item.cover.isBlank() || item.seriesId.isBlank()) return item.cover

        val relativeChapterPath = DownloadPaths.pathFor(context, item.chapterId)
            ?: return item.cover
        val chapterDir = File(Downloads.downloadsRoot(context), relativeChapterPath)
        val seriesDir = chapterDir.parentFile ?: return item.cover
        if (!seriesDir.exists() && !seriesDir.mkdirs()) return item.cover

        val target = File(seriesDir, FILE_NAME)
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
