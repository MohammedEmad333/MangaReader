package com.mangareader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small persisted history for the future Updates screen. */
internal data class ChapterUpdateEvent(
    val sourceId: String,
    val seriesId: String,
    val seriesTitle: String,
    val chapterId: String,
    val chapterName: String,
    val detectedAt: Long,
    val isRead: Boolean = false,
)

internal object ChapterUpdateJournal {
    private const val PREFS = "manga_reader"
    private const val KEY = "chapter_update_events_v1"
    private const val MAX_EVENTS = 500
    private val lock = Any()

    fun list(context: Context): List<ChapterUpdateEvent> = synchronized(lock) {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return@synchronized emptyList()
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ChapterUpdateEvent(
                    sourceId = item.getString("sourceId"),
                    seriesId = item.getString("seriesId"),
                    seriesTitle = item.getString("seriesTitle"),
                    chapterId = item.getString("chapterId"),
                    chapterName = item.getString("chapterName"),
                    detectedAt = item.getLong("detectedAt"),
                    isRead = item.optBoolean("isRead", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun append(context: Context, events: List<ChapterUpdateEvent>) {
        if (events.isEmpty()) return
        synchronized(lock) {
            val current = list(context)
            val merged = merge(current, events)
            val array = JSONArray()
            merged.forEach { event ->
                array.put(JSONObject()
                    .put("sourceId", event.sourceId)
                    .put("seriesId", event.seriesId)
                    .put("seriesTitle", event.seriesTitle)
                    .put("chapterId", event.chapterId)
                    .put("chapterName", event.chapterName)
                    .put("detectedAt", event.detectedAt)
                    .put("isRead", event.isRead))
            }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply()
        }
    }

    fun markRead(context: Context, selected: Set<Triple<String, String, String>>? = null) {
        synchronized(lock) {
            val updates = list(context).map { event ->
                if (selected == null || Triple(event.sourceId, event.seriesId, event.chapterId) in selected)
                    event.copy(isRead = true)
                else event
            }
            val array = JSONArray()
            updates.forEach { event ->
                array.put(JSONObject()
                    .put("sourceId", event.sourceId)
                    .put("seriesId", event.seriesId)
                    .put("seriesTitle", event.seriesTitle)
                    .put("chapterId", event.chapterId)
                    .put("chapterName", event.chapterName)
                    .put("detectedAt", event.detectedAt)
                    .put("isRead", event.isRead))
            }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply()
        }
    }

    fun clear(context: Context) = synchronized(lock) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    internal fun merge(
        previous: List<ChapterUpdateEvent>,
        incoming: List<ChapterUpdateEvent>,
    ): List<ChapterUpdateEvent> =
        (previous + incoming)
            .map { event ->
                if (previous.any { it.sourceId == event.sourceId && it.seriesId == event.seriesId &&
                        it.chapterId == event.chapterId && it.isRead })
                    event.copy(isRead = true)
                else event
            }
            .sortedByDescending { it.detectedAt }
            .distinctBy { Triple(it.sourceId, it.seriesId, it.chapterId) }
            .take(MAX_EVENTS)
}
