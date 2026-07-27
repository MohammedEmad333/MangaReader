package com.mangareader.app

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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

    override val supportsDownload: Boolean = true

    /** First page of popular, for callers that just want a quick look. */
    override suspend fun listSeries(): List<Series> = browseSeries(1).series

    override suspend fun browseSeries(page: Int): SeriesPage = withContext(Dispatchers.IO) {
        delegate.getPopularManga(page).toSeriesPage()
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage =
        withContext(Dispatchers.IO) {
            delegate.getSearchManga(page, query, FilterList()).toSeriesPage()
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
            return@withContext
        }
        val pages = delegate.getPageList(sChapter)
        val dir = if (persist) Downloads.dirFor(context, chapter.id)
        else Downloads.cacheDirFor(context, chapter.id)
        dir.mkdirs()

        val done = arrayOfNulls<File>(pages.size)
        onUpdate(done.toList())

        pages.chunked(PAGE_CONCURRENCY).forEachIndexed { batch, chunk ->
            val base = batch * PAGE_CONCURRENCY
            chunk.mapIndexed { offset, page ->
                async { runCatching { downloadPage(page, dir, base + offset) }.getOrNull() }
            }.awaitAll().forEachIndexed { offset, file ->
                done[base + offset] = file
            }
            onUpdate(done.toList())
        }

        // Only a chapter with every page present counts as downloaded; a partial
        // one stays unmarked so it can be resumed rather than trusted.
        if (persist && pages.isNotEmpty() && done.all { it != null }) {
            Downloads.markComplete(context, chapter.id, pages.size)
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

    private suspend fun downloadPage(page: TachiPage, dir: File, index: Int): File {
        val http = delegate as? HttpSource

        // Some sources return pages without a direct image URL; it has to be
        // resolved with a second request first.
        if (page.imageUrl.isNullOrEmpty() && http != null) {
            page.imageUrl = http.getImageUrl(page)
        }

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
        cover = thumbnail_url,
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

        /** Pages fetched in parallel. Enough to hide latency, not enough to look
         *  like a scraper to the source. */
        const val PAGE_CONCURRENCY = 4
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
