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
        return DownloadQueueFailureEdit(
            items = items + back.map { it.item },
            failed = failed.filterNot { it.item.chapterId in chapterIds },
            changed = back.size,
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
