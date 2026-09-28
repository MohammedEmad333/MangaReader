package com.mangareader.app

import android.content.Context
import org.json.JSONArray

/**
 * Per-source UI state for the Sources list: which sources are pinned, and which
 * one was opened most recently.
 *
 * Both are keyed by `Source.id`, which for extensions is "tachi:<sourceId>" and
 * stays stable across reinstalls of the same extension. That means a pin
 * survives an extension update, and an uninstalled source's pin simply stops
 * matching anything rather than corrupting the list.
 *
 * Stored in the same SharedPreferences file as everything else in the app.
 */
object SourcePrefs {

    private const val KEY_PINNED = "pinned_sources"
    private const val KEY_LAST_USED = "last_used_source"
    private const val KEY_PINNED_ONLY_SEARCH = "global_search_pinned_only"
    private const val KEY_HIDDEN = "hidden_sources"
    private const val KEY_ENABLED_LANGS = "enabled_langs"
    private const val KEY_SHOW_NSFW = "show_nsfw"
    private const val KEY_RECENT_SEARCHES = "global_search_recents"
    private const val KEY_GLOBAL_SEARCH_MEDIA = "global_search_media_filter"
    private const val KEY_SOURCES_MEDIA = "sources_media_filter"
    private const val KEY_BROWSE_TAB = "browse_tab"
    private const val KEY_SOURCES_QUERY = "sources_query"
    private const val KEY_SOURCES_PINNED_ONLY = "sources_pinned_only"

    /** How many past global-search queries are remembered. */
    private const val RECENT_SEARCHES_MAX = 12

    /**
     * Languages shown before anyone chooses. 95 sources across 30-odd languages
     * is unusable as a default, and almost none of them are readable by any one
     * person. "Local" is in here because it isn't really a language — it's the
     * local folder group, and hiding that by default would be baffling.
     */
    val DEFAULT_LANGS = setOf("Local", "Multi", "English")

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    /**
     * Read with a defensive copy. getStringSet's docs are explicit that the
     * returned set must not be mutated, and that the instance may be shared —
     * toSet() here keeps a stale reference from ever leaking back in.
     */
    fun pinned(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()

    fun isPinned(context: Context, id: String): Boolean = id in pinned(context)

    /** Flips the pin and returns the new full set, so callers can update state. */
    fun togglePin(context: Context, id: String): Set<String> {
        val next = pinned(context).toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs(context).edit().putStringSet(KEY_PINNED, next).apply()
        return next
    }

    /** Id of the source opened most recently, or null on a fresh install. */
    fun lastUsed(context: Context): String? =
        prefs(context).getString(KEY_LAST_USED, null)?.takeIf { it.isNotBlank() }

    fun setLastUsed(context: Context, id: String) {
        prefs(context).edit().putString(KEY_LAST_USED, id).apply()
    }

    /**
     * Whether global search should fan out to pinned sources only.
     *
     * Defaults to true, which is safe even before anything is pinned: the search
     * falls back to every searchable source when the pinned subset is empty, so
     * a fresh install behaves exactly as it did before.
     */
    fun pinnedOnlySearch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PINNED_ONLY_SEARCH, true)

    fun setPinnedOnlySearch(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PINNED_ONLY_SEARCH, value).apply()
    }

    fun globalSearchMediaFilter(context: Context): String =
        normalizeGlobalSearchMediaFilter(
            prefs(context).getString(KEY_GLOBAL_SEARCH_MEDIA, null),
        )

    fun setGlobalSearchMediaFilter(context: Context, value: String) {
        prefs(context).edit()
            .putString(KEY_GLOBAL_SEARCH_MEDIA, normalizeGlobalSearchMediaFilter(value))
            .apply()
    }

    fun sourcesMediaFilter(context: Context): String =
        normalizeSourcesMediaFilter(
            prefs(context).getString(KEY_SOURCES_MEDIA, null),
        )

    fun setSourcesMediaFilter(context: Context, value: String) {
        prefs(context).edit()
            .putString(KEY_SOURCES_MEDIA, normalizeSourcesMediaFilter(value))
            .apply()
    }

    fun browseTab(context: Context): Int =
        normalizeBrowseTab(prefs(context).getInt(KEY_BROWSE_TAB, 0))

    fun setBrowseTab(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_BROWSE_TAB, normalizeBrowseTab(value)).apply()
    }

    fun sourcesQuery(context: Context): String =
        normalizeSourcesQuery(prefs(context).getString(KEY_SOURCES_QUERY, null))

    fun setSourcesQuery(context: Context, value: String) {
        prefs(context).edit()
            .putString(KEY_SOURCES_QUERY, normalizeSourcesQuery(value))
            .apply()
    }

    fun sourcesPinnedOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOURCES_PINNED_ONLY, false)

    fun setSourcesPinnedOnly(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOURCES_PINNED_ONLY, value).apply()
    }

    // ---- visibility ----
    //
    // Both stores hold what's *switched off*, not what's on. Empty therefore
    // means "everything visible", which is the right behaviour for a fresh
    // install and for a source that appears after an extension is added — a new
    // source shows up rather than being invisible until someone enables it.

    fun hiddenSources(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    fun toggleSourceHidden(context: Context, id: String): Set<String> {
        val next = hiddenSources(context).toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs(context).edit().putStringSet(KEY_HIDDEN, next).apply()
        return next
    }

    /** Hides or shows every id at once — the per-language switch. */
    fun setSourcesHidden(context: Context, ids: Collection<String>, hidden: Boolean): Set<String> {
        val next = hiddenSources(context).toMutableSet()
        if (hidden) next.addAll(ids) else next.removeAll(ids.toSet())
        prefs(context).edit().putStringSet(KEY_HIDDEN, next).apply()
        return next
    }

    /**
     * Enabled languages, not disabled ones — that's what makes the default
     * possible. A missing key means "never chosen" and yields [DEFAULT_LANGS];
     * an *empty* stored set means the user turned everything off, which is a
     * different thing and is preserved.
     *
     * The cost is that a genuinely new language — installing the first Korean
     * extension, say — arrives switched off. That's the trade for not showing
     * thirty languages nobody asked for.
     */
    fun enabledLangs(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ENABLED_LANGS, null)?.toSet() ?: DEFAULT_LANGS

    fun setLangEnabled(context: Context, lang: String, enabled: Boolean): Set<String> {
        val next = enabledLangs(context).toMutableSet()
        if (enabled) next.add(lang) else next.remove(lang)
        prefs(context).edit().putStringSet(KEY_ENABLED_LANGS, next).apply()
        return next
    }

    fun setLangsEnabled(
        context: Context,
        langs: Collection<String>,
        enabled: Boolean
    ): Set<String> {
        val next = enabledLangs(context).toMutableSet()
        if (enabled) next.addAll(langs) else next.removeAll(langs.toSet())
        prefs(context).edit().putStringSet(KEY_ENABLED_LANGS, next).apply()
        return next
    }

    /**
     * Whether 18+ sources and extensions appear at all.
     *
     * **Defaults to true**, unlike the language store's positive default, and
     * for the opposite reason: this one is not solving a too-long list, it is
     * offering to shorten one. Defaulting it off would make sources disappear
     * for every existing install after an update, which reads as an extension
     * having broken rather than as a new setting.
     *
     * **Scope, because a filter that doesn't state its scope fails silently
     * (§5).** This hides *sources and extensions* — the lists you browse from.
     * It does not hide series already saved to the library, does not untick the
     * 18+ badge, and is not a lock: nothing here is a parental control, and the
     * setting is one tap from the same screen it hides things on.
     */
    fun showNsfw(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_NSFW, true)

    fun setShowNsfw(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_NSFW, value).apply()
    }

    /**
     * A source shows only if its language is on, it isn't individually hidden,
     * and it isn't 18+ while 18+ is switched off.
     *
     * One predicate rather than a second check bolted on beside it, because the
     * Sources list and the global search fan-out both call this and a source
     * hidden from one has to be hidden from the other — §5's "two predicates
     * answering one question will disagree" is exactly what an extra
     * `&& showNsfw` at each call site would set up.
     */
    fun isVisible(
        id: String,
        lang: String,
        isNsfw: Boolean,
        hidden: Set<String>,
        enabledLangs: Set<String>,
        showNsfw: Boolean
    ): Boolean =
        id !in hidden && lang in enabledLangs && (showNsfw || !isNsfw)

    // ---- recent global searches ----
    //
    // A short, most-recent-first list of past queries, so re-running a search is
    // a tap instead of retyping. Stored as an ordered JSON array rather than a
    // StringSet — order is the whole point here, and a StringSet has none.

    /** Past queries, newest first. Empty on a fresh install or if the store is corrupt. */
    fun recentSearches(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /**
     * Records a query at the front, de-duplicated case-insensitively so re-running
     * an old search promotes it rather than adding a twin, and capped at
     * [RECENT_SEARCHES_MAX]. Blank queries are ignored. Returns the new list.
     */
    fun addRecentSearch(context: Context, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return recentSearches(context)
        val next = ArrayList<String>()
        next.add(trimmed)
        for (q in recentSearches(context)) {
            if (!q.equals(trimmed, ignoreCase = true)) next.add(q)
            if (next.size >= RECENT_SEARCHES_MAX) break
        }
        store(context, next)
        return next
    }

    /** Drops one query from the list. Returns what remains. */
    fun removeRecentSearch(context: Context, query: String): List<String> {
        val next = recentSearches(context).filterNot { it.equals(query, ignoreCase = true) }
        store(context, next)
        return next
    }

    fun clearRecentSearches(context: Context) {
        prefs(context).edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    private fun store(context: Context, queries: List<String>) {
        val arr = JSONArray()
        queries.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_RECENT_SEARCHES, arr.toString()).apply()
    }
}
