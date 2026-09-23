package com.mangareader.app

/**
 * One page that couldn't be fetched, with enough context to act on it.
 *
 * The URL is the point. A bare "HTTP error 400" says the server rejected the
 * request but not what was wrong with it, and when most pages of the same chapter
 * succeed, the answer is almost always visible in the URL of one that didn't —
 * an unencoded character, an empty path where `getImageUrl` resolved to nothing,
 * an expired signature.
 */
class PageDownloadException(
    val index: Int,
    val url: String?,
    cause: Throwable
) : Exception(describe(index, url, cause), cause) {

    companion object {
        /** Long enough to see the shape of a CDN URL, short enough to read. */
        private const val URL_LIMIT = 120

        private fun describe(index: Int, url: String?, cause: Throwable): String {
            val why = cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName
            val where = when {
                url.isNullOrBlank() -> "no image url"
                url.length <= URL_LIMIT -> url
                else -> url.take(URL_LIMIT) + "\u2026"
            }
            // 1-based: the page numbers everywhere else in the app are.
            return "$why on page ${index + 1} \u2014 $where"
        }
    }
}

/**
 * A download that finished with pages missing.
 *
 * Thrown by [Source.loadPagesProgressively] only when `persist = true`. The
 * reader path deliberately doesn't get this: a failed page there is drawn as a
 * broken slot and the rest of the chapter stays readable, which is the right
 * behaviour when someone is looking at it. A *download* that quietly stops short
 * is different — it never gets its `.complete` marker, so without an exception
 * the caller has no way to say anything more useful than "something failed",
 * which is exactly where this started.
 *
 * [cause] is the first page failure, not the last: with four pages in flight per
 * batch, a single 429 typically takes its three neighbours down with it, and the
 * first one is the one that explains the rest.
 */
class ChapterDownloadException(
    val failedPages: Int,
    val totalPages: Int,
    cause: Throwable? = null
) : Exception(describe(failedPages, totalPages, cause), cause) {

    companion object {
        private fun describe(failed: Int, total: Int, cause: Throwable?): String {
            val what =
                if (total == 0) "The source returned no pages"
                else "$failed of $total pages failed"
            // HttpException's message is "HTTP error 429", which is the whole
            // point of carrying it up. Exceptions without one fall back to the
            // class name so the message is never just a dangling dash.
            val why = cause?.let {
                it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName
            }
            return if (why == null) what else "$what \u2014 $why"
        }
    }
}
