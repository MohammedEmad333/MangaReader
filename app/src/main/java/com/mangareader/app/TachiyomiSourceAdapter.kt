package com.mangareader.app

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException

/**
 * Wraps a Tachiyomi CatalogueSource so it satisfies this app's Source interface.
 *
 * The SManga/SChapter objects are carried through the `handle` field rather than
 * being reconstructed from ids — extensions rely on fields (url, thumbnail_url)
 * that would otherwise be lost between calls.
 */
class TachiyomiSourceAdapter(
    private val delegate: CatalogueSource,
    private val context: Context,
    override val iconPkg: String? = null,
    override val isNsfw: Boolean = false,
) : Source {

    override val id: String = "tachi:${delegate.id}"

    /**
     * The wrapped extension source. Needed by callers that have to reach the
     * Tachiyomi-side API rather than this app's — currently only the settings
     * screen, which checks for ConfigurableSource.
     */
    val catalogueSource: CatalogueSource get() = delegate

    // Name and language are separate fields now: the sources list shows the
    // language as its own line under the name, the way Mihon does.
    override val name: String = delegate.name

    override val lang: String = langLabel(delegate.lang)

    override val supportsSearch: Boolean = true

    override val supportsPaging: Boolean = true

    private val videoScanner by lazy { TachiyomiVideoScanner(delegate) }
    private val pageLoader by lazy { TachiyomiPageLoader(delegate, context) }

    // Read once at construction. It's a property on extension code, and this
    // file assumes nothing about what extension code does — see the safe*()
    // guards below for the same reasoning applied to lateinit fields.
    override val supportsLatest: Boolean =
        runCatching { delegate.supportsLatest }.getOrDefault(false)

    override val supportsFilters: Boolean get() = filterList.isNotEmpty()

    // ---- filters ----
    //
    // Held as ONE live instance, and that is the whole subtlety. Tachiyomi's
    // `Filter` keeps its value in a mutable `state` property, so the list the
    // dialog edits has to be the same list the search reads. Calling
    // getFilterList() again returns a fresh set of defaults and would silently
    // throw away everything the user picked, which looks like filters that
    // simply don't work.
    //
    // Built lazily because it's a call into extension code and there are 95
    // sources; nothing should pay for it until someone opens the filter sheet.

    @Volatile
    private var cachedFilters: FilterList? = null

    val filterList: FilterList
        get() = cachedFilters ?: synchronized(this) {
            cachedFilters ?: runCatching { delegate.getFilterList() }
                .getOrDefault(FilterList())
                .also { cachedFilters = it }
        }

    /** Drops the instance; the next read rebuilds it at the source's defaults. */
    fun resetFilters() {
        cachedFilters = null
    }

    /**
     * Set the genre/tag filter matching [genre] so a tapped tag searches by
     * genre instead of by title text. Mirrors Tachiyomi/Mihon's genre click.
     *
     * A source exposes its genres in one of two shapes, and this handles both:
     * a [Filter.Group] of per-genre [Filter.TriState]/[Filter.CheckBox] children
     * (the common case), or a single [Filter.Select] whose values are genre
     * names. The match is by name, case-insensitively, because a chip's label is
     * exactly the genre name the source parsed.
     *
     * Resets to defaults first — via the shared live [filterList], since the
     * search reads that same instance — so the browse that follows carries this
     * tag and nothing a manual filter set left behind. On no match it leaves the
     * defaults in place and returns false, and the caller runs a title search.
     */
    override fun applyGenreFilter(genre: String): Boolean {
        resetFilters()
        for (filter in filterList) {
            when (filter) {
                is Filter.Group<*> -> {
                    val child = (filter.state as? List<*>)
                        ?.filterIsInstance<Filter<*>>()
                        ?.firstOrNull { it.name.equals(genre, ignoreCase = true) }
                    when (child) {
                        is Filter.TriState -> {
                            child.state = Filter.TriState.STATE_INCLUDE
                            return true
                        }
                        is Filter.CheckBox -> {
                            child.state = true
                            return true
                        }
                        else -> Unit
                    }
                }
                is Filter.Select<*> -> {
                    val index = filter.values
                        .indexOfFirst { it?.toString().equals(genre, ignoreCase = true) }
                    if (index >= 0) {
                        filter.state = index
                        return true
                    }
                }
                else -> Unit
            }
        }
        // No matching filter — leave the defaults untouched for the fallback
        // title search, which is what the caller does when this is false.
        resetFilters()
        return false
    }

    override val supportsDownload: Boolean = true

    /** First page of popular, for callers that just want a quick look. */
    /**
     * Runs [block] on IO and converts a [LinkageError] into an [IOException].
     *
     * **This is the boundary between this app and foreign code, and it is the
     * right place to do this — not the call sites.**
     *
     * An extension is a separately-compiled APK loaded through a
     * `PathClassLoader` against a vendored copy of the Tachiyomi API. When it
     * was built against a newer API than this app ships, the mismatch arrives as
     * an `Error`, not an `Exception`: `NoClassDefFoundError` for a class that
     * isn't here, `NoSuchMethodError` for one that changed shape. Nothing in the
     * app catches `Error`, and the ones that reach a coroutine with no handler
     * take the process down.
     *
     * 0.75 and 0.76 chased that by widening catches — every source call site in
     * `MainActivity`, then `DownloadService` and `LibraryRefreshService`. It
     * still crashed, because there was another path neither release had found,
     * and reading the tree for `launch` sites did not turn it up.
     *
     * So: stop enumerating the ways out and close the way in. Every call into an
     * extension goes through this class, so converting here means an extension
     * *cannot* raise a non-`Exception` into app code, and every ordinary
     * `catch (e: Exception)` in the tree becomes correct again — including the
     * one nobody has located.
     *
     * `IOException` specifically, because that is what a source failing to
     * answer already looks like everywhere else, and it needs no new handling.
     *
     * [CancellationException] is untouched: it is an `Exception`, it passes
     * straight through, and a cancelled coroutine still ends cancelled.
     */
    private suspend fun <T> onSourceThread(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: LinkageError) {
                throw IOException(
                    "This extension was built against a newer source API than " +
                        "this app provides \u2014 ${e.javaClass.simpleName}: " +
                        "${e.message ?: "missing symbol"}",
                    e
                )
            }
        }

    override suspend fun listSeries(): List<Series> = browseSeries(1).series

    override suspend fun browseSeries(page: Int): SeriesPage = onSourceThread {
        delegate.getPopularManga(page).toSeriesPage()
    }

    override suspend fun latestSeries(page: Int): SeriesPage = onSourceThread {
        delegate.getLatestUpdates(page).toSeriesPage()
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage =
        onSourceThread {
            // The live list, not a fresh FilterList(): a typed query and the
            // filter sheet compose rather than replace each other, which is what
            // Tachiyomi's own UI does.
            delegate.getSearchManga(page, query, filterList).toSeriesPage()
        }


    override suspend fun scanVideos(chapter: Chapter): VideoScan = onSourceThread {
        videoScanner.scan(chapter)
    }

    /** Pulls the source-relative url back out of an id built by [toSeries]. */
    private fun urlFromId(id: String): String? {
        val prefix = "${delegate.id}:"
        if (!id.startsWith(prefix)) return null
        return id.removePrefix(prefix).takeIf { it.isNotBlank() }
    }

    /**
     * Reopen a stored entry without a details round-trip.
     *
     * Browsing hands `getChapterList` the fully-parsed SManga from the catalogue
     * page and never calls `getMangaDetails`; reopening from the library used to
     * synthesise a bare stub and call it. That extra request is the only
     * difference between the two paths, and on a source whose details endpoint is
     * broken it was the thing failing — the chapters themselves were fine.
     *
     * So this builds the url + title pair that Tachiyomi says a stored entry is,
     * marks it initialized, and goes straight to the chapter list.
     */
    override suspend fun restoreSeries(id: String, title: String): Series? =
        onSourceThread {
            val url = urlFromId(id) ?: return@onSourceThread null
            SMangaImpl().apply {
                this.url = url
                this.title = title
                // Tells any extension that checks it that details are already in
                // hand and it needn't fetch them.
                this.initialized = true
            }.toSeries()
        }

    /**
     * Rebuilds the SManga from the id instead of paging the catalogue, so a
     * library entry reopens even when the series has dropped off page one.
     * The id format is "<sourceId>:<url>", and url is all HttpSource needs.
     */
    override suspend fun getSeries(id: String): Series? = onSourceThread {
        val url = urlFromId(id) ?: return@onSourceThread null

        // Both fields are lateinit on SMangaImpl, so the stub has to initialise
        // them up front: if getMangaDetails below fails, this object is what gets
        // returned, and reading an unset lateinit throws rather than yielding null.
        val stub: SManga = SMangaImpl().apply {
            this.url = url
            this.title = ""
        }
        val full = runCatching { delegate.getMangaDetails(stub) }
            .onFailure { Log.w(TAG, "getMangaDetails failed for $url", it) }
            .getOrDefault(stub)
        // getMangaDetails often leaves url blank on the returned copy.
        if (full.safeUrl().isBlank()) full.url = url
        full.toSeries()
    }

    /**
     * The details request that [restoreSeries] skips, run on its own so the
     * series screen can fill in once it arrives. Returns the series untouched if
     * it fails — metadata is a bonus, not a precondition for reading.
     */
    override suspend fun loadDetails(series: Series): Series = onSourceThread {
        val manga = series.handle as? SManga ?: return@onSourceThread series
        val full = runCatching { delegate.getMangaDetails(manga) }
            .onFailure { Log.w(TAG, "getMangaDetails failed for ${series.id}", it) }
            .getOrNull() ?: return@onSourceThread series
        if (full.safeUrl().isBlank()) full.url = manga.safeUrl()
        val enriched = full.toSeries()
        // Keep whatever we already had if the details response omits it.
        series.copy(
            title = enriched.title.ifBlank { series.title },
            cover = enriched.cover ?: series.cover,
            handle = full,
            author = enriched.author ?: series.author,
            artist = enriched.artist ?: series.artist,
            description = enriched.description ?: series.description,
            genres = enriched.genres.ifEmpty { series.genres },
            status = enriched.status ?: series.status,
        )
    }

    /**
     * `HttpSource.getMangaUrl` — the extension's own idea of where this series
     * lives on its site.
     *
     * `runCatching` is not decoration. Asura Scans builds this from
     * `manga.memo["slug"]` and throws when the memo has nothing useful in it,
     * which is exactly the shape of the 0.85 bug; a share button is not worth
     * a crash, so a failure here simply means nothing to share.
     */
    override fun seriesUrl(series: Series): String? {
        val manga = series.handle as? SManga ?: return null
        // getMangaUrl is HttpSource's, not CatalogueSource's — a local or
        // non-HTTP source has no site to point at.
        val http = delegate as? HttpSource ?: return null
        return runCatching { http.getMangaUrl(manga) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    override fun rehydrateChapter(chapter: Chapter): Chapter {
        if (chapter.handle is SChapter) return chapter
        val url = urlFromId(chapter.id) ?: return chapter
        return chapter.copy(
            handle = SChapterImpl().apply {
                this.url = url
                this.name = chapter.name
            }
        )
    }

    override suspend fun listChapters(series: Series): List<Chapter> = onSourceThread {
        val manga = series.handle as? SManga ?: return@onSourceThread emptyList()
        // Extensions return newest-first; this interface wants reading order.
        delegate.getChapterList(manga).asReversed().map { it.toChapter(safeTitleOf(manga)) }
    }

    override suspend fun loadPagesProgressively(
        chapter: Chapter,
        persist: Boolean,
        startAt: Int,
        onUpdate: suspend (List<File?>) -> Unit
    ) = onSourceThread {
        pageLoader.loadPagesProgressively(chapter, persist, startAt, onUpdate)
    }

    override suspend fun loadPages(chapter: Chapter): List<File> = onSourceThread {
        pageLoader.loadPages(chapter)
    }

    private fun MangasPage.toSeriesPage() =
        SeriesPage(series = mangas.map { it.toSeries() }, hasNext = hasNextPage)

    /**
     * `url`, `title`, and SChapter's `name` are all lateinit. An extension that
     * doesn't set one — or a details fetch that failed — makes reading it throw
     * UninitializedPropertyAccessException, which surfaced as
     * "lateinit property title has not been initialized" on opening a library
     * entry. Every read of those three goes through these.
     */
    private fun SManga.safeUrl(): String = runCatching { url }.getOrDefault("")

    private fun SManga.safeTitle(): String = runCatching { title }.getOrDefault("")

    private fun SChapter.safeUrl(): String = runCatching { url }.getOrDefault("")

    private fun SChapter.safeName(): String = runCatching { name }.getOrDefault("")

    private fun SManga.toSeries() = Series(
        id = "${delegate.id}:${safeUrl()}",
        title = safeTitle(),
        cover = thumbnail_url?.repointFromLoopback(),
        handle = this,
        // Kept apart. Joining them with ", " lost which was which, and the
        // series screen then had to guess at a split to search for one of them.
        author = author?.takeIf { it.isNotBlank() },
        artist = artist?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        genres = getGenres().orEmpty(),
        status = statusLabel(status),
    )

    /** The manga's title if it has one — these are `lateinit` (§5). */
    private fun safeTitleOf(manga: SManga): String = runCatching { manga.title }.getOrDefault("")

    private fun SChapter.toChapter(seriesTitle: String = ""): Chapter {
        val chapterUrl = safeUrl()
        return Chapter(
            id = "${delegate.id}:$chapterUrl",
            name = safeName().ifBlank { chapterUrl.trimEnd('/').substringAfterLast('/') },
            handle = this,
            dateUploaded = date_upload,
            scanlator = scanlator?.takeIf { it.isNotBlank() },
            // Nearly every extension leaves `chapter_number` at the API's
            // default of -1f, because upstream Tachiyomi parses the number out
            // of the name app-side and extensions were written against that.
            // 0.107 took the field at face value and shipped a sort with
            // nothing to sort on. `parse` honours a real value when there is
            // one and falls back to the name when there isn't.
            number = ChapterRecognition.parse(seriesTitle, safeName(), chapter_number),
        )
    }

    private companion object {
        const val TAG = "TachiyomiSourceAdapter"
    }

}
