package com.mangareader.app

import android.content.Context

/** Schedule choices for an upcoming constrained background refresh worker. */
internal enum class ChapterUpdateFrequency(val hours: Int, val label: String) {
    OFF(0, "Off"),
    DAILY(24, "Every day"),
    THREE_DAYS(72, "Every 3 days"),
    WEEKLY(168, "Every week");

    fun next(): ChapterUpdateFrequency = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(hours: Int): ChapterUpdateFrequency =
            entries.firstOrNull { it.hours == hours } ?: OFF
    }
}

internal object ChapterUpdateSchedulePrefs {
    private const val PREFS = "manga_reader"
    private const val FREQUENCY = "chapter_update_schedule_hours"
    private const val WIFI_ONLY = "chapter_update_schedule_wifi_only"

    fun frequency(context: Context): ChapterUpdateFrequency =
        ChapterUpdateFrequency.from(context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(FREQUENCY, 0))

    fun setFrequency(context: Context, frequency: ChapterUpdateFrequency) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(FREQUENCY, frequency.hours).apply()
    }

    fun wifiOnly(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(WIFI_ONLY, true)

    fun setWifiOnly(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(WIFI_ONLY, enabled).apply()
    }
}
