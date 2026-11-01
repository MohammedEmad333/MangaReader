package com.mangareader.app

import android.content.Context
import android.util.AtomicFile
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

/**
 * Namespace chapter caches by both source and series ID.
 * A series ID alone is not globally unique across installed extensions.
 */
internal fun chapterCacheScopeKey(sourceId: String, seriesId: String): String =
    offlineKey("${sourceId.length}:$sourceId${seriesId.length}:$seriesId")

/**
 * Old caches lack a source identifier. Only consider one if the library contains
 * exactly one matching series; ambiguous legacy IDs must not cross sources.
 */
internal fun canReadLegacyChapterCache(
    entries: List<LibraryEntry>,
    sourceId: String,
    seriesId: String,
): Boolean =
    sourceId.isNotBlank() && seriesId.isNotBlank() &&
        entries.count { it.seriesId == seriesId } == 1 &&
        entries.any { it.sourceId == sourceId && it.seriesId == seriesId }

/** The last known chapter list for a source-qualified series. */
object ChapterCache {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "chapterlists").apply { mkdirs() }

    private fun fileFor(context: Context, sourceId: String, seriesId: String): File =
        File(dir(context), "${chapterCacheScopeKey(sourceId, seriesId)}.json")

    private fun legacyFileFor(context: Context, seriesId: String): File =
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
        sourceId: String,
        seriesId: String,
        chapters: List<Chapter>
    ) {
        if (chapters.isEmpty()) return
        if (seriesId !in DownloadIndex.seriesIds(context)) return

        val downloaded = DownloadIndex.list(context)
            .firstOrNull { it.sourceId == sourceId && it.seriesId == seriesId }
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

    fun save(context: Context, sourceId: String, seriesId: String, chapters: List<Chapter>) {
        if (sourceId.isBlank() || seriesId.isBlank() || chapters.isEmpty()) return
        runCatching { reconcileDownloadAliases(context, sourceId, seriesId, chapters) }
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
            val atomic = AtomicFile(fileFor(context, sourceId, seriesId))
            val output = atomic.startWrite()
            try {
                output.write(arr.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
    }

    fun load(context: Context, sourceId: String, seriesId: String): List<Chapter> = runCatching {
        if (sourceId.isBlank() || seriesId.isBlank()) return emptyList()
        val scoped = fileFor(context, sourceId, seriesId)
        val file = when {
            scoped.exists() || File(scoped.path + ".bak").exists() -> scoped
            canReadLegacyChapterCache(Library.list(context), sourceId, seriesId) ->
                legacyFileFor(context, seriesId)
            else -> return emptyList()
        }
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        val arr = JSONArray(
            AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        )
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
