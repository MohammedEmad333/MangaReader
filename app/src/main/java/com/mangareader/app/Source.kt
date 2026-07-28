package com.mangareader.app

import java.io.File

/** One page of browse/search results, plus whether another page exists. */
data class SeriesPage(
    val series: List<Series>,
    val hasNext: Boolean
)

/**
 * A source of manga. Today: the local folder and Tachiyomi extension APKs.
 *
 * Everything below `loadPages` has a default implementation, so a source that
 * only knows how to list everything at once (LocalSource) stays valid without
 * changes — it just reports no search and a single page.
 */
interface Source {
    val id: String
    val name: String

    /**
     * Display language for the sources list, e.g. "English" or "Multi".
     * Blank means "don't show a language line" — that's the local folder.
     */
    val lang: String get() = ""

    /**
     * Package name of the extension APK this source came from, used to pull the
     * APK's launcher icon. Null for sources that aren't backed by an APK.
     */
    val iconPkg: String? get() = null

    /** Whether the extension declared tachiyomi.extension.nsfw. */
    val isNsfw: Boolean get() = false

    /** All series this source offers, or its first page for paged sources. */
    suspend fun listSeries(): List<Series>

    /** Chapters of one series, in reading order. */
    suspend fun listChapters(series: Series): List<Chapter>

    /** Extract/download the pages of a chapter, ready to display. */
    suspend fun loadPages(chapter: Chapter): List<File>

    /**
     * Same, but publishing partial results so the reader can open before the
     * whole chapter has downloaded.
     *
     * The first call to [onUpdate] is expected to carry one slot per page, all
     * null — that tells the caller how many pages there are, which is everything
     * it needs to open the reader. Later calls fill slots in. A slot that is
     * still null once this returns is a page that failed.
     *
     * With [persist] set the pages are written to permanent storage and the
     * chapter is marked downloaded once every page succeeds; otherwise they go to
     * the evictable cache. Either way a chapter already downloaded is served
     * from disk without touching the network.
     *
     * Defaults to the all-at-once [loadPages], which is right for sources that
     * produce every page in one operation, like a local CBZ.
     */
    suspend fun loadPagesProgressively(
        chapter: Chapter,
        persist: Boolean = false,
        onUpdate: suspend (List<File?>) -> Unit
    ) {
        onUpdate(loadPages(chapter))
    }

    /**
     * Whether chapters from this source can be downloaded for offline reading.
     * False for sources that are already local, like a folder of CBZs.
     */
    val supportsDownload: Boolean get() = false

    /**
     * Rebuild the source-side handle on a chapter that came back from the offline
     * cache, which can only store the fields this app owns.
     *
     * Without it a cached chapter can still be *read* when downloaded (the page
     * store is consulted before the handle is), but couldn't be fetched once back
     * online. Defaults to returning it unchanged.
     */
    fun rehydrateChapter(chapter: Chapter): Chapter = chapter

    // ---- optional capabilities ----

    /** Whether [searchSeries] does anything useful. */
    val supportsSearch: Boolean get() = false

    /** Whether [browseSeries] can return more than one page. */
    val supportsPaging: Boolean get() = false

    /**
     * One page of the source's catalogue. Page numbers are 1-based.
     * Defaults to wrapping [listSeries] as a single page.
     */
    suspend fun browseSeries(page: Int): SeriesPage =
        if (page <= 1) SeriesPage(listSeries(), hasNext = false)
        else SeriesPage(emptyList(), hasNext = false)

    /**
     * One page of search results. Page numbers are 1-based.
     * Defaults to filtering [listSeries] by title, so local folders get
     * a usable search for free.
     */
    suspend fun searchSeries(query: String, page: Int): SeriesPage {
        if (page > 1) return SeriesPage(emptyList(), hasNext = false)
        val hits = listSeries().filter { it.title.contains(query, ignoreCase = true) }
        return SeriesPage(hits, hasNext = false)
    }

    /**
     * Resolve a single series by its id, without paging the whole catalogue.
     * Used when reopening from the library. Defaults to scanning page one,
     * which is why extension sources should override it.
     */
    suspend fun getSeries(id: String): Series? =
        listSeries().firstOrNull { it.id == id }

    /**
     * Rebuild a series we already know the title of — reopening from Library or
     * History, where both were stored when it was saved.
     *
     * Tachiyomi's own contract is that a stored entry is just url + title, with
     * everything else refilled by a details fetch. Sources that can list chapters
     * from the url alone therefore don't need that fetch at all, so this exists to
     * let them skip it. Defaults to [getSeries] for sources that do need it.
     */
    suspend fun restoreSeries(id: String, title: String): Series? = getSeries(id)

    /**
     * Fill in author, description, genres and status for the series screen.
     *
     * Separate from chapter loading on purpose: it's an extra network request on
     * most sources, and one that can fail on its own. Callers treat a failure as
     * "no metadata", never as a failure to open the series. Defaults to no-op.
     */
    suspend fun loadDetails(series: Series): Series = series
}

/**
 * Fields past [handle] are metadata for the series screen. They're all optional
 * because a source may not supply them, and because reopening a stored entry
 * deliberately skips the details request that would fill them in — see
 * [Source.restoreSeries]. A blank field means "not known", never "empty".
 */
data class Series(
    val id: String,
    val title: String,
    val cover: Any?,
    val handle: Any? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null
)

data class Chapter(
    val id: String,
    val name: String,
    val handle: Any? = null,
    /** Epoch millis from the source; 0 when it doesn't publish one. */
    val dateUploaded: Long = 0L,
    val scanlator: String? = null
)

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
