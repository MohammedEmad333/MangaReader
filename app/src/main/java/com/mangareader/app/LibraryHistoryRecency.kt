package com.mangareader.app

/**
 * Newest read timestamp per source-qualified series, in a single pass.
 *
 * Imported history can have multiple chapters of the same series. Keep the
 * maximum timestamp regardless of input order, while skipping standalone
 * files without a source and series.
 */
internal fun libraryLastReadIndex(entries: Iterable<HistoryEntry>): Map<String, Long> {
    val latest = HashMap<String, Long>()
    for (entry in entries) {
        if (entry.seriesId.isBlank() || entry.sourceId.isBlank()) continue
        val key = libraryEntryKey(entry.sourceId, entry.seriesId)
        val previous = latest[key]
        if (previous == null || entry.updatedAt > previous) {
            latest[key] = entry.updatedAt
        }
    }
    return latest
}
