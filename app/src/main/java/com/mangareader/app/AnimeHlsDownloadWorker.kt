package com.mangareader.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Downloads an HLS episode as a real offline package (playlist + segments + keys/maps). */
internal class AnimeHlsDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Yomu episode" }
        val quality = inputData.getString(KEY_QUALITY).orEmpty()
        val headers = decodeHeaders(inputData.getString(KEY_HEADERS).orEmpty())
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext Result.failure()

        var episodeDir: File? = null
        try {
            reportProgress(title, quality, "Preparing", 0)
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            episodeDir = createEpisodeDirectory(applicationContext, title)

            reportProgress(title, quality, "Reading playlist", 1)
            val rootText = fetchText(client, url, headers)
            requireHlsPlaylist(rootText)
            val (mediaUrl, mediaText) = if (isMasterPlaylist(rootText)) {
                val variant = chooseVariant(url, rootText, quality)
                    ?: error("HLS master playlist has no playable variants")
                val variantText = fetchText(client, variant, headers)
                requireHlsPlaylist(variantText)
                variant to variantText
            } else {
                url to rootText
            }

            val rewritten = downloadMediaPlaylist(
                client = client,
                playlistUrl = mediaUrl,
                playlist = mediaText,
                headers = headers,
                outputDir = episodeDir,
                title = title,
                quality = quality,
            )
            currentCoroutineContext().ensureActive()

            val localPlaylist = File(episodeDir, LOCAL_PLAYLIST)
            localPlaylist.writeText(rewritten)
            File(episodeDir, ".yomu-title").writeText(title)
            File(episodeDir, ".yomu-source").writeText(url)
            AnimeOfflineIndex.record(
                applicationContext,
                AnimeOfflineItem(
                    title = title,
                    path = localPlaylist.absolutePath,
                    sourceUrl = url,
                    quality = quality,
                    downloadedAt = System.currentTimeMillis(),
                ),
            )
            reportProgress(title, quality, "Complete", 100)
            Result.success(workDataOf(KEY_OUTPUT_PATH to localPlaylist.absolutePath))
        } catch (cancelled: CancellationException) {
            episodeDir?.deleteRecursively()
            throw cancelled
        } catch (error: Throwable) {
            episodeDir?.deleteRecursively()
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "HLS download failed")))
        }
    }

    private suspend fun downloadMediaPlaylist(
        client: OkHttpClient,
        playlistUrl: String,
        playlist: String,
        headers: Map<String, String>,
        outputDir: File,
        title: String,
        quality: String,
    ): String {
        val lines = playlist.lines()
        val mediaUris = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        check(mediaUris.isNotEmpty()) { "HLS playlist has no media segments" }
        val total = mediaUris.size
        var completed = 0
        val localNames = linkedMapOf<String, String>()

        fun localName(remote: String, hint: String): String = localNames.getOrPut(remote) {
            val path = runCatching { URI(remote).path }.getOrNull().orEmpty()
            val ext = path.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.let { ".$it" }.orEmpty()
            hint + "-" + sha1(remote).take(12) + ext
        }

        suspend fun ensureDownloaded(rawUri: String, hint: String): String {
            currentCoroutineContext().ensureActive()
            val absolute = resolveUrl(playlistUrl, rawUri)
            val name = localName(absolute, hint)
            val file = File(outputDir, name)
            if (!file.exists() || file.length() == 0L) {
                fetchBytes(client, absolute, headers, file)
            }
            return name
        }

        val rewritten = mutableListOf<String>()
        for (line in lines) {
            currentCoroutineContext().ensureActive()
            when {
                line.isBlank() -> rewritten += line
                line.startsWith("#EXT-X-KEY:") -> {
                    rewritten += rewriteUriAttribute(line) { raw -> ensureDownloaded(raw, "key") }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    rewritten += rewriteUriAttribute(line) { raw -> ensureDownloaded(raw, "init") }
                }
                line.startsWith("#") -> rewritten += line
                else -> {
                    rewritten += ensureDownloaded(line.trim(), "seg")
                    completed++
                    val percent = ((completed * 98f) / total).toInt().coerceIn(2, 99)
                    reportProgress(title, quality, "Downloading segments", percent)
                }
            }
        }
        return rewritten.joinToString("\n")
    }

    private suspend fun reportProgress(title: String, quality: String, stage: String, percent: Int) {
        setProgress(progressData(title, quality, stage, percent))
        setForeground(foregroundInfo(title, quality, stage, percent))
    }

    private fun foregroundInfo(title: String, quality: String, stage: String, percent: Int): ForegroundInfo {
        ensureNotificationChannel()
        val detail = buildString {
            append(stage)
            if (quality.isNotBlank()) append(" • ").append(quality)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setOnlyAlertOnce(true)
            .setOngoing(percent < 100)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 1)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID_BASE + (id.hashCode() and 0x0FFF),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Anime downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress for offline anime downloads"
                setShowBadge(false)
            },
        )
    }

    private suspend fun rewriteUriAttribute(
        line: String,
        download: suspend (String) -> String,
    ): String {
        val match = URI_ATTRIBUTE.find(line) ?: return line
        val local = download(match.groupValues[1])
        return line.replaceRange(match.range, "URI=\"$local\"")
    }

    private fun chooseVariant(masterUrl: String, playlist: String, quality: String): String? {
        val wantedHeight = Regex("(\\d{3,4})p?", RegexOption.IGNORE_CASE)
            .find(quality)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val lines = playlist.lines()
        val variants = mutableListOf<Variant>()
        var info: String? = null
        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                info = line
            } else if (info != null && line.isNotBlank() && !line.startsWith("#")) {
                val streamInfo = info ?: continue
                val height = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)
                    .find(streamInfo)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(streamInfo)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                variants += Variant(resolveUrl(masterUrl, line.trim()), height, bandwidth)
                info = null
            }
        }
        if (variants.isEmpty()) return null
        return if (wantedHeight != null) {
            variants.minByOrNull { kotlin.math.abs((it.height ?: wantedHeight) - wantedHeight) }?.url
        } else {
            variants.maxByOrNull { it.bandwidth }?.url
        }
    }

    private fun fetchText(client: OkHttpClient, url: String, headers: Map<String, String>): String {
        client.newCall(request(url, headers)).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} while fetching playlist")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html")) error("Server returned HTML instead of an HLS playlist")
            return response.body.string()
        }
    }

    private fun fetchBytes(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        destination: File,
    ) {
        client.newCall(request(url, headers)).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} while downloading segment")
            val temp = File(destination.parentFile, destination.name + ".part")
            temp.outputStream().use { output -> response.body.byteStream().copyTo(output) }
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun request(url: String, headers: Map<String, String>): Request =
        Request.Builder().url(url).apply {
            headers.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) header(name, value)
            }
        }.build()

    private fun requireHlsPlaylist(text: String) {
        val normalized = text.trimStart { it == '\uFEFF' || it.isWhitespace() }
        check(normalized.startsWith("#EXTM3U")) { "Server did not return a valid HLS playlist" }
    }

    private fun isMasterPlaylist(text: String): Boolean = text.contains("#EXT-X-STREAM-INF:")

    private fun resolveUrl(base: String, child: String): String = URI(base).resolve(child).toString()

    private fun createEpisodeDirectory(context: Context, title: String): File {
        val publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val canWritePublic = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        val root = if (canWritePublic) {
            File(publicRoot, "Yomu/Anime")
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Yomu/Anime")
        }
        val safe = title.trim()
            .replace(Regex("[\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .take(96)
            .ifBlank { "Yomu episode" }
        var dir = File(root, safe)
        var suffix = 2
        while (dir.exists() && File(dir, LOCAL_PLAYLIST).exists()) {
            dir = File(root, "$safe ($suffix)")
            suffix++
        }
        check(dir.mkdirs() || dir.isDirectory) { "Could not create anime download folder" }
        return dir
    }

    private fun progressData(title: String, quality: String, stage: String, percent: Int): Data =
        workDataOf(
            KEY_TITLE to title,
            KEY_QUALITY to quality,
            KEY_STAGE to stage,
            KEY_PERCENT to percent.coerceIn(0, 100),
        )

    private fun sha1(value: String): String = MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private data class Variant(val url: String, val height: Int?, val bandwidth: Long)

    companion object {
        const val TAG = "anime-hls-download"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_QUALITY = "quality"
        const val KEY_HEADERS = "headers"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR = "error"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        private const val LOCAL_PLAYLIST = "offline.m3u8"
        private const val CHANNEL_ID = "anime_hls_downloads"
        private const val NOTIFICATION_ID_BASE = 4700
        private val URI_ATTRIBUTE = Regex("URI=\\\"([^\\\"]+)\\\"")

        fun input(video: PlayableVideo): Data = workDataOf(
            KEY_URL to video.url,
            KEY_TITLE to video.episodeTitle.ifBlank { video.title.ifBlank { "Yomu episode" } },
            KEY_QUALITY to video.title,
            KEY_HEADERS to JSONObject(video.headers).toString(),
        )

        private fun decodeHeaders(raw: String): Map<String, String> = runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }.getOrDefault(emptyMap())
    }
}
