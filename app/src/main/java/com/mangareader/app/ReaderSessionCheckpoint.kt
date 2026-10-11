package com.mangareader.app

import android.content.Context

/**
 * Last non-incognito reading location, stored independently of history entries.
 * The launcher can use these stable IDs to resolve the source and chapter later.
 */
internal data class ReaderSessionCheckpoint(
    val sourceId: String,
    val seriesId: String,
    val chapterId: String,
    val page: Int,
) {
    fun isValid(): Boolean =
        sourceId.isNotBlank() && seriesId.isNotBlank() &&
            chapterId.isNotBlank() && page >= 0
}

internal object ReaderSessionCheckpointStore {
    private const val SOURCE = "reader_session_source"
    private const val SERIES = "reader_session_series"
    private const val CHAPTER = "reader_session_chapter"
    private const val PAGE = "reader_session_page"

    fun save(context: Context, checkpoint: ReaderSessionCheckpoint) {
        if (isIncognito(context) || !checkpoint.isValid()) return
        prefs(context).edit()
            .putString(SOURCE, checkpoint.sourceId)
            .putString(SERIES, checkpoint.seriesId)
            .putString(CHAPTER, checkpoint.chapterId)
            .putInt(PAGE, checkpoint.page)
            .apply()
    }

    fun load(context: Context): ReaderSessionCheckpoint? {
        if (isIncognito(context)) return null
        val values = prefs(context)
        return ReaderSessionCheckpoint(
            sourceId = values.getString(SOURCE, "").orEmpty(),
            seriesId = values.getString(SERIES, "").orEmpty(),
            chapterId = values.getString(CHAPTER, "").orEmpty(),
            page = values.getInt(PAGE, -1),
        ).takeIf { it.isValid() }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(SOURCE).remove(SERIES)
            .remove(CHAPTER).remove(PAGE).apply()
    }
}
