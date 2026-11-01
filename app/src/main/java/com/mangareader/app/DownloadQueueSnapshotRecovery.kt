package com.mangareader.app

/**
 * Normalize old or interrupted queue snapshots before exposing them to Compose.
 * Invalid identities cannot be resolved by the download service, and duplicate
 * chapter IDs would collide in the queue's keyed rows.
 */
internal fun normalizeRestoredDownloadQueue(
    items: List<DownloadItem>,
    failed: List<FailedDownload>,
    paused: Boolean,
    pausedIds: Set<String>,
    isComplete: (String) -> Boolean,
): DownloadQueueSnapshot {
    val queued = items.asSequence()
        .filter { it.sourceId.isNotBlank() && it.chapterId.isNotBlank() }
        .filterNot { isComplete(it.chapterId) }
        .distinctBy { it.chapterId }
        .toList()
    val queuedIds = queued.mapTo(mutableSetOf()) { it.chapterId }
    val failures = failed.asSequence()
        .filter { it.item.sourceId.isNotBlank() && it.item.chapterId.isNotBlank() }
        // Stale failures must not be shown alongside the same active job.
        .filterNot { it.item.chapterId in queuedIds }
        .filterNot { isComplete(it.item.chapterId) }
        .distinctBy { it.item.chapterId }
        .toList()

    return DownloadQueueSnapshot(
        items = queued,
        failed = failures,
        paused = paused,
        pausedIds = pausedIds.intersect(queuedIds),
    )
}
