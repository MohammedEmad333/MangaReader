package com.mangareader.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File

/** Queues the currently selected anime stream for offline download. */
internal object AnimeVideoDownload {
    fun enqueue(context: Context, video: PlayableVideo): Long? {
        val uri = runCatching { Uri.parse(video.url) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https")) {
            Toast.makeText(context, "This stream cannot be downloaded", Toast.LENGTH_SHORT).show()
            return null
        }

        if (looksLikeHls(video.url)) {
            val work = OneTimeWorkRequestBuilder<AnimeHlsDownloadWorker>()
                .setInputData(AnimeHlsDownloadWorker.input(video))
                .addTag("anime-hls-download")
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(work)
            Toast.makeText(context, "HLS download started", Toast.LENGTH_SHORT).show()
            return null
        }

        val title = video.episodeTitle.ifBlank { video.title.ifBlank { "Yomu episode" } }
        val fileName = safeFileName(title) + "." + extensionFor(video.url)
        val destination = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Yomu/Anime/$fileName",
        )
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription("Downloading anime episode")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Yomu/Anime/$fileName")

        video.headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) request.addRequestHeader(name, value)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching { manager.enqueue(request) }
            .onSuccess {
                AnimeOfflineIndex.record(
                    context,
                    AnimeOfflineItem(
                        title = title,
                        path = destination.absolutePath,
                        sourceUrl = video.url,
                        quality = video.title,
                        downloadedAt = System.currentTimeMillis(),
                    ),
                )
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
            }
            .getOrNull()
    }

    private fun looksLikeHls(url: String): Boolean =
        url.substringBefore('?').lowercase().endsWith(".m3u8")

    private fun extensionFor(url: String): String {
        val path = Uri.parse(url).lastPathSegment.orEmpty().substringBefore('?')
        val ext = MimeTypeMap.getFileExtensionFromUrl(path).lowercase()
        return ext.takeIf { it in setOf("mp4", "mkv", "webm", "m4v", "ts") } ?: "mp4"
    }

    private fun safeFileName(value: String): String =
        value.trim()
            .replace(Regex("[\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .take(96)
            .ifBlank { "Yomu episode" }
}
