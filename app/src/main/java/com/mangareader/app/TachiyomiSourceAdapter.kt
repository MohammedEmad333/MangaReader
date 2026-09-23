package com.mangareader.app

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import eu.kanade.tachiyomi.source.model.Page as TachiPage

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


    /**
     * See [Source.listVideos] for why this exists outside the page list.
     *
     * Fetches the chapter's own page THROUGH THE EXTENSION'S CLIENT AND HEADERS,
     * not a bare request: that client carries the Cloudflare interceptor, the
     * recorded clearance UA and whatever else the source needs, and a plain GET
     * would be challenged where the extension is not.
     *
     * The selector is generic — `video[src]` and `video source[src]` — rather
     * than anything CosplayTele-shaped. A source with no <video> tags returns an
     * empty list and no caller shows anything, so this costs nothing anywhere
     * except the one request, which is only made when asked for.
     *
     * Relative URLs are resolved against the document, so a source using
     * `/media/x.mp4` works without special handling.
     */
    override suspend fun scanVideos(chapter: Chapter): VideoScan = onSourceThread {
        val http = delegate as? HttpSource
            ?: return@onSourceThread VideoScan(emptyList(), note = "Not an HTTP source.")
        val sChapter = chapter.handle as? SChapter
            ?: return@onSourceThread VideoScan(emptyList(), note = "This chapter has no source handle.")
        val url = http.baseUrl + sChapter.url

        val body = http.client.newCall(GET(url, http.headers)).execute().use { response ->
            if (!response.isSuccessful) {
                return@onSourceThread VideoScan(
                    emptyList(),
                    note = "The page answered HTTP ${response.code}."
                )
            }
            response.body?.string()
                ?: return@onSourceThread VideoScan(emptyList(), note = "The page returned no body.")
        }

        // `abs:src` resolves against the document's own base, so the second
        // argument here is what makes a relative path usable.
        val doc = Jsoup.parse(body, url)
        val links = doc.select("video[src], video source[src]")
            .map { it.attr("abs:src") }
            .filter { it.isNotBlank() }
            .distinct()

        // Iframes are collected whether or not a direct link was found: a page
        // can carry both, and the embed is the fallback when the media file is
        // built by the player's own script rather than served in the markup.
        // Data-uri and about:blank frames are dropped — those are ad slots.
        val embeds = doc.select("iframe[src]")
            .map { it.attr("abs:src") }
            .filter { it.startsWith("http", ignoreCase = true) }
            .filterNot { url -> ANALYTICS_FRAMES.any { url.contains(it, ignoreCase = true) } }
            .distinct()

        if (links.isNotEmpty()) return@onSourceThread VideoScan(links, embeds)

        // NOTHING FOUND, so report what the document DID hold. Counting the
        // elements a video could hide in separates "no video anywhere in the
        // served HTML" — meaning JavaScript builds it, and no selector will ever
        // help — from "a video element exists but not in the shape being
        // selected", which is one more selector away.
        //
        // The raw-text counts matter more than the tag counts: a .mp4 in the
        // HTML with no <video> around it means the markup is something else
        // entirely, and the excerpt below shows what.
        val videos = doc.select("video").size
        val iframes = doc.select("iframe").size
        val sources = doc.select("source").size
        val mp4 = Regex("\\.mp4").findAll(body).count()
        val m3u8 = Regex("\\.m3u8").findAll(body).count()

        val excerpt = listOf(".mp4", ".m3u8", "<video")
            .firstNotNullOfOrNull { needle ->
                body.indexOf(needle, ignoreCase = true).takeIf { it >= 0 }?.let { at ->
                    body.substring(
                        (at - 90).coerceAtLeast(0),
                        (at + 90).coerceAtMost(body.length)
                    ).replace(Regex("\\s+"), " ")
                }
            }

        VideoScan(
            emptyList(),
            embeds,
            buildString {
                append("Page fetched, ${body.length} chars.\n")
                append("video=$videos  iframe=$iframes  source=$sources\n")
                append("\".mp4\"×$mp4  \".m3u8\"×$m3u8\n")
                if (excerpt != null) append("\nAround the first match:\n…$excerpt…")
                else append("\nNo .mp4, .m3u8 or <video anywhere in the HTML.")
                if (embeds.isNotEmpty()) {
                    append("\n\nThe media is built by an embedded player's own ")
                    append("script, so no selector here can reach the file. ")
                    append("The frame below opens it.")
                }
            }
        )
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

    /**
     * Downloads in source order, [PAGE_CONCURRENCY] at a time, publishing after
     * every batch.
     *
     * The empty slot list goes out first so the reader can open on the page count
     * alone — previously nothing was shown until the last byte of the last page
     * had landed, which on a 30MB chapter is a long stare at a blank screen.
     *
     * Fetched from [startAt] forward, then wrapping to the pages before it, so a
     * chapter resumed at page 40 doesn't download 39 pages nobody is waiting for
     * first. Only the *order requests go out in* changes: [done] is indexed by
     * page number throughout and every file is written under its own index, so
     * the published list and the files on disk are identical either way. At
     * `startAt = 0` the order is the plain front-to-back sequence this used to
     * have, which is what keeps the download path unchanged.
     *
     * The batching is deliberately untouched. Requests still go out
     * [PAGE_CONCURRENCY] at a time with the same gap and the same recycle
     * cadence, because requests-per-connection is the variable the manhwatoon
     * 400s turned on and reordering must not disturb it.
     */
    override suspend fun loadPagesProgressively(
        chapter: Chapter,
        persist: Boolean,
        startAt: Int,
        onUpdate: suspend (List<File?>) -> Unit
    ) = onSourceThread {
        // Already downloaded: serve straight off disk, no page list request, no
        // image requests. This is what makes offline reading work.
        if (Downloads.isComplete(context, chapter.id)) {
            onUpdate(Downloads.pages(context, chapter.id))
            return@onSourceThread
        }

        val sChapter = chapter.handle as? SChapter
        if (sChapter == null) {
            onUpdate(emptyList())
            if (persist) throw ChapterDownloadException(0, 0, IllegalStateException("No chapter handle"))
            return@onSourceThread
        }
        val pages = cappedPageList(sChapter)
        val dir = if (persist) Downloads.dirFor(context, chapter.id)
        else Downloads.cacheDirFor(context, chapter.id)
        dir.mkdirs()

        val done = arrayOfNulls<File>(pages.size)
        onUpdate(done.toList())

        // The reason a failure is kept rather than dropped: `getOrNull()` alone
        // turned a 403, a timeout and a missing image URL into the same silence,
        // and the download path had nothing to report but "some pages failed".
        val firstError = AtomicReference<Throwable?>(null)

        // Page indices in the order they'll be requested. Every index appears
        // exactly once, so this is the same work in a different sequence.
        val order = fetchOrder(pages.size, startAt)

        // How many pages were actually tried, and how many of those failed.
        // Kept because `done` cannot answer either question: a page nobody
        // reached is null exactly like a page that failed.
        var attempted = 0
        var failedSoFar = 0

        // Consecutive connect failures per host, for THIS fetch only.
        //
        // Scoped to the call rather than the process on purpose: a host down now
        // may be up in a minute, and a breaker outliving the fetch turns a blip
        // into a source that stays broken until restart. Nothing has to remember
        // to reset it, because it goes out of scope with the chapter.
        // ConcurrentHashMap because downloadPage writes to it from the async
        // page coroutines, which is the point: a failure has to be visible to
        // its siblings WHILE the batch is still running, not after it.
        val hostFailures = ConcurrentHashMap<String, Int>()

        order.chunked(PAGE_CONCURRENCY).forEachIndexed { batch, chunk ->
            chunk.map { index ->
                async {
                    // The index is carried through rather than recomputed from
                    // the batch, because it is no longer the position in the
                    // sequence — it is the page's own number, and it addresses
                    // both the slot below and the file on disk.
                    val outcome = runCatching { downloadPage(pages[index], dir, index, hostFailures) }
                    outcome.exceptionOrNull()?.let { firstError.compareAndSet(null, it) }
                    Triple(index, outcome.getOrNull(), outcome.exceptionOrNull())
                }
            }.awaitAll().let { results ->
                results.forEach { (index, file, _) -> done[index] = file }
                attempted += results.size
                failedSoFar += results.count { (_, file, _) -> file == null }

                // Connect failures are counted inside downloadPage, one per
                // ATTEMPT, so the retries count too — they are the same host
                // refusing the same connection. Success is handled here, once
                // the batch is complete, so the reset does not depend on the
                // order results happen to arrive in.
                results.mapNotNull { (index, file, _) ->
                    if (file != null) hostOf(pages[index].imageUrl) else null
                }.toSet().forEach { hostFailures.remove(it) }
            }
            onUpdate(done.toList())

            // Checked per completed batch, because pages are fetched
            // concurrently and failures arrive together rather than in order.
            val dead = hostFailures.entries
                .firstOrNull { it.value >= HOST_CONNECT_FAILURE_LIMIT }
            if (dead != null) {
                // Named, because the host IS the diagnosis for this failure
                // class. Without it a chapter targeting one unreachable host
                // spends pages x connectTimeout discovering that one page at a
                // time, with nothing remembering the previous attempts and no
                // user-visible error until the very end.
                val stopped = IOException(
                    "Could not connect to ${dead.key} — gave up after " +
                        "${dead.value} consecutive failures across $attempted " +
                        "of ${pages.size} pages"
                )
                // Thrown on BOTH paths. The download path wraps it so the
                // chapter fails rather than being stored partial; the reader
                // surfaces it through sourceFailureMessage instead of sitting
                // on spinners.
                if (persist) {
                    // failedSoFar, NOT done.count { it == null }: pages that were
                    // never attempted are also null, so counting nulls reported
                    // "30 of 30 pages failed" for a chapter that stopped after
                    // six. A number that looks like a measurement and is not one
                    // is the fault this whole area has been fixing all session.
                    throw ChapterDownloadException(failedSoFar, pages.size, stopped)
                }
                throw stopped
            }

            if ((batch + 1) * PAGE_CONCURRENCY < order.size) {
                // Cap how many requests any one connection carries. This is the
                // preventative half of recycleConnections() — retrying on a fresh
                // socket fixes a failure after the fact, this stops the pooled
                // connection getting old enough to cause one.
                if ((batch + 1) % CONNECTION_RECYCLE_BATCHES == 0) recycleConnections()
                delay(BATCH_GAP_MS)
            }
        }

        if (!persist) return@onSourceThread

        // Only a chapter with every page present counts as downloaded; a partial
        // one stays unmarked so it can be resumed rather than trusted.
        val failed = done.count { it == null }
        if (pages.isNotEmpty() && failed == 0) {
            Downloads.markComplete(context, chapter.id, pages.size)
        } else {
            throw ChapterDownloadException(failed, pages.size, firstError.get())
        }
    }

    /**
     * Page indices ordered from [startAt] to the end, then backwards from
     * [startAt] to the first page.
     *
     * A permutation of `0 until count`, always — the caller relies on every page
     * being requested exactly once, and on the count of batches being unchanged.
     *
     * The tail runs *descending* so the pages immediately behind the reader come
     * back first. Ascending was the obvious way to write it and it puts the page
     * one flick above you last in the queue, which makes scrolling back the
     * slowest thing in the chapter — the exact move someone resuming is most
     * likely to make.
     *
     * [startAt] is clamped rather than trusted: it comes from a stored resume
     * position, and a chapter that lost pages since it was last read will hand
     * this a number past the end.
     */
    private fun fetchOrder(count: Int, startAt: Int): List<Int> {
        if (count <= 0) return emptyList()
        val start = startAt.coerceIn(0, count - 1)
        // Not merely an optimisation — it is the guarantee that the download
        // path, which never passes a start, behaves exactly as it did before.
        if (start == 0) return (0 until count).toList()
        return (start until count) + (start - 1 downTo 0)
    }


    /**
     * [getPageList] with a ceiling on it. Both call sites go through this.
     *
     * The app had no limit of any kind on getPageList — not a request count, not
     * a page count, not a clock. An extension that looped was indistinguishable
     * from a slow source, and the reader would sit on spinners forever with
     * nothing anywhere able to say which.
     *
     * TWO LIMITS, BECAUSE THEY CATCH DIFFERENT FAILURES.
     *
     * The PAGE COUNT catches an extension that returns, having collected
     * nonsense — a gallery whose `rel=next` walked into the site's listing
     * pagination and came back with thousands of "pages". That is a bug whatever
     * the timing, and this check is exact.
     *
     * The WALL CLOCK catches one that never returns at all. **It does not stop
     * it.** withTimeout cancels the coroutine, and cancellation in Kotlin is
     * cooperative: a blocking `while (true)` inside an extension has no
     * suspension point to observe it, so the extension's thread keeps going
     * until it finishes or the process dies. What this buys is that the READER
     * stops waiting and says why. The runaway work is leaked, deliberately and
     * knowingly, because the alternative on offer is an app that hangs.
     *
     * If a source is ever found that actually hits the clock, the leak stops
     * being theoretical and this needs revisiting — probably by giving the
     * extension's OkHttp client a smaller call timeout, which is the only lever
     * that reaches inside its loop.
     *
     * BOTH THROW rather than returning what was collected. A truncated chapter
     * reads as one that legitimately ends early, which is a silent failure of
     * the kind this codebase keeps finding; source errors have named their
     * exception type since 0.78, so the existing path reports these properly.
     * Both call sites throw BEFORE any directory is created, so a cap firing
     * mid-download fails the chapter rather than storing a partial one.
     */
    private suspend fun cappedPageList(sChapter: SChapter): List<TachiPage> {
        val pages = try {
            withTimeout(PAGE_LIST_TIMEOUT_MS) { delegate.getPageList(sChapter) }
        } catch (e: TimeoutCancellationException) {
            throw IOException(
                "This source took more than ${PAGE_LIST_TIMEOUT_MS / 1000}s to list " +
                    "the pages of one chapter and was given up on. The extension may be " +
                    "stuck following its own pagination."
            )
        }
        if (pages.size > PAGE_LIST_MAX) {
            throw IOException(
                "This source returned ${pages.size} pages for one chapter, past the " +
                    "$PAGE_LIST_MAX-page limit. That is almost certainly the extension " +
                    "walking the site rather than the chapter."
            )
        }
        return pages
    }

    override suspend fun loadPages(chapter: Chapter): List<File> = onSourceThread {
        val sChapter = chapter.handle as? SChapter ?: return@onSourceThread emptyList()
        val pages = cappedPageList(sChapter)

        if (Downloads.isComplete(context, chapter.id)) {
            return@onSourceThread Downloads.pages(context, chapter.id)
        }
        val dir = Downloads.cacheDirFor(context, chapter.id).apply { mkdirs() }

        // A tally of its own, unread: this path has no circuit breaker and is
        // not getting one here. Passing a fresh map keeps downloadPage's
        // retry-suppression working within this call without leaking counts
        // between two unrelated fetches.
        val hostFailures = ConcurrentHashMap<String, Int>()
        pages.mapIndexedNotNull { index, page ->
            runCatching { downloadPage(page, dir, index, hostFailures) }.getOrNull()
        }
    }

    /**
     * Fetches one page, retrying transient rejections.
     *
     * This exists because of a source that answers **400** under parallel load
     * rather than 429: a minority of pages of any given chapter fail, the URLs
     * are perfectly well-formed, and the very same URL succeeds moments later.
     * Retrying by hand was working, so the loop belongs here.
     *
     * The backoff is per page, not per chapter, so a chapter isn't restarted for
     * the sake of two pages, and the jitter keeps the four in-flight requests of
     * a batch from re-colliding on the same schedule after they fail together.
     */
    /**
     * The source's own OkHttp client, when it has one.
     *
     * Only used to reach its connection pool — every actual request still goes
     * through the source's own methods, so extensions that override
     * `imageRequest` to sign URLs or send a POST keep working.
     */
    private val httpClient: OkHttpClient?
        get() = (delegate as? HttpSource)?.client

    /**
     * Drops pooled connections so the next request opens a fresh one.
     *
     * `cdn.manhwatoon.me` starts answering 400 once a connection has carried
     * enough requests. The evidence is that the failure rate tracks
     * requests-per-connection and nothing else: 4 concurrent requests over HTTP/2
     * share one connection and 12 of 36 pages failed; 2 over HTTP/1.1 use two
     * connections and 7 of 39 failed. It also explains the observation that ruled
     * out every other theory — an immediate retry fails because it lands on the
     * same pooled socket, while a manual retry minutes later gets a fresh one.
     *
     * This is global to that client, so it can disturb a request in flight on
     * another coroutine. At PAGE_CONCURRENCY of 2 that's at most one, and that
     * one would be retried anyway.
     */
    private fun recycleConnections() {
        runCatching { httpClient?.connectionPool?.evictAll() }
    }

    /** Host of [url], or null if it has none or cannot be parsed. */
    private fun hostOf(url: String?): String? =
        if (url.isNullOrBlank()) null
        else runCatching { java.net.URI(url).host }.getOrNull()

    /**
     * The host a page failure was against, taken from the failure itself.
     *
     * [PageDownloadException] already carries the URL it was fetching, and that
     * is more reliable than re-reading `page.imageUrl`: for a source that
     * resolves the image URL lazily, the field may still be null on the page
     * object while the exception knows exactly what it tried.
     */
    private fun failingHost(error: Throwable?): String? =
        (error as? PageDownloadException)?.let { hostOf(it.url) }

    /**
     * Whether this failure means "no connection was made", as opposed to a
     * server that answered with something unwelcome.
     *
     * The distinction is the whole point of the breaker: a 404 or a decode
     * error says something about ONE page, while a connect timeout or a refusal
     * says something about the host, and only the second generalises. Walks the
     * cause chain because the page path wraps failures twice.
     */
    private fun isConnectFailure(error: Throwable?): Boolean {
        var e = error
        var depth = 0
        while (e != null && depth++ < CAUSE_CHAIN_LIMIT) {
            if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) return true
            e = e.cause
        }
        return false
    }

    /**
     * [hostFailures] is the fetch-scoped connect-failure tally, shared with the
     * sibling page coroutines. Written here rather than in the batch loop
     * because a retry is another connection to the same host and has to count
     * as one, and because a failure has to be visible to the pages running
     * alongside this one while the batch is still in flight.
     */
    private suspend fun downloadPage(
        page: TachiPage,
        dir: File,
        index: Int,
        hostFailures: ConcurrentHashMap<String, Int>
    ): File {
        var attempt = 0
        while (true) {
            try {
                return fetchPage(page, dir, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++

                // A connect failure is recorded per attempt, and past the first
                // one for that host this stops retrying.
                //
                // RETRYING A CONNECT TIMEOUT AGAINST A HOST THAT JUST TIMED OUT
                // BUYS NOTHING. recycleConnections() exists for a stale pool,
                // and a fresh socket does not help a host that is not answering
                // — it pays the full connectTimeout again to learn the same
                // thing. This was the floor under the circuit breaker: three
                // attempts at 30s made every page cost 90s, so six page
                // failures took four and a half minutes to notice.
                val deadHost = isConnectFailure(e) &&
                    hostOf(page.imageUrl)?.let { host ->
                        hostFailures.merge(host, 1, Int::plus)!! >= CONNECT_RETRY_GIVE_UP_AT
                    } == true

                // A 403 or 404 means the same thing however many times it's
                // asked, so those fail immediately rather than burning three
                // more requests and eleven seconds on a foregone conclusion.
                if (attempt >= PAGE_ATTEMPTS || !isTransient(e) || deadHost) {
                    throw PageDownloadException(index, page.imageUrl, e)
                }
                // Before the backoff, not after: the retry has to land on a new
                // connection or it reproduces the failure exactly.
                recycleConnections()
                delay(
                    PAGE_RETRY_BASE_MS * (1L shl (attempt - 1)) +
                        Random.nextLong(PAGE_RETRY_JITTER_MS)
                )
            }
        }
    }

    private fun isTransient(e: Throwable): Boolean = when (e) {
        // 400 is in here because of the source described above. It's normally a
        // permanent "your request is wrong", but a server using it as a throttle
        // signal is indistinguishable from one that means it — and retrying a
        // genuinely malformed request a few times costs little.
        is HttpException -> e.code in TRANSIENT_HTTP_CODES
        is IOException -> true
        else -> false
    }

    private suspend fun fetchPage(page: TachiPage, dir: File, index: Int): File {
        val http = delegate as? HttpSource

        // Some sources return pages without a direct image URL; it has to be
        // resolved with a second request first.
        if (page.imageUrl.isNullOrEmpty() && http != null) {
            page.imageUrl = http.getImageUrl(page)
        }
        // Done here rather than only on covers: the same server hands out the
        // same broken host for page images, and `imageRequest(page)` builds the
        // request from this field, so fixing it here covers both the default
        // path and a source that overrides the request.
        page.imageUrl = page.imageUrl?.repointFromLoopback()

        val target = File(dir, "%04d".format(index))
        if (target.exists() && target.length() > 0L) return target

        // Going through the source's own client matters: it carries the
        // source's headers (Referer, User-Agent). A bare GET 403s on most sites.
        val body = if (http != null) {
            http.getImage(page).body!!
        } else {
            val url = page.imageUrl ?: error("No image url for page $index")
            fallbackClient.newCall(Request.Builder().url(url).build()).execute().body!!
        }

        val partial = File(dir, "%04d.part".format(index))
        body.byteStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        // Write-then-rename, so an interrupted download can't leave a truncated
        // file that the exists() check above would later treat as complete.
        partial.renameTo(target)
        return target
    }

    /**
     * Repoints an image URL that came back aimed at loopback.
     *
     * SpyFakku's mirrors return covers and pages as
     * `http://127.0.0.1/image/<hash>/<n>`, which resolves to the phone itself
     * and fails to connect. The cause is upstream — the site's application
     * builds absolute URLs from its own bind address instead of the public host
     * it's proxied behind — and it is not fixable from here or by choosing a
     * different mirror, because every mirror runs the same software. The path
     * and query are correct; only the origin is wrong, so swapping in the
     * source's own base URL produces exactly the URL the server meant to send.
     *
     * Applied to every source rather than special-casing one, which is safe
     * because a legitimate source can never mean loopback: that address is this
     * phone. A genuinely self-hosted source would have a loopback `baseUrl` too,
     * and the guard below leaves those alone rather than rewriting a correct
     * port into a wrong one.
     */
    private fun String.repointFromLoopback(): String {
        val url = toHttpUrlOrNull() ?: return this
        if (!url.host.isLoopback()) return this
        val base = (delegate as? HttpSource)?.baseUrl?.toHttpUrlOrNull() ?: return this
        if (base.host.isLoopback()) return this
        return url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
            .toString()
    }

    private fun String.isLoopback(): Boolean =
        this == "localhost" || this == "::1" || this == "0.0.0.0" || startsWith("127.")

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

        /**
         * Pages fetched in parallel.
         *
         * Was 4. Dropped to 2 alongside the move to HTTP/1.1 in `NetworkHelper`:
         * with no multiplexing, each concurrent request holds its own connection,
         * and two is enough to hide latency without opening a fan of sockets at
         * every source.
         */
        const val PAGE_CONCURRENCY = 2

        /**
         * Total tries per page, first attempt included.
         *
         * Kept low on purpose. Retrying was originally set to 4 attempts on the
         * theory that a 400 here meant rate limiting; it didn't, and all the
         * extra attempts bought was a much slower download that failed anyway.
         * What's left covers genuinely transient failures — a dropped connection,
         * a momentary 5xx — rather than trying to out-stubborn a server.
         */
        const val PAGE_ATTEMPTS = 3

        /**
         * Ceiling on how many pages one chapter may claim to have.
         *
         * Generous on purpose: the longest real chapters anywhere near this app
         * are a few hundred pages, so this only fires on something that is not a
         * chapter at all. A tighter limit would start refusing real content, and
         * refusing real content is worse than the failure it guards against.
         */
        /**
         * Iframe hosts that are never a player.
         *
         * The http-only filter was not enough: CosplayTele's two frames are the
         * player AND googletagmanager.com/ns.html, which is the GTM noscript
         * beacon. Offering that as "Open player 1" is worse than offering
         * nothing, because it looks like the feature found something.
         *
         * A denylist, not an allowlist: a new player host should still show up
         * without anyone editing this, whereas a new tracker only costs one
         * useless row until someone adds it here.
         */
        val ANALYTICS_FRAMES = listOf(
            "googletagmanager.com",
            "google-analytics.com",
            "doubleclick.net",
            "googlesyndication.com",
            "facebook.net",
            "facebook.com/plugins",
            "disqus.com"
        )

        const val PAGE_LIST_MAX = 2000

        /**
         * How long one getPageList may take before the caller stops waiting.
         *
         * Three minutes, which is far longer than any legitimate page list and
         * is meant to be. A wall clock punishes a slow network for an
         * extension's fault, so it is set where only a genuinely stuck source
         * can reach it. See cappedPageList for what this does and does not do.
         */
        const val PAGE_LIST_TIMEOUT_MS = 180_000L

        /** Doubles each attempt: 750ms, then 1.5s. */
        const val PAGE_RETRY_BASE_MS = 750L

        /** Spread so a batch that failed together doesn't retry in lockstep. */
        const val PAGE_RETRY_JITTER_MS = 250L

        /** Breather between batches. */
        const val BATCH_GAP_MS = 200L

        /**
         * Recycle pooled connections every this many batches.
         *
         * At PAGE_CONCURRENCY of 2 that caps a connection at roughly 8 requests
         * before it's replaced, which is the point of the exercise — see
         * `recycleConnections`. Lower it if 400s persist; raise it if downloads
         * feel slow, since each recycle costs a TCP and TLS handshake.
         */
        const val CONNECTION_RECYCLE_BATCHES = 4

        /**
         * Failed connect ATTEMPTS to one host before a fetch gives up.
         *
         * Attempts, not pages: a retry is another connection to the same host
         * and counts. The first shipped version counted pages and predicted
         * ninety seconds; it took four and a half minutes, because
         * PAGE_ATTEMPTS = 3 made every page failure cost 3 x connectTimeout and
         * nothing had multiplied by that.
         *
         * With retries suppressed past the first failure per host, four is
         * roughly two batches — about a minute against a blackholed host,
         * against the eighteen minutes AHottie used to take.
         */
        const val HOST_CONNECT_FAILURE_LIMIT = 4

        /**
         * Connect failures to one host, within a fetch, after which that host's
         * pages stop retrying. Two, so a single blip still gets its retry.
         */
        const val CONNECT_RETRY_GIVE_UP_AT = 2

        /** Depth limit when walking a cause chain, so a cycle cannot hang. */
        const val CAUSE_CHAIN_LIMIT = 6

        /**
         * Worth retrying. 408/429/5xx are the textbook ones; 400 is here because
         * at least one source uses it as its throttle response.
         */
        val TRANSIENT_HTTP_CODES = setOf(400, 408, 425, 429, 500, 502, 503, 504)

        val fallbackClient = OkHttpClient()
    }
}
