package com.mangareader.app

import android.content.Context

internal data class ChapterUpdateCheckSummary(
    val completedAt: Long,
    val checked: Int,
    val failed: Int,
    val unavailable: Int,
    val newChapters: Int = 0,
    val sourceDiscoveryFailed: Boolean = false,
)

internal object ChapterUpdateCheckSummaryStore {
    private const val PREFS = "manga_reader"

    fun save(context: Context, result: ChapterUpdateCheckSummary) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("chapter_update_last_check_at", result.completedAt)
            .putInt("chapter_update_last_checked", result.checked)
            .putInt("chapter_update_last_failed", result.failed)
            .putInt("chapter_update_last_unavailable", result.unavailable)
            .putInt("chapter_update_last_new_chapters", result.newChapters)
            .putBoolean("chapter_update_source_discovery_failed", result.sourceDiscoveryFailed)
            .apply()
    }

    fun load(context: Context): ChapterUpdateCheckSummary? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("chapter_update_last_check_at", 0L)
        if (timestamp <= 0L) return null
        return ChapterUpdateCheckSummary(
            timestamp,
            prefs.getInt("chapter_update_last_checked", 0),
            prefs.getInt("chapter_update_last_failed", 0),
            prefs.getInt("chapter_update_last_unavailable", 0),
            prefs.getInt("chapter_update_last_new_chapters", 0),
            prefs.getBoolean("chapter_update_source_discovery_failed", false),
        )
    }
}

internal fun scheduledCheckOutcomeLabel(summary: ChapterUpdateCheckSummary?): String {
    if (summary == null) return "No automatic check completed yet"
    if (summary.sourceDiscoveryFailed) return "Could not load sources for automatic check"
    return "${summary.checked} checked • ${summary.newChapters} new chapters • ${summary.failed} failed • ${summary.unavailable} unavailable"
}
