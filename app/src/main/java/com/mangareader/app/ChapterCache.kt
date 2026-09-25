package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Persistent aliases from the chapter id a source returns now to the id that
 * was used when the chapter was downloaded.
 *
 * Extension updates sometimes change a chapter URL/id while leaving the chapter
 * itself unchanged. The download index can still list the old id, but the series
 * screen asks Downloads about the new id. Keeping that relationship here lets
 * every existing Downloads API keep using the current id while resolving the
 * already-downloaded directory underneath it.
 */
internal fun offlineKey(id: String): String =
    MessageDigest.getInstance("MD5")
        .digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }

/** The last known chapter list for a series, so it can be opened without network. */
object ChapterCache {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "chapterlists").apply { mkdirs() }

    private fun fileFor(context: Context, seriesId: String): File =
        File(dir(context), "${offlineKey(seriesId)}.json")

    /** A conservative identity used only to reconnect downloads after an id change. */
    private fun normalizedName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * Reconnects downloads whose source changed only the chapter id/URL.
     *
     * DownloadIndex still knows the old downloaded id and chapter name. We only
     * create an alias when the normalized name is unique on both sides, so two
     * same-named releases/scanlations are never guessed between. This runs only
     * for a series the download index says has a completed download.
     */
    private fun reconcileDownloadAliases(
        context: Context,
        seriesId: String,
        chapters: List<Chapter>
    ) {
        if (chapters.isEmpty()) return
        if (seriesId !in DownloadIndex.seriesIds(context)) return

        val downloaded = DownloadIndex.list(context)
            .firstOrNull { it.seriesId == seriesId }
            ?.chapters
            .orEmpty()
        if (downloaded.isEmpty()) return

        val currentByName = chapters
            .groupBy { normalizedName(it.name) }
            .filterValues { it.size == 1 }
        val oldByName = downloaded
            .groupBy { normalizedName(it.name) }
            .filterValues { it.size == 1 }

        var changed = false
        for ((name, currentMatches) in currentByName) {
            if (name.isBlank()) continue
            val old = oldByName[name]?.singleOrNull() ?: continue
            val current = currentMatches.single()
            if (old.chapterId == current.id) continue
            if (!Downloads.isComplete(context, old.chapterId)) continue
            if (Downloads.isComplete(context, current.id)) continue
            changed = DownloadAliases.put(context, current.id, old.chapterId) || changed
        }

        // The current ids may already have been memoised as not downloaded above.
        if (changed) Downloads.invalidateCompletion()
    }

    fun save(context: Context, seriesId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        runCatching { reconcileDownloadAliases(context, seriesId, chapters) }
        runCatching {
            val arr = JSONArray()
            chapters.forEach { ch ->
                arr.put(
                    JSONObject().apply {
                        put("id", ch.id)
                        put("name", ch.name)
                        put("date", ch.dateUploaded)
                        put("scanlator", ch.scanlator ?: "")
                        put("number", ch.number.toDouble())
                    }
                )
            }
            fileFor(context, seriesId).writeText(arr.toString())
        }
    }

    fun load(context: Context, seriesId: String): List<Chapter> = runCatching {
        val file = fileFor(context, seriesId)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Chapter(
                id = o.getString("id"),
                name = o.optString("name"),
                handle = null,
                dateUploaded = o.optLong("date", 0L),
                scanlator = o.optString("scanlator").takeIf { it.isNotBlank() },
                number = ChapterRecognition.parse(
                    seriesTitle = "",
                    chapterName = o.optString("name"),
                    fromSource = o.optDouble("number", Chapter.NO_NUMBER.toDouble()).toFloat()
                )
            )
        }
    }.getOrDefault(emptyList())

    fun clearAll(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
