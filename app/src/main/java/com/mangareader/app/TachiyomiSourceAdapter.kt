package com.mangareader.app

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val filterController by lazy { TachiyomiFilterController(delegate) }
    private val modelMapper by lazy { TachiyomiModelMapper(delegate) }

    // Read once at construction. It's a property on extension code, and this
    // file assumes nothing about what extension code does — see the safe*()
    // guards below for the same reasoning applied to lateinit fields.
    override val supportsLatest: Boolean =
        runCatching { delegate.supportsLatest }.getOrDefault(false)

    override val supportsFilters: Boolean
        get() = filterController.filterList.isNotEmpty()

    val filterList
        get() = filterController.filterList

    fun resetFilters() {
        filterController.reset()
    }

    override fun applyGenreFilter(genre: String): Boolean =
        filterController.applyGenre(genre)

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
        delegate.getPopularManga(page).let(modelMapper::toSeriesPage)
    }

    override suspend fun latestSeries(page: Int): SeriesPage = onSourceThread {
        delegate.getLatestUpdates(page).let(modelMapper::toSeriesPage)
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage =
        onSourceThread {
            // The live list, not a fresh FilterList(): a typed query and the
            // filter sheet compose rather than replace each other, which is what
            // Tachiyomi's own UI does.
            delegate.getSearchManga(page, query, filterList).let(modelMapper::toSeriesPage)
        }


    override suspend fun scanVideos(chapter: Chapter): VideoScan = onSourceThread {
        videoScanner.scan(chapter)
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
            val url = modelMapper.urlFromId(id) ?: return@onSourceThread null
            modelMapper.restoredSeries(url, title)
        }

    /**
     * Rebuilds the SManga from the id instead of paging the catalogue, so a
     * library entry reopens even when the series has dropped off page one.
     * The id format is "<sourceId>:<url>", and url is all HttpSource needs.
     */
    override suspend fun getSeries(id: String): Series? = onSourceThread {
        val url = modelMapper.urlFromId(id) ?: return@onSourceThread null

        // Both fields are lateinit on SMangaImpl, so the stub has to initialise
        // them up front: if getMangaDetails below fails, this object is what gets
        // returned, and reading an unset lateinit throws rather than yielding null.
        val stub: SManga = modelMapper.stubManga(url)
        val full = runCatching { delegate.getMangaDetails(stub) }
            .onFailure { Log.w(TAG, "getMangaDetails failed for $url", it) }
            .getOrDefault(stub)
        // getMangaDetails often leaves url blank on the returned copy.
        modelMapper.ensureUrl(full, url)
        modelMapper.toSeries(full)
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
        modelMapper.ensureUrl(full, modelMapper.safeUrl(manga))
        val enriched = modelMapper.toSeries(full)
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

    override fun rehydrateChapter(chapter: Chapter): Chapter =
        modelMapper.rehydrateChapter(chapter)

    override suspend fun listChapters(series: Series): List<Chapter> = onSourceThread {
        val manga = series.handle as? SManga ?: return@onSourceThread emptyList()
        // Extensions return newest-first; this interface wants reading order.
        delegate.getChapterList(manga).asReversed()
            .map { modelMapper.toChapter(it, modelMapper.safeTitle(manga)) }
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

    private companion object {
        const val TAG = "TachiyomiSourceAdapter"
    }

}
