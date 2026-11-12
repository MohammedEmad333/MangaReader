package com.mangareader.app

/**
 * Compose reader state must belong to a specific series and chapter.
 *
 * Chapter IDs are not guaranteed to be unique across series within a source,
 * so source + chapter alone cannot safely key a reader composition.
 */
internal data class ReaderRouteIdentity(
    val sourceId: String,
    val seriesId: String,
    val chapterId: String,
)

internal fun readerRouteIdentity(
    sourceId: String,
    seriesId: String?,
    chapterId: String,
): ReaderRouteIdentity = ReaderRouteIdentity(
    sourceId = sourceId,
    seriesId = seriesId.orEmpty(),
    chapterId = chapterId,
)
