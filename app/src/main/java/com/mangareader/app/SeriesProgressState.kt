package com.mangareader.app

import android.content.Context

/**
 * Returns the chapter/episode index that Start/Resume should open.
 *
 * The furthest entry with any progress is the anchor. A partially consumed
 * entry resumes in place; a completed entry advances to the next unread one,
 * with a fallback to the first unread entry for out-of-order histories.
 */
internal data class SeriesProgressSummary(
    val resumeIndex: Int,
    val anyProgress: Boolean,
)

internal fun seriesProgressSummary(
    context: Context,
    chapters: List<Chapter>,
    sourceId: String,
    isAnimeSource: Boolean,
): SeriesProgressSummary {
    val read = BooleanArray(chapters.size)
    var lastTouched = -1

    chapters.forEachIndexed { index, chapter ->
        val key = chapterKeyOf(sourceId, chapter)
        read[index] = ReadState.isRead(context, key)
        val hasPartialProgress = if (isAnimeSource) {
            VideoPlaybackProgress.position(context, key) > 0L
        } else {
            savedPage(context, key) > 0
        }
        if (read[index] || hasPartialProgress) lastTouched = index
    }

    val resumeIndex = when {
        lastTouched >= 0 && !read[lastTouched] -> lastTouched
        else -> {
            val from = lastTouched + 1
            (from until chapters.size).firstOrNull { !read[it] }
                ?: read.indices.firstOrNull { !read[it] }
                ?: -1
        }
    }
    return SeriesProgressSummary(
        resumeIndex = resumeIndex,
        anyProgress = lastTouched >= 0,
    )
}

internal fun seriesResumeIndex(
    context: Context,
    chapters: List<Chapter>,
    sourceId: String,
    isAnimeSource: Boolean,
): Int = seriesProgressSummary(
    context = context,
    chapters = chapters,
    sourceId = sourceId,
    isAnimeSource = isAnimeSource,
).resumeIndex

internal fun seriesHasAnyProgress(
    context: Context,
    chapters: List<Chapter>,
    sourceId: String,
    isAnimeSource: Boolean,
): Boolean = seriesProgressSummary(
    context = context,
    chapters = chapters,
    sourceId = sourceId,
    isAnimeSource = isAnimeSource,
).anyProgress
