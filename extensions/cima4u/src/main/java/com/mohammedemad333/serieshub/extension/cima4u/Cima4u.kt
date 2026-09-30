package com.mohammedemad333.serieshub.extension.cima4u

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.extractor.SeriesHubEmbedExtractor
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URI

class Cima4u : AnimeHttpSource() {

    override val id: Long = 0x43494D4134550001L
    override val name: String = "Cima4u"
    override val lang: String = "ar"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://c4u.top"

    override val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val embedExtractor by lazy {
        SeriesHubEmbedExtractor(client, USER_AGENT)
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        loadCatalog(if (page <= 1) baseUrl else "$baseUrl/page/$page/")

    override suspend fun getLatestUpdates(page: Int): AnimesPage =
        getPopularAnime(page)

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return getPopularAnime(page)

        val catalog = getPopularAnime(page)
        return AnimesPage(
            catalog.animes.filter { it.title.lowercase().contains(normalized) },
            catalog.hasNextPage,
        )
    }

    @Deprecated("Compatibility API")
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        Observable.fromCallable {
            loadCatalogBlocking(if (page <= 1) baseUrl else "$baseUrl/page/$page/")
        }

    @Deprecated("Compatibility API")
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        fetchPopularAnime(page)

    @Deprecated("Compatibility API")
    override fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> =
        Observable.fromCallable {
            val normalized = query.trim().lowercase()
            val catalog = loadCatalogBlocking(
                if (page <= 1) baseUrl else "$baseUrl/page/$page/",
            )
            if (normalized.isBlank()) {
                catalog
            } else {
                AnimesPage(
                    catalog.animes.filter { it.title.lowercase().contains(normalized) },
                    catalog.hasNextPage,
                )
            }
        }

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        val fullAnime = if (fetchDetails) loadDetails(anime) else anime
        val fullEpisodes = if (fetchEpisodes) loadEpisodes(fullAnime) else episodes
        return SAnimeEpisodeUpdate(fullAnime, fullEpisodes)
    }

    @Deprecated("Compatibility API")
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = loadDetails(anime)

    @Deprecated("Compatibility API")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
        loadEpisodes(anime)

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> =
        emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val episodeUrl = absolute(episode.url)
        val watchUrl = when {
            episodeUrl.contains("?") && !episodeUrl.contains("wat=") -> "$episodeUrl&wat=1"
            episodeUrl.contains("wat=") -> episodeUrl
            else -> "$episodeUrl?wat=1"
        }

        val html = fetch(watchUrl, referer = episodeUrl)
        val document = Jsoup.parse(html, watchUrl)

        val direct = extractVideos(html, document, watchUrl)
        val hosters = mutableListOf<Hoster>()
        if (direct.isNotEmpty()) {
            hosters += Hoster(
                hosterUrl = watchUrl,
                hosterName = "Cima4u",
                videoList = direct,
                lazy = false,
            )
        }

        serverCandidates(html, document, watchUrl)
            .filterNot { candidate -> candidate == watchUrl }
            .forEach { candidate ->
                hosters += Hoster(
                    hosterUrl = candidate,
                    hosterName = hostLabel(candidate),
                    videoList = null,
                    lazy = true,
                )
            }

        if (hosters.isEmpty()) {
            hosters += Hoster(
                hosterUrl = watchUrl,
                hosterName = "Cima4u",
                videoList = null,
                lazy = true,
            )
        }

        return hosters.distinctBy { it.hosterUrl }
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        val url = hoster.hosterUrl
        if (url.isBlank()) return emptyList()

        val hostSpecific = embedExtractor.videosFromUrl(url, baseUrl)
        if (hostSpecific.isNotEmpty()) return hostSpecific

        val firstHtml = fetch(url, referer = baseUrl)
        val firstDocument = Jsoup.parse(firstHtml, url)
        val direct = extractVideos(firstHtml, firstDocument, url)
        if (direct.isNotEmpty()) return direct

        val nested = serverCandidates(firstHtml, firstDocument, url)
            .filterNot { it == url }
            .take(MAX_NESTED_SERVERS)

        val result = mutableListOf<Video>()
        for (nestedUrl in nested) {
            runCatching {
                val extracted = embedExtractor.videosFromUrl(nestedUrl, url)
                if (extracted.isNotEmpty()) {
                    result += extracted
                } else {
                    val html = fetch(nestedUrl, referer = url)
                    val document = Jsoup.parse(html, nestedUrl)
                    result += extractVideos(html, document, nestedUrl)
                }
            }
        }
        return result.distinctBy { it.videoUrl }
    }

    private fun loadCatalog(url: String): AnimesPage = loadCatalogBlocking(url)

    private fun loadCatalogBlocking(url: String): AnimesPage {
        val html = fetch(url)
        val document = Jsoup.parse(html, url)
        val items = mutableListOf<SAnime>()
        val seen = mutableSetOf<String>()

        document.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank {
                resolve(url, anchor.attr("href"))
            }
            if (!sameSite(href)) return@forEach

            val title = mediaTitle(anchor)
            val thumbnail = imageUrl(anchor, url)
                ?: anchor.parent()?.let { imageUrl(it, url) }
            if (!looksLikeMedia(title)) return@forEach
            if (!looksLikeMediaUrl(href)) return@forEach
            if (!seen.add(href)) return@forEach

            items += SAnime.create().apply {
                this.url = href
                this.title = title
                this.thumbnail_url = thumbnail
                this.initialized = false
            }
        }

        return AnimesPage(
            animes = items,
            hasNextPage = document.selectFirst(
                "a[rel=next], .next a, a.next, .pagination a[href*=page]",
            ) != null,
        )
    }

    private fun loadDetails(anime: SAnime): SAnime {
        val pageUrl = absolute(anime.url)
        val html = fetch(pageUrl)
        val document = Jsoup.parse(html, pageUrl)

        return anime.copy().apply {
            url = pageUrl
            title = firstText(
                document,
                "h1",
                "[itemprop=name]",
                ".title",
                ".post-title",
            ).ifBlank { anime.title }
            thumbnail_url = document.selectFirst("meta[property=og:image]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { resolve(pageUrl, it) }
                ?: imageUrl(document, pageUrl)
                ?: anime.thumbnail_url
            description = document.selectFirst("meta[name=description]")
                ?.attr("content")
                ?.clean()
                ?.takeIf { it.isNotBlank() }
                ?: firstText(
                    document,
                    "[itemprop=description]",
                    ".description",
                    ".story",
                    ".plot",
                    ".summary",
                ).ifBlank { anime.description.orEmpty() }
            initialized = true
        }
    }

    private fun loadEpisodes(anime: SAnime): List<SEpisode> {
        val pageUrl = absolute(anime.url)
        val html = fetch(pageUrl)
        val document = Jsoup.parse(html, pageUrl)
        val result = mutableListOf<SEpisode>()
        val seen = mutableSetOf<String>()

        document.select("a[href]").forEach { anchor ->
            val target = anchor.absUrl("href").ifBlank {
                resolve(pageUrl, anchor.attr("href"))
            }
            if (!sameSite(target)) return@forEach

            val text = (
                anchor.attr("title").takeIf { it.isNotBlank() }
                    ?: anchor.text()
                ).clean()
            val number = episodeNumber(text, target) ?: return@forEach
            if (!text.contains("الحلقة") &&
                !Regex("""/(episode|watch|view)/""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(target)
            ) {
                return@forEach
            }
            if (!seen.add(target)) return@forEach

            result += SEpisode.create().apply {
                url = target
                name = text.ifBlank { "الحلقة $number" }
                episode_number = number.toFloat()
                preview_url = imageUrl(anchor, pageUrl)
            }
        }

        if (result.isNotEmpty()) {
            return result.sortedByDescending { it.episode_number }
        }

        val heading = firstText(document, "h1", ".title", ".post-title")
        val number = episodeNumber(heading, pageUrl) ?: 1
        return listOf(
            SEpisode.create().apply {
                url = pageUrl
                name = heading.ifBlank { "الحلقة $number" }
                episode_number = number.toFloat()
                preview_url = imageUrl(document, pageUrl)
            },
        )
    }

    private fun extractVideos(
        html: String,
        document: Document,
        pageUrl: String,
    ): List<Video> {
        val urls = linkedSetOf<String>()

        fun add(raw: String?) {
            val value = raw?.trim().orEmpty()
            if (value.isBlank()) return
            val decoded = value
                .replace("\\/", "/")
                .replace("&amp;", "&")
            val absolute = resolve(pageUrl, decoded)
            if (!MEDIA_PATTERN.containsMatchIn(absolute)) return
            urls += absolute
        }

        document.select("video[src]").forEach { add(it.attr("src")) }
        document.select("video source[src]").forEach { add(it.attr("src")) }
        document.select(
            "meta[property=og:video], " +
                "meta[property=og:video:url], " +
                "meta[property=og:video:secure_url], " +
                "meta[itemprop=contentUrl]",
        ).forEach { add(it.attr("content")) }

        MEDIA_URL_PATTERN.findAll(html).forEach { match ->
            add(match.value)
        }

        return urls.map { url ->
            Video(
                videoUrl = url,
                videoTitle = qualityLabel(url),
                headers = mediaHeaders(pageUrl),
                preferred = url.contains(".m3u8", ignoreCase = true),
                initialized = true,
            )
        }
    }

    private fun serverCandidates(
        html: String,
        document: Document,
        pageUrl: String,
    ): List<String> {
        val result = linkedSetOf<String>()

        document.select(
            "iframe, [data-src], [data-lazy-src], [data-url], " +
                "[data-link], [data-embed], [data-server]",
        ).forEach { element ->
            SERVER_ATTRIBUTES.forEach { attribute ->
                val value = element.attr(attribute).trim()
                if (value.isNotBlank()) {
                    val candidate = resolve(pageUrl, value)
                    if (isHttp(candidate) && !STATIC_ASSET_PATTERN.containsMatchIn(candidate)) {
                        result += candidate
                    }
                }
            }
        }

        HTTP_URL_PATTERN.findAll(html).forEach { match ->
            val candidate = match.value.replace("\\/", "/")
            if (isHttp(candidate) && !STATIC_ASSET_PATTERN.containsMatchIn(candidate)) {
                result += candidate
            }
        }

        return result
            .sortedBy { serverPriority(it) }
            .take(MAX_SERVERS)
    }

    private fun serverPriority(url: String): Int {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        val index = PREFERRED_HOSTS.indexOfFirst { preferred ->
            host == preferred || host.endsWith(".$preferred")
        }
        return if (index >= 0) index else PREFERRED_HOSTS.size + 1
    }

    private fun mediaTitle(anchor: Element): String = (
        anchor.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: anchor.attr("title").takeIf { it.isNotBlank() }
            ?: anchor.text()
        ).clean()

    private fun looksLikeMedia(title: String): Boolean {
        if (title.length < 4) return false
        if (NAVIGATION_TITLES.any { it.equals(title, ignoreCase = true) }) return false
        return MEDIA_TITLE_PATTERN.containsMatchIn(title)
    }

    private fun looksLikeMediaUrl(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        if (path.isBlank() || path == "/") return false
        return NAVIGATION_PATHS.none { marker -> path.startsWith(marker) }
    }

    private fun episodeNumber(text: String, url: String): Int? {
        EPISODE_TEXT_PATTERN.find(text)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        return EPISODE_URL_PATTERN.find(url)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun firstText(document: Document, vararg selectors: String): String {
        selectors.forEach { selector ->
            val value = document.selectFirst(selector)?.text()?.clean().orEmpty()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun imageUrl(scope: Element, base: String): String? {
        val image = scope.selectFirst(
            "img[data-src], img[data-lazy-src], img[data-original], " +
                "img[data-srcset], img[srcset], img[src]",
        )

        val raw = image?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: image?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
            ?: image?.attr("data-original")?.takeIf { it.isNotBlank() }
            ?: image?.attr("data-srcset")?.takeIf { it.isNotBlank() }?.srcsetFirst()
            ?: image?.attr("srcset")?.takeIf { it.isNotBlank() }?.srcsetFirst()
            ?: image?.attr("src")?.takeIf { it.isNotBlank() }
            ?: STYLE_IMAGE_PATTERN.find(scope.attr("style"))
                ?.groupValues
                ?.getOrNull(1)
            ?: scope.selectFirst("[style*=background-image]")
                ?.attr("style")
                ?.let { STYLE_IMAGE_PATTERN.find(it)?.groupValues?.getOrNull(1) }
            ?: return null

        return resolve(base, raw.replace("&amp;", "&"))
    }

    private fun String.srcsetFirst(): String =
        split(",")
            .firstOrNull()
            ?.trim()
            ?.substringBefore(" ")
            .orEmpty()

    private fun fetch(url: String, referer: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ar,en;q=0.8")
            .apply {
                if (!referer.isNullOrBlank()) {
                    header("Referer", referer)
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Cima4u returned HTTP ${response.code} for $url")
            }
            return response.body.string()
        }
    }

    private fun mediaHeaders(referer: String): Headers =
        Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Referer", referer)
            .build()

    private fun absolute(url: String): String =
        if (isHttp(url)) url else resolve(baseUrl, url)

    private fun sameSite(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "c4u.top" || host.endsWith(".c4u.top")
    }

    private fun resolve(base: String, value: String): String =
        runCatching { URI(base).resolve(value.trim()).toString() }
            .getOrDefault(value.trim())

    private fun isHttp(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")

    private fun hostLabel(url: String): String =
        runCatching { URI(url).host.orEmpty() }
            .getOrDefault("")
            .ifBlank { "Server" }

    private fun qualityLabel(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "HLS"
        url.contains(".mpd", ignoreCase = true) -> "DASH"
        url.contains(".webm", ignoreCase = true) -> "WebM"
        else -> "MP4"
    }

    private fun String.clean(): String = replace(Regex("""\s+"""), " ").trim()

    private companion object {
        const val MAX_SERVERS = 10
        const val MAX_NESTED_SERVERS = 5
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        val PREFERRED_HOSTS = listOf(
            "hglink.to",
            "dood.li",
            "minochinos.com",
        )

        val SERVER_ATTRIBUTES = listOf(
            "src",
            "data-src",
            "data-lazy-src",
            "data-url",
            "data-link",
            "data-embed",
            "data-server",
        )

        val NAVIGATION_TITLES = setOf(
            "افلام",
            "افلام اجنبي",
            "افلام اسيوي",
            "افلام انمي",
            "مسلسلات",
            "مسلسلات اجنبي",
            "مسلسلات اسيوية",
            "مسلسلات انمي",
            "الأكثر مشاهدة",
            "الاكثر مشاهدة",
            "الأعلى تقييما",
            "الاعلى تقييما",
        )

        val NAVIGATION_PATHS = listOf(
            "/category/",
            "/genre/",
            "/quality/",
            "/year/",
            "/tag/",
            "/actor/",
            "/director/",
            "/country/",
            "/language/",
            "/page/",
        )

        val MEDIA_TITLE_PATTERN = Regex(
            """(فيلم|مسلسل|الحلقة|مشاهدة|movie|film|series|episode)""",
            RegexOption.IGNORE_CASE,
        )
        val EPISODE_TEXT_PATTERN = Regex(
            """(?:الحلقة|episode|ep)\s*[-:#]?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )
        val EPISODE_URL_PATTERN = Regex(
            """(?:e|episode|ep)[-_]?(\d+)(?:\D|$)""",
            RegexOption.IGNORE_CASE,
        )
        val MEDIA_PATTERN = Regex(
            """\.(m3u8|mpd|mp4|webm)(?:$|\?)""",
            RegexOption.IGNORE_CASE,
        )
        val MEDIA_URL_PATTERN = Regex(
            """https?:\\?/\\?/[^"'\s<>]+?\.(?:m3u8|mpd|mp4|webm)(?:\?[^"'\s<>]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val HTTP_URL_PATTERN = Regex(
            """https?:\\?/\\?/[^"'\s<>]+""",
            RegexOption.IGNORE_CASE,
        )
        val STYLE_IMAGE_PATTERN = Regex(
            """background-image\s*:\s*url\(['"]?([^'")]+)""",
            RegexOption.IGNORE_CASE,
        )
        val STATIC_ASSET_PATTERN = Regex(
            """\.(?:jpg|jpeg|png|gif|webp|css|js)(?:$|\?)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
