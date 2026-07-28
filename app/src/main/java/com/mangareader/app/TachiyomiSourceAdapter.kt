package com.mangareader.app

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
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

    override val supportsDownload: Boolean = true

    /** First page of popular, for callers that just want a quick look. */
    override suspend fun listSeries(): List<Series> = browseSeries(1).series

    override suspend fun browseSeries(page: Int): SeriesPage = withContext(Dispatchers.IO) {
        delegate.getPopularManga(page).toSeriesPage()
    }

    override suspend fun latestSeries(page: Int): SeriesPage = withContext(Dispatchers.IO) {
        delegate.getLatestUpdates(page).toSeriesPage()
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage =
        withContext(Dispatchers.IO) {
            // The live list, not a fresh FilterList(): a typed query and the
            // filter sheet compose rather than replace each other, which is what
            // Tachiyomi's own UI does.
            delegate.getSearchManga(page, query, filterList).toSeriesPage()
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
        withContext(Dispatchers.IO) {
            val url = urlFromId(id) ?: return@withContext null
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
    override suspend fun getSeries(id: String): Series? = withContext(Dispatchers.IO) {
        val url = urlFromId(id) ?: return@withContext null

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
    override suspend fun loadDetails(series: Series): Series = withContext(Dispatchers.IO) {
        val manga = series.handle as? SManga ?: return@withContext series
        val full = runCatching { delegate.getMangaDetails(manga) }
            .onFailure { Log.w(TAG, "getMangaDetails failed for ${series.id}", it) }
            .getOrNull() ?: return@withContext series
        if (full.safeUrl().isBlank()) full.url = manga.safeUrl()
        val enriched = full.toSeries()
        // Keep whatever we already had if the details response omits it.
        series.copy(
            title = enriched.title.ifBlank { series.title },
            cover = enriched.cover ?: series.cover,
            handle = full,
            author = enriched.author ?: series.author,
            description = enriched.description ?: series.description,
            genres = enriched.genres.ifEmpty { series.genres },
            status = enriched.status ?: series.status,
        )
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

    override suspend fun listChapters(series: Series): List<Chapter> = withContext(Dispatchers.IO) {
        val manga = series.handle as? SManga ?: return@withContext emptyList()
        // Extensions return newest-first; this interface wants reading order.
        delegate.getChapterList(manga).asReversed().map { it.toChapter() }
    }

    /**
     * Downloads in source order, [PAGE_CONCURRENCY] at a time, publishing after
     * every batch.
     *
     * The empty slot list goes out first so the reader can open on the page count
     * alone — previously nothing was shown until the last byte of the last page
     * had landed, which on a 30MB chapter is a long stare at a blank screen.
     *
     * In source order rather than starting from the resume position: the adapter
     * isn't told where the reader will open, and reading is overwhelmingly
     * front-to-back.
     */
    override suspend fun loadPagesProgressively(
        chapter: Chapter,
        persist: Boolean,
        onUpdate: suspend (List<File?>) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Already downloaded: serve straight off disk, no page list request, no
        // image requests. This is what makes offline reading work.
        if (Downloads.isComplete(context, chapter.id)) {
            onUpdate(Downloads.pages(context, chapter.id))
            return@withContext
        }

        val sChapter = chapter.handle as? SChapter
        if (sChapter == null) {
            onUpdate(emptyList())
            if (persist) throw ChapterDownloadException(0, 0, IllegalStateException("No chapter handle"))
            return@withContext
        }
        val pages = delegate.getPageList(sChapter)
        val dir = if (persist) Downloads.dirFor(context, chapter.id)
        else Downloads.cacheDirFor(context, chapter.id)
        dir.mkdirs()

        val done = arrayOfNulls<File>(pages.size)
        onUpdate(done.toList())

        // The reason a failure is kept rather than dropped: `getOrNull()` alone
        // turned a 403, a timeout and a missing image URL into the same silence,
        // and the download path had nothing to report but "some pages failed".
        val firstError = AtomicReference<Throwable?>(null)

        pages.chunked(PAGE_CONCURRENCY).forEachIndexed { batch, chunk ->
            val base = batch * PAGE_CONCURRENCY
            chunk.mapIndexed { offset, page ->
                async {
                    runCatching { downloadPage(page, dir, base + offset) }
                        .onFailure { firstError.compareAndSet(null, it) }
                        .getOrNull()
                }
            }.awaitAll().forEachIndexed { offset, file ->
                done[base + offset] = file
            }
            onUpdate(done.toList())
            if (base + PAGE_CONCURRENCY < pages.size) {
                // Cap how many requests any one connection carries. This is the
                // preventative half of recycleConnections() — retrying on a fresh
                // socket fixes a failure after the fact, this stops the pooled
                // connection getting old enough to cause one.
                if ((batch + 1) % CONNECTION_RECYCLE_BATCHES == 0) recycleConnections()
                delay(BATCH_GAP_MS)
            }
        }

        if (!persist) return@withContext

        // Only a chapter with every page present counts as downloaded; a partial
        // one stays unmarked so it can be resumed rather than trusted.
        val failed = done.count { it == null }
        if (pages.isNotEmpty() && failed == 0) {
            Downloads.markComplete(context, chapter.id, pages.size)
        } else {
            throw ChapterDownloadException(failed, pages.size, firstError.get())
        }
    }

    override suspend fun loadPages(chapter: Chapter): List<File> = withContext(Dispatchers.IO) {
        val sChapter = chapter.handle as? SChapter ?: return@withContext emptyList()
        val pages = delegate.getPageList(sChapter)

        if (Downloads.isComplete(context, chapter.id)) {
            return@withContext Downloads.pages(context, chapter.id)
        }
        val dir = Downloads.cacheDirFor(context, chapter.id).apply { mkdirs() }

        pages.mapIndexedNotNull { index, page ->
            runCatching { downloadPage(page, dir, index) }.getOrNull()
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

    private suspend fun downloadPage(page: TachiPage, dir: File, index: Int): File {
        var attempt = 0
        while (true) {
            try {
                return fetchPage(page, dir, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                // A 403 or 404 means the same thing however many times it's
                // asked, so those fail immediately rather than burning three
                // more requests and eleven seconds on a foregone conclusion.
                if (attempt >= PAGE_ATTEMPTS || !isTransient(e)) {
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
        author = listOfNotNull(author, artist)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        genres = getGenres().orEmpty(),
        status = statusLabel(status),
    )

    private fun SChapter.toChapter(): Chapter {
        val chapterUrl = safeUrl()
        return Chapter(
            id = "${delegate.id}:$chapterUrl",
            name = safeName().ifBlank { chapterUrl.trimEnd('/').substringAfterLast('/') },
            handle = this,
            dateUploaded = date_upload,
            scanlator = scanlator?.takeIf { it.isNotBlank() },
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
         * Worth retrying. 408/429/5xx are the textbook ones; 400 is here because
         * at least one source uses it as its throttle response.
         */
        val TRANSIENT_HTTP_CODES = setOf(400, 408, 425, 429, 500, 502, 503, 504)

        val fallbackClient = OkHttpClient()
    }
}

/**
 * Turns an extension's language code into the label shown in the sources list.
 *
 * Extensions use ISO codes plus Tachiyomi's own "all" for multi-language
 * sources. Anything not listed falls back to the uppercased code, so a source
 * in a language nobody mapped still reads sensibly instead of showing blank.
 */
fun langLabel(code: String): String = when (code.lowercase()) {
    "all" -> "Multi"
    "other" -> "Other"
    "en" -> "English"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "zh" -> "Chinese"
    "es" -> "Spanish"
    "es-419" -> "Spanish (LatAm)"
    "fr" -> "French"
    "de" -> "German"
    "it" -> "Italian"
    "pt" -> "Portuguese"
    "pt-br" -> "Portuguese (BR)"
    "ru" -> "Russian"
    "id" -> "Indonesian"
    "vi" -> "Vietnamese"
    "th" -> "Thai"
    "ar" -> "Arabic"
    "tr" -> "Turkish"
    "pl" -> "Polish"
    "uk" -> "Ukrainian"
    "fa" -> "Persian"
    "hi" -> "Hindi"
    "fil" -> "Filipino"
    "ms" -> "Malay"
    "nl" -> "Dutch"
    "ca" -> "Catalan"
    "he" -> "Hebrew"
    "cs" -> "Czech"
    "hu" -> "Hungarian"
    "ro" -> "Romanian"
    "bg" -> "Bulgarian"
    "el" -> "Greek"
    "sv" -> "Swedish"
    "no", "nb" -> "Norwegian"
    "da" -> "Danish"
    "fi" -> "Finnish"
    else -> code.uppercase()
}

/** SManga.status is an int enum; this is its display form. */
fun statusLabel(status: Int): String? = when (status) {
    SManga.ONGOING -> "Ongoing"
    SManga.COMPLETED -> "Completed"
    SManga.LICENSED -> "Licensed"
    SManga.PUBLISHING_FINISHED -> "Publishing finished"
    SManga.CANCELLED -> "Cancelled"
    SManga.ON_HIATUS -> "On hiatus"
    else -> null
}
