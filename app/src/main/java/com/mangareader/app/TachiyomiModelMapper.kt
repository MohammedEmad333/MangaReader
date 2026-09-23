package com.mangareader.app

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Translates Tachiyomi extension models into MangaReader domain models and back.
 */
internal class TachiyomiModelMapper(
    private val delegate: CatalogueSource,
) {
    fun urlFromId(id: String): String? {
        val prefix = "${delegate.id}:"
        if (!id.startsWith(prefix)) return null
        return id.removePrefix(prefix).takeIf { it.isNotBlank() }
    }

    fun stubManga(url: String): SManga = SMangaImpl().apply {
        this.url = url
        this.title = ""
    }

    fun restoredSeries(url: String, title: String): Series =
        toSeries(
            SMangaImpl().apply {
                this.url = url
                this.title = title
                this.initialized = true
            }
        )

    fun ensureUrl(manga: SManga, fallback: String) {
        if (safeUrl(manga).isBlank()) manga.url = fallback
    }

    fun safeUrl(manga: SManga): String =
        runCatching { manga.url }.getOrDefault("")

    fun safeTitle(manga: SManga): String =
        runCatching { manga.title }.getOrDefault("")

    fun toSeriesPage(page: MangasPage): SeriesPage =
        SeriesPage(
            series = page.mangas.map(::toSeries),
            hasNext = page.hasNextPage
        )

    fun toSeries(manga: SManga): Series = Series(
        id = "${delegate.id}:${safeUrl(manga)}",
        title = safeTitle(manga),
        cover = manga.thumbnail_url?.repointFromLoopback(),
        handle = manga,
        author = manga.author?.takeIf { it.isNotBlank() },
        artist = manga.artist?.takeIf { it.isNotBlank() },
        description = manga.description?.takeIf { it.isNotBlank() },
        genres = manga.getGenres().orEmpty(),
        status = statusLabel(manga.status),
    )

    fun rehydrateChapter(chapter: Chapter): Chapter {
        if (chapter.handle is SChapter) return chapter
        val url = urlFromId(chapter.id) ?: return chapter
        return chapter.copy(
            handle = SChapterImpl().apply {
                this.url = url
                this.name = chapter.name
            }
        )
    }

    fun toChapter(chapter: SChapter, seriesTitle: String = ""): Chapter {
        val chapterUrl = safeUrl(chapter)
        val chapterName = safeName(chapter)
        return Chapter(
            id = "${delegate.id}:$chapterUrl",
            name = chapterName.ifBlank {
                chapterUrl.trimEnd('/').substringAfterLast('/')
            },
            handle = chapter,
            dateUploaded = chapter.date_upload,
            scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
            number = ChapterRecognition.parse(
                seriesTitle,
                chapterName,
                chapter.chapter_number
            ),
        )
    }

    private fun safeUrl(chapter: SChapter): String =
        runCatching { chapter.url }.getOrDefault("")

    private fun safeName(chapter: SChapter): String =
        runCatching { chapter.name }.getOrDefault("")

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
        this == "localhost" ||
            this == "::1" ||
            this == "0.0.0.0" ||
            startsWith("127.")
}
