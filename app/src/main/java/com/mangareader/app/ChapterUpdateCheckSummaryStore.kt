package com.mangareader.app

import android.content.Context

internal data class ChapterUpdateCheckSummary(
    val completedAt: Long,
    val checked: Int,
    val failed: Int,
    val unavailable: Int,
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
        )
    }
}

internal fun scheduledCheckOutcomeLabel(summary: ChapterUpdateCheckSummary?): String {
    if (summary == null) return "No automatic check completed yet"
    return "${summary.checked} checked • ${summary.failed} failed • ${summary.unavailable} unavailable"
}
