package eu.kanade.tachiyomi.animesource.extractor

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import kotlin.random.Random

/**
 * Small host-dispatch extractor for SeriesHub sources.
 *
 * The design follows current Aniyomi/Yuzono extensions: detect the embed host,
 * then use a host-specific extractor instead of treating every iframe as if it
 * exposed a direct media URL.
 */
class SeriesHubEmbedExtractor(
    private val client: OkHttpClient,
    private val userAgent: String,
) {
    fun videosFromUrl(url: String, referer: String? = null): List<Video> {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host.contains("streamtape") -> streamTape(url)
            host.contains("dood") -> dood(url)
            host.contains("vidmoly") -> vidMoly(url)
            host == "ok.ru" || host.endsWith(".ok.ru") -> okRu(url)
            isStreamWishHost(host) -> streamWish(url)
            host.contains("mixdrop") -> mixDrop(url)
            isVoeHost(host) -> voe(url)
            else -> generic(url, referer)
        }
    }

    private fun streamTape(url: String): List<Video> = runCatching {
        val parts = url.split("/")
        val id = parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val embed = "https://streamtape.com/e/$id"
        val html = request(embed)
        val marker = "document.getElementById('robotlink')"
        val script = Jsoup.parse(html, embed)
            .selectFirst("script:containsData($marker)")
            ?.data()
            ?: return emptyList()
        val first = script.substringAfter("$marker.innerHTML = '", "")
        if (first.isBlank()) return emptyList()
        val videoUrl = "https:" + first.substringBefore("'") +
            first.substringAfter("+ ('xcd", "").substringBefore("'")
        if (!videoUrl.startsWith("http")) return emptyList()
        listOf(video(videoUrl, "StreamTape", embed))
    }.getOrDefault(emptyList())

    private fun dood(url: String): List<Video> = runCatching {
        val response = execute(url)
        val finalUrl = response.first
        val html = response.second
        val host = URI(finalUrl).let { "${it.scheme}://${it.host}" }
        val passPath = Regex("""/pass_md5/[^']+""").find(html)?.value ?: return emptyList()
        val passUrl = host + passPath
        val token = passUrl.substringAfterLast("/")
        val start = request(
            passUrl,
            Headers.headersOf("Referer", finalUrl, "User-Agent", userAgent),
        ).trim()
        if (!start.startsWith("http")) return emptyList()
        val random = buildString {
            repeat(10) {
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                append(chars[Random.nextInt(chars.length)])
            }
        }
        val expiry = System.currentTimeMillis()
        val videoUrl = "$start$random?token=$token&expiry=$expiry"
        val headers = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Referer", "$host/")
            .build()
        listOf(
            Video(
                videoUrl = videoUrl,
                videoTitle = "DoodStream",
                headers = headers,
                initialized = true,
            ),
        )
    }.getOrDefault(emptyList())

    private fun vidMoly(url: String): List<Video> = runCatching {
        val html = request(
            url,
            Headers.Builder()
                .add("User-Agent", userAgent)
                .add("Referer", "https://vidmoly.biz/")
                .add("Origin", "https://vidmoly.biz")
                .build(),
        )
        val script = Jsoup.parse(html, url)
            .selectFirst("script:containsData(sources)")
            ?.data()
            ?: return emptyList()
        Regex("""file\s*:\s*["'](.+?)["']""")
            .findAll(script)
            .map { it.groupValues[1].replace("\\/","/") }
            .filter { it.startsWith("http") }
            .distinct()
            .map { media ->
                video(media, if (media.contains(".m3u8", true)) "VidMoly HLS" else "VidMoly", url)
            }
            .toList()
    }.getOrDefault(emptyList())

    private fun voe(url: String): List<Video> = runCatching {
        var pageUrl = url
        var html = request(pageUrl)
        val redirect = Regex("""window\.location\.href\s*=\s*'([^']+)'""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
        if (!redirect.isNullOrBlank()) {
            pageUrl = redirect
            html = request(pageUrl)
        }
        val data = Jsoup.parse(html, pageUrl)
            .selectFirst("script[type=application/json]")
            ?.data()
            ?.trim()
            ?.substringAfter("[\"", "")
            ?.substringBeforeLast("\"]", "")
            ?: return emptyList()
        if (data.isBlank()) return emptyList()
        val decoded = decryptVoe(data) ?: return emptyList()
        val urls = linkedSetOf<String>()
        Regex("""["']source["']\s*:\s*["']([^"']+)["']""").find(decoded)
            ?.groupValues?.getOrNull(1)?.let(urls::add)
        Regex("""["']direct_access_url["']\s*:\s*["']([^"']+)["']""").find(decoded)
            ?.groupValues?.getOrNull(1)?.let(urls::add)
        urls.map { raw ->
            val media = raw.replace("\\/","/")
            video(media, if (media.contains(".m3u8", true)) "VOE HLS" else "VOE MP4", pageUrl)
        }
    }.getOrDefault(emptyList())


    private fun okRu(url: String): List<Video> = runCatching {
        val html = request(url)
        val doc = Jsoup.parse(html, url)
        val data = doc.selectFirst("div[data-options]")
            ?.attr("data-options")
            ?: return emptyList()

        val unescaped = data
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("\\\"", "\"")

        val direct = linkedMapOf<String, String>()
        Regex(""""name"\s*:\s*"([^"]+)"[^{}]*?"url"\s*:\s*"([^"]+)"""")
            .findAll(unescaped)
            .forEach { match ->
                val quality = okRuQuality(match.groupValues[1])
                val media = match.groupValues[2]
                if (media.startsWith("http")) direct[media] = "OK.ru $quality"
            }

        if (direct.isNotEmpty()) {
            return direct.map { (media, title) -> video(media, title, url) }
        }

        val manifest = listOf("ondemandHls", "ondemandDash")
            .firstNotNullOfOrNull { key ->
                Regex(""""$key"\s*:\s*"([^"]+)"""")
                    .find(unescaped)
                    ?.groupValues
                    ?.getOrNull(1)
            }
            ?: return emptyList()

        listOf(
            video(
                manifest,
                if (manifest.contains(".mpd", true)) "OK.ru DASH" else "OK.ru HLS",
                url,
            ),
        )
    }.getOrDefault(emptyList())

    private fun streamWish(url: String): List<Video> = runCatching {
        val html = request(url)
        val doc = Jsoup.parse(html, url)
        val scripts = doc.select("script").joinToString("\n") { it.data() }

        val media = Regex("""https?[^"'\\\s]+\.m3u8[^"'\\\s]*""", RegexOption.IGNORE_CASE)
            .find(scripts)
            ?.value
            ?.replace("\\/", "/")
            ?: return emptyList()

        listOf(video(media, "StreamWish HLS", url))
    }.getOrDefault(emptyList())

    private fun mixDrop(url: String): List<Video> = runCatching {
        val headers = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Referer", "https://mixdrop.co/")
            .build()
        val html = request(url, headers)

        val media = sequenceOf(
            Regex("""(?:MDCore|Core)\.wurl\s*=\s*["']([^"']+)["']"""),
            Regex("""wurl\s*:\s*["']([^"']+)["']"""),
            Regex("""https?:\\?/\\?/[^"'\\s<>]+?\.mp4(?:\?[^"'\\s<>]*)?""", RegexOption.IGNORE_CASE),
        ).mapNotNull { regex ->
            regex.find(html)?.let { match ->
                when {
                    match.groupValues.size > 1 -> match.groupValues[1]
                    else -> match.value
                }
            }
        }.firstOrNull() ?: return emptyList()

        val normalized = when {
            media.startsWith("//") -> "https:$media"
            media.startsWith("http") -> media
            else -> "https:$media"
        }.replace("\\/", "/")

        listOf(
            Video(
                videoUrl = normalized,
                videoTitle = "MixDrop",
                headers = headers,
                initialized = true,
            ),
        )
    }.getOrDefault(emptyList())

    private fun okRuQuality(value: String): String = when (value.lowercase()) {
        "ultra" -> "2160p"
        "quad" -> "1440p"
        "full" -> "1080p"
        "hd" -> "720p"
        "sd" -> "480p"
        "low" -> "360p"
        "lowest" -> "240p"
        "mobile" -> "144p"
        else -> value
    }

    private fun generic(url: String, referer: String?): List<Video> = runCatching {
        val html = request(
            url,
            Headers.Builder().add("User-Agent", userAgent).apply {
                if (!referer.isNullOrBlank()) add("Referer", referer)
            }.build(),
        )
        val doc = Jsoup.parse(html, url)
        val urls = linkedSetOf<String>()
        doc.select("video[src], video source[src]").forEach {
            val raw = it.attr("src")
            val absolute = runCatching { URI(url).resolve(raw).toString() }.getOrDefault(raw)
            if (MEDIA.containsMatchIn(absolute)) urls += absolute
        }
        MEDIA_URL.findAll(html).forEach { match ->
            urls += match.value.replace("\\/","/")
        }
        urls.map { media ->
            video(media, quality(media), url)
        }
    }.getOrDefault(emptyList())

    private fun video(url: String, title: String, referer: String): Video =
        Video(
            videoUrl = url,
            videoTitle = title,
            headers = Headers.Builder()
                .add("User-Agent", userAgent)
                .add("Referer", referer)
                .build(),
            preferred = url.contains(".m3u8", true),
            initialized = true,
        )

    private fun quality(url: String): String = when {
        url.contains(".m3u8", true) -> "HLS"
        url.contains(".mpd", true) -> "DASH"
        url.contains(".webm", true) -> "WebM"
        else -> "MP4"
    }

    private fun request(url: String, headers: Headers? = null): String =
        execute(url, headers).second

    private fun execute(url: String, headers: Headers? = null): Pair<String, String> {
        val request = Request.Builder().url(url).apply {
            if (headers != null) headers(headers)
            else header("User-Agent", userAgent)
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.request.url.toString() to response.body.string()
        }
    }

    private fun decryptVoe(input: String): String? = runCatching {
        val rot = input.map { c ->
            when (c) {
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                else -> c
            }
        }.joinToString("")
        val replaced = rot.replace(PATTERNS, "_").replace("_", "")
        val first = String(Base64.decode(replaced, Base64.DEFAULT), Charsets.ISO_8859_1)
        val shifted = first.map { (it.code - 3).toChar() }.joinToString("").reversed()
        String(Base64.decode(shifted, Base64.DEFAULT), Charsets.ISO_8859_1)
    }.getOrNull()

    private fun isVoeHost(host: String): Boolean =
        host == "voe.sx" || host.startsWith("voe.") ||
            host.contains("voe") || host.contains("tubeless") ||
            host.contains("simpulum") || host.contains("urochs")

    private fun isStreamWishHost(host: String): Boolean =
        host.contains("streamwish") ||
            host.contains("niramirus") ||
            host.contains("medixiru")

    private companion object {
        val PATTERNS = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
            .joinToString("|") { Regex.escape(it) }
            .toRegex()
        val MEDIA = Regex("""\.(m3u8|mpd|mp4|webm)(?:$|\?)""", RegexOption.IGNORE_CASE)
        val MEDIA_URL = Regex(
            """https?:\\?/\\?/[^"'\s<>]+?\.(?:m3u8|mpd|mp4|webm)(?:\?[^"'\s<>]*)?""",
            RegexOption.IGNORE_CASE,
        )
    }
}
