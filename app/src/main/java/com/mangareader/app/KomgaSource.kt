package com.mangareader.app

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Source backed by a self-hosted Komga server (https://komga.org).
 * Proves the Source seam: same interface as LocalSource, completely
 * different backend (REST + Basic auth instead of SAF + zip).
 */
class KomgaSource(
    override val id: String,
    baseUrl: String,
    user: String,
    pass: String,
    private val cacheDir: File
) : Source {

    override val name = "Komga"

    private val base = baseUrl.trimEnd('/')
    private val auth = Credentials.basic(user, pass)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getText(path: String): String {
        val req = Request.Builder()
            .url(base + path)
            .header("Authorization", auth)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Komga error " + resp.code + " — check URL/login")
            }
            return resp.body?.string() ?: ""
        }
    }

    private fun downloadTo(path: String, out: File): Boolean {
        val req = Request.Builder()
            .url(base + path)
            .header("Authorization", auth)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            resp.body?.byteStream()?.use { ins ->
                out.outputStream().use { o -> ins.copyTo(o) }
            } ?: return false
        }
        return true
    }

    private fun cachedThumb(path: String, key: String): File? {
        return try {
            val dir = File(cacheDir, "covers")
            dir.mkdirs()
            val f = File(dir, "komga_" + key.hashCode().toString() + ".jpg")
            if (f.exists()) return f
            if (downloadTo(path, f)) f else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun listSeries(): List<Series> {
        val json = getText("/api/v1/series?size=200&sort=metadata.titleSort,asc")
        val content = JSONObject(json).getJSONArray("content")
        val out = mutableListOf<Series>()
        for (i in 0 until content.length()) {
            val s = content.getJSONObject(i)
            val id = s.getString("id")
            val meta = s.optJSONObject("metadata")
            val title = meta?.optString("title", "") ?: ""
            out.add(
                Series(
                    id = "komga:" + id,
                    title = if (title.isNotBlank()) title else s.optString("name", "series"),
                    cover = cachedThumb("/api/v1/series/" + id + "/thumbnail", "series_" + id),
                    handle = id
                )
            )
        }
        return out
    }

    override suspend fun listChapters(series: Series): List<Chapter> {
        val id = series.handle as? String ?: return emptyList()
        val json = getText("/api/v1/series/" + id + "/books?size=500&sort=metadata.numberSort,asc")
        val content = JSONObject(json).getJSONArray("content")
        val out = mutableListOf<Chapter>()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            val bid = b.getString("id")
            val meta = b.optJSONObject("metadata")
            val title = meta?.optString("title", "") ?: ""
            out.add(
                Chapter(
                    id = "komga:" + bid,
                    name = if (title.isNotBlank()) title else b.optString("name", "chapter"),
                    handle = bid
                )
            )
        }
        return out
    }

    override suspend fun loadPages(chapter: Chapter): List<File> {
        val bid = chapter.handle as? String
            ?: throw IllegalStateException("Bad Komga chapter")
        val arr = JSONArray(getText("/api/v1/books/" + bid + "/pages"))

        val pagesDir = File(cacheDir, "current_book")
        pagesDir.deleteRecursively()
        pagesDir.mkdirs()

        val out = mutableListOf<File>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val num = p.getInt("number")
            val f = File(pagesDir, "page_" + num.toString().padStart(4, '0') + ".img")
            if (!downloadTo("/api/v1/books/" + bid + "/pages/" + num, f)) {
                throw IllegalStateException("Failed to download page " + num)
            }
            out.add(f)
        }
        if (out.isEmpty()) throw IllegalStateException("Chapter has no pages")
        return out.sortedBy { it.name }
    }
}
