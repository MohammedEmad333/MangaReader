package com.mangareader.app

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Bridges an Aniyomi anime extension into Yomu's existing browse/series shell.
 *
 * Episodes deliberately reuse [Chapter] at the app boundary. The important
 * difference is [isAnime]: tapping an episode resolves videos and opens the
 * player instead of entering the page reader.
 */
class AniyomiSourceAdapter(
    private val delegate: AnimeCatalogueSource,
    override val iconPkg: String? = null,
    override val isNsfw: Boolean = false,
) : Source {

    override val id: String = "aniyomi:${delegate.id}"
    override val name: String = delegate.name
    override val lang: String = langLabel(delegate.lang)
    override val isAnime: Boolean = true
    override val supportsSearch: Boolean = true
    override val supportsPaging: Boolean = true
    override val supportsLatest: Boolean =
        runCatching { delegate.supportsLatest }.getOrDefault(false)

    private suspend fun <T> onSourceThread(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: LinkageError) {
                throw IOException(
                    "This anime extension needs a newer Aniyomi source API — " +
                        "${e.javaClass.simpleName}: ${e.message ?: "missing symbol"}",
                    e,
                )
            }
        }

    override suspend fun listSeries(): List<Series> = browseSeries(1).series

    override suspend fun browseSeries(page: Int): SeriesPage = onSourceThread {
        delegate.getPopularAnime(page).let {
            SeriesPage(it.animes.map(::toSeries), it.hasNextPage)
        }
    }

    override suspend fun latestSeries(page: Int): SeriesPage = onSourceThread {
        delegate.getLatestUpdates(page).let {
            SeriesPage(it.animes.map(::toSeries), it.hasNextPage)
        }
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage = onSourceThread {
        delegate.getSearchAnime(page, query, AnimeFilterList()).let {
            SeriesPage(it.animes.map(::toSeries), it.hasNextPage)
        }
    }

    override suspend fun restoreSeries(id: String, title: String): Series? {
        val url = urlFromId(id) ?: return null
        return toSeries(SAnime.create().apply {
            this.url = url
            this.title = title
            initialized = true
        })
    }

    override suspend fun getSeries(id: String): Series? =
        restoreSeries(id, id.substringAfterLast('/').ifBlank { "Anime" })

    override suspend fun loadDetails(series: Series): Series = onSourceThread {
        val anime = series.handle as? SAnime ?: return@onSourceThread series
        val full = delegate.getAnimeEpisodeUpdate(
            anime = anime,
            episodes = emptyList(),
            fetchDetails = true,
            fetchEpisodes = false,
        ).anime
        val enriched = toSeries(full)
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

    override suspend fun listChapters(series: Series): List<Chapter> = onSourceThread {
        val anime = series.handle as? SAnime ?: return@onSourceThread emptyList()
        val episodes = runCatching {
            delegate.getAnimeEpisodeUpdate(
                anime = anime,
                episodes = emptyList(),
                fetchDetails = false,
                fetchEpisodes = true,
            ).episodes
        }.getOrElse {
            @Suppress("DEPRECATION")
            delegate.getEpisodeList(anime)
        }

        episodes.asReversed().map { episode ->
            Chapter(
                id = episodeId(episode),
                name = episode.name,
                handle = episode,
                dateUploaded = episode.date_upload,
                scanlator = episode.scanlator,
                number = episode.episode_number.takeIf { it >= 0f } ?: Chapter.NO_NUMBER,
            )
        }
    }

    override suspend fun loadPages(chapter: Chapter): List<File> = emptyList()

    override suspend fun scanVideos(chapter: Chapter): VideoScan = onSourceThread {
        val episode = chapter.handle as? SEpisode
            ?: return@onSourceThread VideoScan(emptyList(), note = "Episode handle is unavailable.")

        val videos = resolveVideos(episode)
            .filter { it.videoUrl.isNotBlank() }
            .sortedWith(
                compareByDescending<Video> { it.preferred }
                    .thenByDescending { it.resolution ?: 0 },
            )
            .distinctBy { it.videoUrl }

        VideoScan(
            links = videos.map { it.videoUrl },
            note = if (videos.isEmpty()) {
                "The anime extension returned no playable video for this episode."
            } else {
                "Found ${videos.size} stream${if (videos.size == 1) "" else "s"} from the anime extension."
            },
            videos = videos.map { video ->
                PlayableVideo(
                    url = video.videoUrl,
                    title = video.videoTitle,
                    headers = video.headers?.toMap().orEmpty(),
                    resumeKey = chapter.id,
                    subtitles = video.subtitleTracks.map { track ->
                        VideoSubtitle(track.url, track.lang)
                    },
                )
            },
        )
    }

    private suspend fun resolveVideos(episode: SEpisode): List<Video> {
        val fromHosters = runCatching {
            delegate.getHosterList(episode).flatMap { hoster ->
                hoster.videoList ?: delegate.getVideoList(hoster)
            }
        }.getOrNull().orEmpty()

        if (fromHosters.isNotEmpty()) return fromHosters

        @Suppress("DEPRECATION")
        return runCatching { delegate.getVideoList(episode) }.getOrDefault(emptyList())
    }

    override fun seriesUrl(series: Series): String? {
        val anime = series.handle as? SAnime ?: return null
        val http = delegate as? AnimeHttpSource ?: return null
        return runCatching { http.getAnimeUrl(anime) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun toSeries(anime: SAnime): Series = Series(
        id = seriesId(anime),
        title = runCatching { anime.title }.getOrDefault("Anime"),
        cover = anime.thumbnail_url,
        handle = anime,
        author = anime.author,
        artist = anime.artist,
        description = anime.description,
        genres = anime.getGenres().orEmpty(),
        status = when (anime.status) {
            SAnime.ONGOING -> "Ongoing"
            SAnime.COMPLETED -> "Completed"
            SAnime.LICENSED -> "Licensed"
            SAnime.CANCELLED -> "Cancelled"
            SAnime.ON_HIATUS -> "On hiatus"
            SAnime.UPCOMING -> "Upcoming"
            else -> null
        },
    )

    private fun seriesId(anime: SAnime): String =
        "anime:${delegate.id}:${anime.url}"

    private fun episodeId(episode: SEpisode): String =
        "episode:${delegate.id}:${episode.url}"

    private fun urlFromId(id: String): String? {
        val prefix = "anime:${delegate.id}:"
        return id.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
    }
}
