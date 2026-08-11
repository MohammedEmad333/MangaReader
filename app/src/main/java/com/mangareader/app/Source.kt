package com.mangareader.app

import java.io.File

/** One page of browse/search results, plus whether another page exists. */
data class SeriesPage(
    val series: List<Series>,
    val hasNext: Boolean
)

/** Which listing the per-source browse screen is showing. */
enum class BrowseMode { POPULAR, LATEST, FILTER }

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
     * [startAt] is where the reader is going to open. Pages from there to the end
     * are fetched first and the ones before it afterwards, so resuming halfway
     * through a chapter doesn't spend the first minute downloading pages that
     * have already been read. It only reorders *fetching* — the published list is
     * always in page order, and a slot is filled at its own index whenever it
     * lands. Defaulted to 0 because the download path wants the whole chapter
     * regardless of where anyone stopped reading, and 0 reproduces the old
     * front-to-back sequence exactly.
     *
     * Defaults to the all-at-once [loadPages], which is right for sources that
     * produce every page in one operation, like a local CBZ — there is no fetch
     * order to reorder, so [startAt] is ignored.
     */
    suspend fun loadPagesProgressively(
        chapter: Chapter,
        persist: Boolean = false,
        startAt: Int = 0,
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

    /**
     * Whether [latestSeries] shows anything [browseSeries] doesn't.
     *
     * Tachiyomi's `CatalogueSource` declares this per source and a good number
     * of them answer false, so the browse screen only offers the Popular/Latest
     * choice where there is actually a choice to make.
     */
    val supportsLatest: Boolean get() = false

    /** Whether this source declares any filters worth showing. */
    val supportsFilters: Boolean get() = false

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
    /**
     * Newest additions, page by page.
     *
     * Falls back to [browseSeries] rather than to an empty page: a caller that
     * asks for it on a source declaring [supportsLatest] false should get the
     * catalogue, not nothing.
     */
    suspend fun latestSeries(page: Int): SeriesPage = browseSeries(page)

    /**
     * Browse using whatever filters are currently set on the source.
     *
     * An empty query rather than a separate call, because Tachiyomi's
     * `getSearchManga(page, query, filters)` is the one entry point that takes
     * filters at all \u2014 a filtered browse *is* a search with nothing typed.
     */
    suspend fun filteredSeries(page: Int): SeriesPage = searchSeries("", page)

    /**
     * Set a genre/tag filter matching [genre], returning whether the source had
     * one. This is what makes a tapped tag search *by genre* rather than run the
     * genre's text as a title query: a source that lists "Romance" as a filter
     * has it selected, and a [filteredSeries] browse then returns everything
     * tagged Romance instead of everything with "Romance" in its title.
     *
     * Resets other filters to their defaults first, so a tapped tag searches for
     * exactly that tag and nothing carried over from a manual filter set — the
     * same thing Tachiyomi/Mihon's genre click does.
     *
     * Callers fall back to a title [searchSeries] when this returns false, since
     * many sources still match a genre inside their text search. The default
     * matches nothing (LocalSource, and any source without filters).
     */
    fun applyGenreFilter(genre: String): Boolean = false

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

    /**
     * Video URLs on a chapter's own page, for handing to an external player.
     *
     * **This deliberately sits OUTSIDE the page list, and that is the whole
     * design.** Some gallery sources publish photos and videos together —
     * CosplayTele's "31 photos and 13 videos" is the reported case — but
     * `pageListParse` there is `select(".gallery-item img")`, which matches
     * `<img>` and nothing else. The videos are on the page and the extension
     * never emits them, so nothing in this app has ever seen one.
     *
     * Folding them into [loadPages] would have been the obvious move and would
     * have been wrong: it changes page counts, chapter progress, the download
     * queue's arithmetic and the reader's every assumption about what a page is,
     * for every source, to serve one. This is additive instead — a source that
     * has no videos returns an empty list and nothing anywhere changes.
     *
     * **It is a second-guess at an extension's job and it will rot.** The app
     * is otherwise a pure pass-through: it hands filters and requests to the
     * extension unmodified and reads back what it is given. This one method
     * breaks that on purpose, so it is scoped as narrowly as it can be — a
     * generic `<video>` scan of a document the source already told us about,
     * never a per-source selector. When it stops finding things, suspect the
     * site's markup, and prefer fixing the extension upstream over widening
     * this.
     */
    suspend fun scanVideos(chapter: Chapter): VideoScan = VideoScan(emptyList())

    /**
     * A page on the source's own site for [series], for sharing or opening in a
     * browser. Null when there is nothing to point at — local folders, and any
     * source whose handle didn't survive.
     */
    fun seriesUrl(series: Series): String? = null
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
    /**
     * Separate from [author] because sources report them separately and many
     * series have two different people in them. They were joined into one
     * string at the boundary, which made "who drew this" unanswerable and made
     * searching for either of them search for both at once.
     */
    val artist: String? = null,
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
    val scanlator: String? = null,
    /**
     * The source's own chapter number, from `SChapter.chapter_number`.
     *
     * **[NO_NUMBER] means "the source didn't say", and that is not zero.** Three
     * things arrive without one and all three are normal: a chapter from a
     * source that publishes no numbering, a chapter rebuilt from the download
     * queue (which stores an id and a name and nothing else), and every chapter
     * in a `ChapterCache` file written before this field existed. Defaulting
     * those to `0f` would make them a real chapter zero and pile them at the top
     * of a number sort, which reads as the sort being broken rather than as data
     * being absent — the same three-state trap `SeriesIndex` documents, in a new
     * store.
     *
     * So anything sorting on this puts [NO_NUMBER] at the end, never among the
     * real ones, and anything displaying it falls back to [name].
     */
    val number: Float = NO_NUMBER
) {
    companion object {
        /** "This source published no chapter number." Negative so it can never collide with one. */
        const val NO_NUMBER = -1f
    }
}

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

/**
 * The result of [Source.scanVideos] — what was found, and what was there instead.
 *
 * **[note] exists because "found nothing" is not a diagnosis.** The first
 * version of this returned a bare list, and when it came back empty on a gallery
 * advertising thirteen videos there was no way to tell which of three things had
 * happened: the videos are injected by JavaScript and are not in the served HTML
 * at all; they are in a different element than the one being selected; or the
 * page served to this client differs from the one a browser sees.
 *
 * Separating those from a phone otherwise means reading page source, which
 * Android browsers do not offer. So the scan reports what the document actually
 * contained — the same instinct as the connection probe, which exists because a
 * failure that names nothing costs more than the request that would have named
 * it.
 */
data class VideoScan(
    /** Direct, playable urls — a <video> the page served itself. */
    val links: List<String>,
    /**
     * Iframe urls, which are where an embedded player usually lives.
     *
     * CosplayTele is the worked example: its page has ONE embedded video and no
     * <video> element, no .mp4 and no .m3u8 anywhere in 286KB of served HTML —
     * the player is built by the iframe's own script. Nothing this app can
     * select will ever reach the media file, but the iframe url IS in the HTML,
     * and handing that to a browser gets a working player.
     *
     * Kept separate from [links] because they are not the same promise. A link
     * plays; an embed opens a page that might.
     */
    val embeds: List<String> = emptyList(),
    /** What the document held, when nothing playable was found. */
    val note: String? = null
)
