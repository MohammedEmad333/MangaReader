package com.mangareader.app

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
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
) : Source {

    override val id: String = "tachi:${delegate.id}"

    override val name: String = "${delegate.name} (${delegate.lang})"

    override val supportsSearch: Boolean = true

    override val supportsPaging: Boolean = true

    /** First page of popular, for callers that just want a quick look. */
    override suspend fun listSeries(): List<Series> = browseSeries(1).series

    override suspend fun browseSeries(page: Int): SeriesPage = withContext(Dispatchers.IO) {
        delegate.getPopularManga(page).toSeriesPage()
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage =
        withContext(Dispatchers.IO) {
            delegate.getSearchManga(page, query, FilterList()).toSeriesPage()
        }

    /**
     * Rebuilds the SManga from the id instead of paging the catalogue, so a
     * library entry reopens even when the series has dropped off page one.
     * The id format is "<sourceId>:<url>", and url is all HttpSource needs.
     */
    override suspend fun getSeries(id: String): Series? = withContext(Dispatchers.IO) {
        val prefix = "${delegate.id}:"
        if (!id.startsWith(prefix)) return@withContext null
        val url = id.removePrefix(prefix)
        if (url.isBlank()) return@withContext null

        val stub: SManga = SMangaImpl().apply { this.url = url }
        val full = runCatching { delegate.getMangaDetails(stub) }.getOrDefault(stub)
        // getMangaDetails often leaves url blank on the returned copy.
        if (full.url.isBlank()) full.url = url
        full.toSeries()
    }

    override suspend fun listChapters(series: Series): List<Chapter> = withContext(Dispatchers.IO) {
        val manga = series.handle as? SManga ?: return@withContext emptyList()
        // Extensions return newest-first; this interface wants reading order.
        delegate.getChapterList(manga).asReversed().map { it.toChapter() }
    }

    override suspend fun loadPages(chapter: Chapter): List<File> = withContext(Dispatchers.IO) {
        val sChapter = chapter.handle as? SChapter ?: return@withContext emptyList()
        val pages = delegate.getPageList(sChapter)

        val dir = File(context.cacheDir, "pages/${chapter.id.hashCode()}").apply { mkdirs() }

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

    private fun SManga.toSeries() = Series(
        id = "${delegate.id}:$url",
        title = title,
        cover = thumbnail_url,
        handle = this,
    )

    private fun SChapter.toChapter() = Chapter(
        id = "${delegate.id}:$url",
        name = name,
        handle = this,
    )

    private companion object {
        val fallbackClient = OkHttpClient()
    }
}
