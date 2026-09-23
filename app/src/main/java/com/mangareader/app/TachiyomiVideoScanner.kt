package com.mangareader.app

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import org.jsoup.Jsoup

/**
 * Scans extension-backed chapter pages for direct video media and embedded
 * players. Kept separate from catalogue adaptation because it is HTML/media I/O,
 * not model translation.
 */
internal class TachiyomiVideoScanner(
    private val delegate: CatalogueSource,
) {
    suspend fun scan(chapter: Chapter): VideoScan {
        val http = delegate as? HttpSource
            ?: return VideoScan(emptyList(), note = "Not an HTTP source.")
        val sChapter = chapter.handle as? SChapter
            ?: return VideoScan(emptyList(), note = "This chapter has no source handle.")
        val url = http.baseUrl + sChapter.url

        val body = http.client.newCall(GET(url, http.headers)).execute().use { response ->
            if (!response.isSuccessful) {
                return VideoScan(
                    emptyList(),
                    note = "The page answered HTTP ${response.code}."
                )
            }
            response.body?.string()
                ?: return VideoScan(emptyList(), note = "The page returned no body.")
        }

        val doc = Jsoup.parse(body, url)
        val links = doc.select("video[src], video source[src]")
            .map { it.attr("abs:src") }
            .filter { it.isNotBlank() }
            .distinct()

        val embeds = doc.select("iframe[src]")
            .map { it.attr("abs:src") }
            .filter { it.startsWith("http", ignoreCase = true) }
            .filterNot { candidate ->
                ANALYTICS_FRAMES.any { candidate.contains(it, ignoreCase = true) }
            }
            .distinct()

        if (links.isNotEmpty()) return VideoScan(links, embeds)

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

        return VideoScan(
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

    private companion object {
        val ANALYTICS_FRAMES = listOf(
            "googletagmanager.com",
            "google-analytics.com",
            "doubleclick.net",
            "googlesyndication.com",
            "facebook.net",
            "facebook.com/plugins",
            "disqus.com"
        )
    }
}
