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

/**
 * Stable, Bundle-saveable key for the History LazyColumn and scroll ordering.
 * Extensions can share series IDs, so the source ID must participate. Prefix
 * with source length to avoid delimiter collisions between source/series IDs.
 * Standalone files follow the same chapter identity as History.forDisplay.
 */
internal fun historyRowKey(entry: HistoryEntry): String {
    val identity = historyEntryIdentity(entry)
    return if (identity.first) {
        "history:series:${identity.second.length}:${identity.second}:${identity.third}"
    } else {
        "history:chapter:${identity.third}"
    }
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
