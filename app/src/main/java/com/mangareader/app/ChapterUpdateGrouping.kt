package com.mangareader.app

import java.text.DateFormat
import java.util.Date

internal data class ChapterUpdateGroup(
    val sourceId: String,
    val seriesId: String,
    val seriesTitle: String,
    val events: List<ChapterUpdateEvent>,
)

/** Groups each series within a local calendar date while preserving newest-first order. */
internal fun groupChapterUpdatesByDay(
    events: List<ChapterUpdateEvent>,
    dayKey: (Long) -> String,
): Map<String, List<ChapterUpdateGroup>> =
    events.sortedByDescending { it.detectedAt }
        .groupBy { dayKey(it.detectedAt) }
        .mapValues { (_, sameDay) ->
            sameDay.groupBy { it.sourceId to it.seriesId }
                .map { (_, chapters) ->
                    val latest = chapters.first()
                    ChapterUpdateGroup(
                        latest.sourceId, latest.seriesId, latest.seriesTitle, chapters
                    )
                }
        }

internal fun chapterUpdateDayLabel(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
