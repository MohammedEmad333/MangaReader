package com.mangareader.app

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    internal val catalogueSource: AnimeCatalogueSource,
    override val iconPkg: String? = null,
    override val isNsfw: Boolean = false,
) : Source {

    private data class CachedVideos(
        val videos: List<Video>,
        val expiresAtElapsedMs: Long,
    )

    private val videoCache = mutableMapOf<String, CachedVideos>()

    override val id: String = "aniyomi:${catalogueSource.id}"
    override val name: String = catalogueSource.name
    override val lang: String = langLabel(catalogueSource.lang)
    override val isAnime: Boolean = true
    override val supportsSearch: Boolean = true
    override val supportsPaging: Boolean = true
    override val supportsLatest: Boolean =
        runCatching { catalogueSource.supportsLatest }.getOrDefault(false)

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
        catalogueSource.getPopularAnime(page).let {
            SeriesPage(it.animes.map(::toSeries), it.hasNextPage)
        }
    }

    override suspend fun latestSeries(page: Int): SeriesPage = onSourceThread {
        catalogueSource.getLatestUpdates(page).let {
            SeriesPage(it.animes.map(::toSeries), it.hasNextPage)
        }
    }

    override suspend fun searchSeries(query: String, page: Int): SeriesPage = onSourceThread {
        catalogueSource.getSearchAnime(page, query, AnimeFilterList()).let {
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
        val full = catalogueSource.getAnimeEpisodeUpdate(
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
            catalogueSource.getAnimeEpisodeUpdate(
                anime = anime,
                episodes = emptyList(),
                fetchDetails = false,
                fetchEpisodes = true,
            ).episodes
        }.getOrElse {
            @Suppress("DEPRECATION")
            catalogueSource.getEpisodeList(anime)
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
                    resumeKey = chapterKeyOf(id, chapter),
                    subtitles = video.subtitleTracks.map { track ->
                        VideoSubtitle(track.url, track.lang)
                    },
                    episodeTitle = chapter.name,
                )
            },
        )
    }

    private suspend fun resolveVideos(episode: SEpisode): List<Video> {
        val cacheKey = episode.url
        val now = SystemClock.elapsedRealtime()
        synchronized(videoCache) {
            videoCache[cacheKey]
                ?.takeIf { it.expiresAtElapsedMs > now }
                ?.let { return it.videos }
            videoCache.entries.removeAll { it.value.expiresAtElapsedMs <= now }
        }

        val hosters = runCatching { catalogueSource.getHosterList(episode) }
            .getOrNull()
            .orEmpty()

        // Hoster resolution is network-bound and independent. The old flatMap
        // resolved one hoster after another, so three 4-second servers meant a
        // 12-second loading dialog. Start them together and isolate failures so
        // one broken server cannot hide streams returned by the others.
        val fromHosters = supervisorScope {
            hosters.map { hoster ->
                async(Dispatchers.IO) {
                    hoster.videoList ?: withTimeoutOrNull(HOSTER_TIMEOUT_MS) {
                        runCatching { catalogueSource.getVideoList(hoster) }
                            .getOrDefault(emptyList())
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }

        val resolved = if (fromHosters.isNotEmpty()) {
            fromHosters
        } else {
            @Suppress("DEPRECATION")
            runCatching { catalogueSource.getVideoList(episode) }.getOrDefault(emptyList())
        }

        // Stream URLs can be signed/short-lived, so this is intentionally a
        // short process cache: it makes closing/reopening the same episode
        // instant without keeping a stale URL around for a later session.
        if (resolved.isNotEmpty()) {
            synchronized(videoCache) {
                videoCache[cacheKey] = CachedVideos(
                    videos = resolved,
                    expiresAtElapsedMs = SystemClock.elapsedRealtime() + STREAM_CACHE_TTL_MS,
                )
            }
        }
        return resolved
    }

    private companion object {
        const val HOSTER_TIMEOUT_MS = 10_000L
        const val STREAM_CACHE_TTL_MS = 2 * 60_000L
    }

    override fun seriesUrl(series: Series): String? {
        val anime = series.handle as? SAnime ?: return null
        val http = catalogueSource as? AnimeHttpSource ?: return null
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
        "anime:${catalogueSource.id}:${anime.url}"

    private fun episodeId(episode: SEpisode): String =
        "episode:${catalogueSource.id}:${episode.url}"

    private fun urlFromId(id: String): String? {
        val prefix = "anime:${catalogueSource.id}:"
        return id.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
    }
}
