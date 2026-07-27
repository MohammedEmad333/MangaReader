package com.mangareader.app

import android.content.Context

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

    /** A source shows only if its language is on and it isn't individually hidden. */
    fun isVisible(id: String, lang: String, hidden: Set<String>, enabledLangs: Set<String>): Boolean =
        id !in hidden && lang in enabledLangs
}
