package com.mangareader.app

import java.io.File

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
