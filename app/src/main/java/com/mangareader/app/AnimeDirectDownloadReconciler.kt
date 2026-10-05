package com.mangareader.app

import android.app.DownloadManager
import android.content.Context
import java.io.File

internal const val MAX_DIRECT_ANIME_DOWNLOAD_RETRIES = 1

internal fun shouldRetryDirectAnimeDownload(retryCount: Int): Boolean =
    retryCount < MAX_DIRECT_ANIME_DOWNLOAD_RETRIES

/**
 * Reconciles direct anime downloads delegated to Android DownloadManager.
 *
 * This runs on app start and when Android announces a completed download, so a
 * direct episode becomes available in Offline anime even when the Downloads tab
 * was never opened while the transfer was running.
 */
internal object AnimeDirectDownloadReconciler {
    fun reconcileAll(context: Context): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return AnimeDirectDownloadIndex.list(context).sumOf { item ->
            if (reconcile(context, manager, item)) 1 else 0
        }
    }

    fun reconcileId(context: Context, id: Long): Boolean {
        if (id < 0L) return false
        val item = AnimeDirectDownloadIndex.list(context).firstOrNull { it.id == id } ?: return false
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return reconcile(context, manager, item)
    }

    /** User-initiated retry after the automatic retry budget has been exhausted. */
    fun retryFailed(context: Context, item: PendingDirectAnimeDownload): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.remove(item.id)
        File(item.path).delete()

        val replacementId = runCatching {
            AnimeVideoDownload.retryDirect(context, item)
        }.getOrElse {
            return false
        }

        AnimeDirectDownloadIndex.replace(
            context = context,
            oldId = item.id,
            item = item.copy(
                id = replacementId,
                startedAt = System.currentTimeMillis(),
                retryCount = 0,
            ),
        )
        return true
    }

    private fun reconcile(
        context: Context,
        manager: DownloadManager,
        item: PendingDirectAnimeDownload,
    ): Boolean {
        val cursor = runCatching {
            manager.query(DownloadManager.Query().setFilterById(item.id))
        }.getOrNull() ?: return false

        cursor.use {
            if (!it.moveToFirst()) {
                AnimeDirectDownloadIndex.remove(context, item.id)
                File(item.path).delete()
                return false
            }

            return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> promoteCompleted(context, item)
                DownloadManager.STATUS_FAILED -> retryOrKeepFailed(context, manager, item)
                else -> false
            }
        }
    }

    private fun retryOrKeepFailed(
        context: Context,
        manager: DownloadManager,
        item: PendingDirectAnimeDownload,
    ): Boolean {
        // Keep the terminal failure in the persistent index so Downloads can
        // show an explicit Retry/Delete choice instead of silently discarding it.
        if (!shouldRetryDirectAnimeDownload(item.retryCount)) return false

        manager.remove(item.id)
        File(item.path).delete()
        val replacementId = runCatching {
            AnimeVideoDownload.retryDirect(context, item)
        }.getOrElse {
            return false
        }

        AnimeDirectDownloadIndex.replace(
            context = context,
            oldId = item.id,
            item = item.copy(
                id = replacementId,
                startedAt = System.currentTimeMillis(),
                retryCount = item.retryCount + 1,
            ),
        )
        return false
    }

    private fun promoteCompleted(context: Context, item: PendingDirectAnimeDownload): Boolean {
        val file = File(item.path)
        if (!file.exists() || file.length() <= 0L) return false

        AnimeOfflineIndex.record(
            context,
            AnimeOfflineItem(
                title = item.title,
                path = item.path,
                sourceUrl = item.sourceUrl,
                quality = item.quality,
                downloadedAt = System.currentTimeMillis(),
            ),
        )
        AnimeDirectDownloadIndex.remove(context, item.id)
        return true
    }
}
