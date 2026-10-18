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
        val back = failed.filter { it.item.chapterId in chapterIds }
        if (back.isEmpty()) {
            return DownloadQueueFailureEdit(items, failed, 0)
        }
        val queuedIds = items.mapTo(mutableSetOf()) { it.chapterId }
        val toQueue = back.map { it.item }.filter { queuedIds.add(it.chapterId) }
        return DownloadQueueFailureEdit(
            items = items + toQueue,
            failed = failed.filterNot { it.item.chapterId in chapterIds },
            changed = back.map { it.item.chapterId }.distinct().size,
        )
    }

    fun dismiss(
        items: List<DownloadItem>,
        failed: List<FailedDownload>,
        chapterId: String,
    ): DownloadQueueFailureEdit = DownloadQueueFailureEdit(
        items = items,
        failed = failed.filterNot { it.item.chapterId == chapterId },
        changed = 1,
    )

    fun clear(
        items: List<DownloadItem>,
        failed: List<FailedDownload>,
    ): DownloadQueueFailureEdit = DownloadQueueFailureEdit(
        items = items,
        failed = emptyList(),
        changed = failed.size,
    )
}
