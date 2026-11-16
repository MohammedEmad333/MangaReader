package com.mangareader.app

import eu.kanade.tachiyomi.network.HttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** One additional chapter pass after page-level retries, never an endless queue loop. */
internal const val MAX_TRANSIENT_CHAPTER_RETRIES = 1
internal const val TRANSIENT_CHAPTER_RETRY_DELAY_MS = 1_500L

/**
 * Only retry failures that are probably temporary. An empty page list, invalid
 * image, broken chapter handle, missing source or HTTP 4xx other than 408/425/429
 * should go straight to the persistent Failed list for a manual retry.
 */
internal fun shouldRetryTransientChapterFailure(
    failure: Throwable,
    retriesAlreadyMade: Int,
): Boolean {
    if (retriesAlreadyMade < 0 || retriesAlreadyMade >= MAX_TRANSIENT_CHAPTER_RETRIES) return false
    if (failure !is ChapterDownloadException ||
        failure.totalPages <= 0 || failure.failedPages <= 0) return false

    var cause: Throwable? = failure.cause
    val visited = HashSet<Throwable>()
    repeat(8) {
        if (cause == null || !visited.add(cause!!)) return false
        when (val current = cause!!) {
            is HttpException -> return isRetryablePageHttpStatus(current.code)
            is SocketTimeoutException,
            is ConnectException,
            is NoRouteToHostException,
            is UnknownHostException -> return true
        }
        cause = cause?.cause
    }
    return false
}
