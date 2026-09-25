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
internal object DownloadAliases {
    private const val FILE = "download_id_aliases.json"
    private var aliases = mutableMapOf<String, String>()
    private var loaded = false

    private fun file(context: Context) =
        File(context.applicationContext.filesDir, FILE)

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (!f.exists()) return
        runCatching {
            val json = JSONObject(f.readText())
            val restored = mutableMapOf<String, String>()
            for (key in json.keys()) {
                val value = json.optString(key)
                if (key.isNotBlank() && value.isNotBlank() && key != value) {
                    restored[key] = value
                }
            }
            aliases = restored
        }
    }

    @Synchronized
    fun resolve(context: Context, chapterId: String): String {
        load(context)
        var current = chapterId
        val seen = mutableSetOf<String>()
        while (seen.add(current)) {
            val next = aliases[current] ?: break
            current = next
        }
        return current
    }

    @Synchronized
    fun put(context: Context, currentId: String, downloadedId: String): Boolean {
        load(context)
        if (currentId.isBlank() || downloadedId.isBlank()) return false
        val target = resolve(context, downloadedId)
        if (currentId == target || aliases[currentId] == target) return false
        aliases[currentId] = target
        save(context)
        return true
    }

    /** Removes every alias that points at the same stored chapter. */
    @Synchronized
    fun forget(context: Context, chapterId: String) {
        forgetMany(context, listOf(chapterId))
    }

    /**
     * Removes aliases for many stored chapters with one persistence write.
     *
     * Resolve against a snapshot before mutating the map. That also makes alias
     * chains deterministic: deleting A removes every key whose chain ultimately
     * lands on A's stored chapter, regardless of iteration order.
     */
    @Synchronized
    fun forgetMany(context: Context, chapterIds: Collection<String>) {
        if (chapterIds.isEmpty()) return
        load(context)

        val snapshot = aliases.toMap()
        fun resolveSnapshot(chapterId: String): String {
            var current = chapterId
            val seen = mutableSetOf<String>()
            while (seen.add(current)) {
                val next = snapshot[current] ?: break
                current = next
            }
            return current
        }

        val targets = chapterIds.mapTo(mutableSetOf(), ::resolveSnapshot)
        val removed = aliases.entries.removeAll { (key, _) ->
            resolveSnapshot(key) in targets
        }
        if (removed) save(context)
    }

    @Synchronized
    fun clear(context: Context) {
        aliases.clear()
        loaded = true
        runCatching { file(context).delete() }
    }

    private fun save(context: Context) {
        runCatching {
            val json = JSONObject()
            aliases.forEach { (key, value) -> json.put(key, value) }
            file(context).writeText(json.toString())
        }
    }
}

/** Chapters kept on disk for offline reading. */
