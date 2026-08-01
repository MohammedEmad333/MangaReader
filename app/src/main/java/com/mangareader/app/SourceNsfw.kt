package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Which source ids are 18+.
 *
 * **Why this exists at all.** `LibraryEntry` stores `seriesId`, `sourceId`,
 * `title`, `cover` and `addedAt` — nothing about content. So the only thing a
 * saved series can be classified by is the source it came from, and asking the
 * source directly means `SourceManager.listAllSources()`, which classloads
 * extension APKs and is exactly what §4 says not to do from composition. This is
 * `SourceNames` again, one field over: **a record written at the point the
 * information exists**, read later by a screen that cannot afford to ask.
 *
 * §4 has listed *Lewd* since 0.65 as blocked on a data-model decision rather
 * than an index. This is that decision, and it is deliberately the narrow one —
 * see the scope note below before extending it.
 *
 * **A map, not a set of the 18+ ones.** A set collapses "known safe" into
 * "never seen", and this store has the three states §5 keeps finding: 18+,
 * known-not-18+, and never-recorded. Only a map can record a source that
 * *stops* being flagged, which happens when an extension is re-published with a
 * corrected manifest.
 *
 * **Scope, stated because a filter that doesn't state its scope fails
 * silently.** This classifies a *source*, not a series. A mixed-content source
 * marks everything saved from it. Series-level classification would need
 * something stored per entry — SY reads genre tags for this — and `LibraryEntry`
 * has nowhere to put it, so that remains the data-model decision this one
 * sidesteps.
 *
 * Never pruned, for `SourceNames`' reason: an uninstalled extension's sources
 * vanish from `listAllSources`, and that is precisely when a library entry from
 * one still needs classifying.
 */
object SourceNsfw {
    private const val KEY = "source_nsfw"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    @Volatile
    private var cached: Map<String, Boolean>? = null

    /**
     * Bumped when a flag is learned, so a screen already drawn can notice.
     *
     * Load-bearing for the same reason `SourceNames.version` is: the recording
     * happens inside `listAllSources` on `Dispatchers.IO`, which on a cold start
     * finishes *after* the Library tab has composed and filtered itself.
     */
    var version by mutableIntStateOf(0)
        private set

    fun all(context: Context): Map<String, Boolean> {
        cached?.let { return it }
        val parsed = runCatching {
            val raw = prefs(context).getString(KEY, null) ?: return@runCatching emptyMap()
            val o = JSONObject(raw)
            buildMap {
                for (key in o.keys()) put(key, o.optBoolean(key))
            }
        }.getOrDefault(emptyMap())
        cached = parsed
        return parsed
    }

    /**
     * Null when nothing has ever classified this source — **not** false.
     *
     * Callers must decide what an unknown source means for them rather than
     * taking `?: false` without thinking about it. The library filter treats
     * unknown as "the condition doesn't hold", which hides it under Include and
     * keeps it under Exclude — the same rule every `SeriesIndex` consumer
     * follows, and the one that fails towards showing a series rather than
     * making one disappear.
     */
    fun isNsfw(context: Context, sourceId: String): Boolean? = all(context)[sourceId]

    /**
     * Records flags for a batch of sources.
     *
     * Called from `SourceManager.listAllSources`, which runs on the Browse tab,
     * on every global search and from a lifecycle observer — so a batch that
     * says nothing new is refused before any JSON is built. That makes this a
     * write on install, uninstall and re-publish, and nothing else.
     */
    fun record(context: Context, flags: Map<String, Boolean>) {
        if (flags.isEmpty()) return
        val current = all(context)
        val fresh = flags.filter { (id, value) -> current[id] != value }
        if (fresh.isEmpty()) return

        val merged = current + fresh
        cached = merged
        version++
        runCatching {
            val o = JSONObject()
            merged.forEach { (id, value) -> o.put(id, value) }
            prefs(context).edit().putString(KEY, o.toString()).apply()
        }
    }
}
