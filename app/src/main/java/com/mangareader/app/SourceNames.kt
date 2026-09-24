package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * What each source id is called.
 *
 * `LibraryEntry`, `DownloadItem` and the refresh's failure tally all store a
 * `sourceId` and never a name, because the name belongs to the extension and the
 * extension may not be installed when the record is read. The id alone is
 * unreadable — `tachi:6202325652827735606` is a real string a user was shown
 * when a source failed twelve times, and it names nothing.
 *
 * The obvious fix is to ask `SourceManager.listAllSources()`, and that is exactly
 * what cannot be done from the places that need it: it classloads extension APKs,
 * so it is not something to call from composition or from a settings row. This is
 * the inverse of a one-way lookup, answered the way §5 says to answer those — **a
 * record written at the point the information exists**, which is whenever sources
 * are listed for some other reason.
 *
 * Deliberately never pruned. An uninstalled extension's sources vanish from
 * `listAllSources`, and that is the moment their names become most valuable: a
 * library entry from a source you removed should still say what it was, not
 * revert to an id.
 */
object SourceNames {
    private const val KEY = "source_names"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    @Volatile
    private var cached: Map<String, String>? = null

    /**
     * Bumped whenever a name is learned, so a screen already on display can
     * notice.
     *
     * Compose state in a process-wide object, the same shape `DownloadQueue`
     * uses and for the same reason: the writer is a background coroutine and the
     * reader is a composable in the same process, so there is nothing to plumb.
     *
     * This is load-bearing rather than a nicety. Names are recorded from
     * `SourceManager.listAllSources`, which runs on `ON_RESUME` **on
     * `Dispatchers.IO`** — so on a cold start the Library tab has already
     * composed and grouped itself before a single name exists. Without a key
     * that moves, the source tabs render as raw ids until something unrelated
     * invalidates them.
     */
    var version by mutableIntStateOf(0)
        private set

    fun all(context: Context): Map<String, String> {
        cached?.let { return it }
        val parsed = runCatching {
            val raw = prefs(context).getString(KEY, null) ?: return@runCatching emptyMap()
            val o = JSONObject(raw)
            buildMap {
                for (key in o.keys()) put(key, o.optString(key))
            }
        }.getOrDefault(emptyMap())
        cached = parsed
        return parsed
    }

    /** The source's name, or [unnamed] when nothing has ever recorded one. */
    fun nameOf(context: Context, sourceId: String): String =
        all(context)[sourceId]?.takeIf { it.isNotBlank() } ?: unnamed(sourceId)

    /**
     * What to call a source no map has a name for.
     *
     * Happens for a source whose extension was uninstalled before its name was
     * ever recorded — nothing will list it again, so nothing will ever learn it
     * except the repo index. Nineteen digits is not a label, so this keeps the
     * tail, which is enough to tell two of them apart and short enough to read
     * on a tab.
     */
    fun unnamed(sourceId: String): String {
        val suffix = when {
            sourceId.isMangaExtensionSourceId() -> sourceId.removePrefix("tachi:")
            sourceId.isAnimeExtensionSourceId() -> sourceId.removePrefix("aniyomi:")
            else -> sourceId
        }
        return if (suffix.length <= 6) "Unknown ($suffix)"
        else "Unknown (\u2026${suffix.takeLast(6)})"
    }

    /**
     * Records names for a batch of sources.
     *
     * Called from `SourceManager.listAllSources`, which runs often — on the
     * Browse tab, on every global search, from a lifecycle observer. So the
     * common path has to be free: a batch that says nothing new is refused
     * before any JSON is built, which makes this a write on install, uninstall
     * and rename, and nothing else.
     */
    fun record(context: Context, names: Map<String, String>) {
        if (names.isEmpty()) return
        val current = all(context)
        val fresh = names.filter { (id, name) ->
            name.isNotBlank() && current[id] != name
        }
        if (fresh.isEmpty()) return

        val merged = current + fresh
        cached = merged
        version++
        runCatching {
            val o = JSONObject()
            merged.forEach { (id, name) -> o.put(id, name) }
            prefs(context).edit().putString(KEY, o.toString()).apply()
        }
    }
}
