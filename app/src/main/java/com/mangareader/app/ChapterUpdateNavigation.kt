package com.mangareader.app

/** Never navigate to a similarly named series from another source. */
internal fun findUpdateLibraryEntry(
    entries: List<LibraryEntry>,
    update: ChapterUpdateEvent,
): LibraryEntry? = entries.firstOrNull {
    it.seriesId == update.seriesId && it.sourceId == update.sourceId
}
