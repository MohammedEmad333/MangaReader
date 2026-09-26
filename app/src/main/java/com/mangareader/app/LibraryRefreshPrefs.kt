package com.mangareader.app

import android.content.Context

/**
 * Controls which entries take part in a full-library refresh.
 *
 * Direct refresh from a series screen and explicitly targeted source refreshes
 * deliberately ignore these preferences: an explicit user action always wins.
 */
internal enum class LibraryRefreshAge(
    val hours: Int,
    val label: String,
) {
    ALL(0, "Every time"),
    ONE_DAY(24, "After 1 day"),
    THREE_DAYS(72, "After 3 days"),
    ONE_WEEK(168, "After 7 days");

    fun next(): LibraryRefreshAge {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun from(hours: Int): LibraryRefreshAge =
            entries.firstOrNull { it.hours == hours } ?: ALL
    }
}

internal object LibraryRefreshPrefs {
    private const val PREFS = "manga_reader"
    private const val KEY_SKIP_COMPLETED = "lib_refresh_skip_completed"
    private const val KEY_MIN_AGE_HOURS = "lib_refresh_min_age_hours"
    private const val KEY_SKIPPED_SERIES = "lib_refresh_skipped_series"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Source is part of the key even though many current series ids already
     * contain it. Extension ids are not required to be globally unique.
     */
    private fun seriesKey(sourceId: String, seriesId: String): String =
        "${sourceId.length}:$sourceId:$seriesId"

    fun skipCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SKIP_COMPLETED, false)

    fun setSkipCompleted(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SKIP_COMPLETED, value).apply()
    }

    fun minimumAge(context: Context): LibraryRefreshAge =
        LibraryRefreshAge.from(prefs(context).getInt(KEY_MIN_AGE_HOURS, 0))

    fun setMinimumAge(context: Context, value: LibraryRefreshAge) {
        prefs(context).edit().putInt(KEY_MIN_AGE_HOURS, value.hours).apply()
    }

    fun skipsSeries(context: Context, sourceId: String, seriesId: String): Boolean =
        seriesKey(sourceId, seriesId) in
            (prefs(context).getStringSet(KEY_SKIPPED_SERIES, emptySet()) ?: emptySet())

    fun setSkipsSeries(
        context: Context,
        sourceId: String,
        seriesId: String,
        skip: Boolean,
    ) {
        val current = LinkedHashSet(
            prefs(context).getStringSet(KEY_SKIPPED_SERIES, emptySet()) ?: emptySet()
        )
        val key = seriesKey(sourceId, seriesId)
        if (skip) current.add(key) else current.remove(key)
        prefs(context).edit().putStringSet(KEY_SKIPPED_SERIES, current).apply()
    }

    /**
     * Applies preferences only to a full sweep. Unknown counts are always kept:
     * "not measured yet" must not become "completed" or "fresh".
     */
    fun eligibleForFullRefresh(
        context: Context,
        entries: List<LibraryEntry>,
        counts: Map<String, SeriesCounts>,
        now: Long = System.currentTimeMillis(),
    ): List<LibraryEntry> {
        val skipped = prefs(context).getStringSet(KEY_SKIPPED_SERIES, emptySet()) ?: emptySet()
        val skipCompleted = skipCompleted(context)
        val minimumAge = minimumAge(context)
        val cutoff =
            if (minimumAge.hours <= 0) Long.MAX_VALUE
            else now - minimumAge.hours * 60L * 60L * 1000L

        return entries.filter { entry ->
            if (seriesKey(entry.sourceId, entry.seriesId) in skipped) return@filter false

            val known = counts[entry.seriesId]
            if (skipCompleted && known?.completed == true) return@filter false

            if (minimumAge.hours > 0) {
                val sweptAt = known?.sweptAt ?: 0L
                if (sweptAt > cutoff) return@filter false
            }

            true
        }
    }
}
