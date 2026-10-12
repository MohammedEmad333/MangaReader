package com.mangareader.app

/** Resolve a saved session only to the same source, series, and chapter. */
internal fun findRestorableReaderHistory(
    checkpoint: ReaderSessionCheckpoint,
    history: List<HistoryEntry>,
): HistoryEntry? {
    if (!checkpoint.isValid()) return null
    val chapterKey = "${checkpoint.sourceId}|${checkpoint.chapterId}"
    return history.firstOrNull {
        it.sourceId == checkpoint.sourceId &&
            it.seriesId == checkpoint.seriesId &&
            it.chapterKey == chapterKey &&
            it.mediaType == "manga"
    }
}
