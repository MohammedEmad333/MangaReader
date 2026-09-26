package com.mangareader.app

import android.content.Context

/**
 * Per-series automatic download preference.
 *
 * Disabled by default. The refresh worker also requires a pre-existing chapter
 * cache before it considers anything "new", so enabling this can never turn the
 * first library refresh into a download of the whole backlog.
 */
internal object AutoDownloadPrefs {
    private const val PREFS = "manga_reader"
    private const val KEY_ENABLED = "auto_download_series"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(sourceId: String, seriesId: String): String =
        sourceId.length.toString() + ":" + sourceId + ":" + seriesId

    fun enabled(context: Context, sourceId: String, seriesId: String): Boolean =
        key(sourceId, seriesId) in
            (prefs(context).getStringSet(KEY_ENABLED, emptySet()) ?: emptySet())

    fun setEnabled(
        context: Context,
        sourceId: String,
        seriesId: String,
        enabled: Boolean,
    ) {
        val current = LinkedHashSet(
            prefs(context).getStringSet(KEY_ENABLED, emptySet()) ?: emptySet()
        )
        val value = key(sourceId, seriesId)
        if (enabled) current.add(value) else current.remove(value)
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
    }

    /**
     * Returns chapters not represented by the previous snapshot.
     *
     * Id is the strongest identity, but extension updates sometimes rewrite URLs
     * for every chapter. The normalized name fallback prevents that from looking
     * like hundreds of brand-new chapters and flooding the queue.
     */
    fun newChapters(previous: List<Chapter>, fresh: List<Chapter>): List<Chapter> {
        if (previous.isEmpty() || fresh.isEmpty()) return emptyList()

        val oldIds = previous.mapTo(HashSet()) { it.id }
        val oldNames = previous.mapTo(HashSet()) { normalize(it.name) }

        return fresh.filter { chapter ->
            chapter.id !in oldIds && normalize(chapter.name) !in oldNames
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")
}
