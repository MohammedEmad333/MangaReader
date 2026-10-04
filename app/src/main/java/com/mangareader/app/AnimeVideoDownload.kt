package com.mangareader.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.security.MessageDigest

/** Queues the currently selected anime stream for offline download. */
internal object AnimeVideoDownload {
    fun enqueue(context: Context, video: PlayableVideo): Long? {
        val uri = runCatching { Uri.parse(video.url) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https")) {
            Toast.makeText(context, "This stream cannot be downloaded", Toast.LENGTH_SHORT).show()
            return null
        }

        if (alreadyOffline(context, video.url)) {
            Toast.makeText(context, "Episode already downloaded", Toast.LENGTH_SHORT).show()
            return null
        }

        if (AnimeDirectDownloadIndex.list(context).any { it.sourceUrl == video.url }) {
            Toast.makeText(context, "Download already in progress", Toast.LENGTH_SHORT).show()
            return null
        }

        if (looksLikeHlsUrl(video.url)) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = OneTimeWorkRequestBuilder<AnimeHlsDownloadWorker>()
                .setInputData(AnimeHlsDownloadWorker.input(video))
                .setConstraints(constraints)
                .addTag(AnimeHlsDownloadWorker.TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueHlsWorkName(video.url),
                ExistingWorkPolicy.KEEP,
                work,
            )
            Toast.makeText(context, "HLS download queued", Toast.LENGTH_SHORT).show()
            return null
        }

        val title = video.episodeTitle.ifBlank { video.title.ifBlank { "Yomu episode" } }
        val extension = extensionFor(video.url)
        val destination = uniqueDirectDestination(context, title, video.title, extension)
        val relativePath = "Yomu/Anime/${destination.name}"
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription("Downloading anime episode")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, relativePath)

        video.headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) request.addRequestHeader(name, value)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching { manager.enqueue(request) }
            .onSuccess { id ->
                AnimeDirectDownloadIndex.record(
                    context,
                    PendingDirectAnimeDownload(
                        id = id,
                        title = title,
                        path = destination.absolutePath,
                        sourceUrl = video.url,
                        quality = video.title,
                        startedAt = System.currentTimeMillis(),
                    ),
                )
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
            }
            .getOrNull()
    }

    private fun alreadyOffline(context: Context, sourceUrl: String): Boolean =
        AnimeOfflineIndex.list(context).any { item ->
            item.sourceUrl == sourceUrl && File(item.path).exists()
        }

    private fun uniqueDirectDestination(
        context: Context,
        title: String,
        quality: String,
        extension: String,
    ): File {
        val root = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Yomu/Anime",
        )
        val reserved = buildSet {
            AnimeOfflineIndex.list(context).forEach { add(it.path) }
            AnimeDirectDownloadIndex.list(context).forEach { add(it.path) }
        }
        val base = safeFileName(title)
        val qualitySuffix = quality.trim()
            .takeIf { it.isNotBlank() && !title.contains(it, ignoreCase = true) }
            ?.let(::safeFileName)
            .orEmpty()

        fun candidate(stem: String) = File(root, "$stem.$extension")
        var file = candidate(base)
        if (!file.exists() && file.absolutePath !in reserved) return file

        if (qualitySuffix.isNotBlank()) {
            file = candidate("$base - $qualitySuffix")
            if (!file.exists() && file.absolutePath !in reserved) return file
        }

        var suffix = 2
        while (file.exists() || file.absolutePath in reserved) {
            file = candidate("$base ($suffix)")
            suffix++
        }
        return file
    }

    private fun uniqueHlsWorkName(url: String): String =
        "anime-hls-" + MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }

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

/** Conservative URL-only HLS detection for normal and signed manifest URLs. */
internal fun looksLikeHlsUrl(url: String): Boolean {
    val normalized = url.trim().lowercase()
    if (normalized.substringBefore('?').substringBefore('#').endsWith(".m3u8")) return true

    val query = normalized.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return false
    if (query.contains(".m3u8")) return true

    return query.split('&').any { part ->
        val key = part.substringBefore('=').trim()
        val value = part.substringAfter('=', "").trim()
        key in setOf("format", "type", "stream", "manifest", "playlist") &&
            value in setOf("hls", "m3u8", "application/vnd.apple.mpegurl", "application/x-mpegurl")
    }
}
