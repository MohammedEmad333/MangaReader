package com.mangareader.app

/**
 * Validate new queue entries before any filesystem lookup.
 *
 * Downloads are keyed by chapter ID. Empty IDs collide, and an empty source ID
 * cannot be resolved by the service. Preserve requested order and ignore any
 * chapters already queued or completely downloaded.
 */
internal fun eligibleNewDownloadItems(
    queued: List<DownloadItem>,
    requested: List<DownloadItem>,
    isComplete: (String) -> Boolean,
): List<DownloadItem> {
    val seen = queued.mapTo(mutableSetOf()) { it.chapterId }
    return requested.filter { item ->
        item.chapterId.isNotBlank() &&
            item.sourceId.isNotBlank() &&
            item.chapterId !in seen &&
            !isComplete(item.chapterId) &&
            seen.add(item.chapterId)
    }
}
