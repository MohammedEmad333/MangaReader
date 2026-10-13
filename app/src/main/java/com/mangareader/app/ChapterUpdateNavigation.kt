package com.mangareader.app

/** Never navigate to a similarly named series from another source. */
internal fun findUpdateLibraryEntry(
    entries: List<LibraryEntry>,
    update: ChapterUpdateEvent,
): LibraryEntry? = entries.firstOrNull {
    it.seriesId == update.seriesId && it.sourceId == update.sourceId
}

/** Chapter IDs must match exactly; never open an unrelated chapter by position. */
internal fun findUpdateChapterIndex(chapters: List<Chapter>, chapterId: String): Int? =
    chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 }
