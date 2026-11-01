package com.mangareader.app

/**
 * A series identity is scoped to its source. Extensions may reuse series IDs
 * and a saved title is not an identifier. Standalone files use chapterKey.
 */
internal fun historyEntryIdentity(entry: HistoryEntry): Triple<Boolean, String, String> =
    if (entry.sourceId.isNotBlank() && entry.seriesId.isNotBlank()) {
        Triple(true, entry.sourceId, entry.seriesId)
    } else {
        Triple(false, "", entry.chapterKey)
    }

/** Replace the last chapter read for the exact series, never another source. */
internal fun shouldReplaceHistoryEntry(
    existing: HistoryEntry,
    incoming: HistoryEntry,
): Boolean =
    existing.chapterKey == incoming.chapterKey ||
        (incoming.sourceId.isNotBlank() && incoming.seriesId.isNotBlank() &&
            existing.sourceId == incoming.sourceId &&
            existing.seriesId == incoming.seriesId)
