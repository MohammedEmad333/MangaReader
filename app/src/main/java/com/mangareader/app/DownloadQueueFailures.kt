package com.mangareader.app

internal data class DownloadQueueFailureEdit(
    val items: List<DownloadItem>,
    val failed: List<FailedDownload>,
    val changed: Int,
)

internal object DownloadQueueFailures {
    fun retry(
        items: List<DownloadItem>,
        failed: List<FailedDownload>,
        chapterIds: Set<String>,
    ): DownloadQueueFailureEdit {
        // Blank chapter IDs cannot identify a unique download and are unsafe to retry.
        val validIds = chapterIds.filterTo(mutableSetOf()) { it.isNotBlank() }
        val back = failed.filter { it.item.chapterId in validIds }
        if (back.isEmpty()) {
            return DownloadQueueFailureEdit(items, failed, 0)
        }
        val queuedIds = items.mapTo(mutableSetOf()) { it.chapterId }
        val toQueue = back.map { it.item }.filter { queuedIds.add(it.chapterId) }
        return DownloadQueueFailureEdit(
            items = items + toQueue,
            failed = failed.filterNot { it.item.chapterId in validIds },
            changed = back.map { it.item.chapterId }.distinct().size,
        )
    }

    fun dismiss(
        items: List<DownloadItem>,
        failed: List<FailedDownload>,
        chapterId: String,
    ): DownloadQueueFailureEdit {
        if (chapterId.isBlank()) return DownloadQueueFailureEdit(items, failed, 0)
        val remaining = failed.filterNot { it.item.chapterId == chapterId }
        return DownloadQueueFailureEdit(
            items = items,
            failed = remaining,
            changed = failed.size - remaining.size,
        )
    }

    fun clear(
        items: List<DownloadItem>,
        failed: List<FailedDownload>,
    ): DownloadQueueFailureEdit = DownloadQueueFailureEdit(
        items = items,
        failed = emptyList(),
        changed = failed.size,
    )
}
