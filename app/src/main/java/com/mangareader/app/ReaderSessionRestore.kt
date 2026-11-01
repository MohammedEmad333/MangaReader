package com.mangareader.app

/** Resolve a saved session only to the same source, series, and chapter. */
internal fun findRestorableReaderHistory(
    checkpoint: ReaderSessionCheckpoint,
    history: List<HistoryEntry>,
): HistoryEntry? {
    if (!checkpoint.isValid()) return null
    val chapterKey = "${checkpoint.sourceId}|${checkpoint.chapterId}"
    // Older or corrupted history may contain duplicates: never restore an
    // arbitrary row if multiple matches claim the same checkpoint identity.
    return history.singleOrNull {
        it.sourceId == checkpoint.sourceId &&
            it.seriesId == checkpoint.seriesId &&
            it.chapterKey == chapterKey &&
            it.mediaType == "manga"
    }
}

/** Resolve a history chapter only when its source-qualified identity is unique. */
internal fun findHistoryChapterIndex(
    chapters: List<Chapter>,
    sourceId: String,
    chapterKey: String,
): Int? {
    if (sourceId.isBlank() || chapterKey.isBlank()) return null
    return chapters.indices.singleOrNull { index ->
        val chapter = chapters[index]
        chapter.id.isNotBlank() && chapterKeyOf(sourceId, chapter) == chapterKey
    }
}
