package com.mangareader.app

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/**
 * One request to a source, reported in full.
 *
 * The probe intentionally keeps the original base-URL request, but manga sources
 * now get a second, browse-specific diagnostic too. That matters for sources such
 * as MadaraNoAjax: the homepage can return a clean HTTP 200 while Popular actually
 * loads /<manga path>/?m_orderby=views and the parser sees zero archive cards.
 */
internal suspend fun probeSource(context: Context, source: Source): String =
    withContext(Dispatchers.IO) {
        val mangaHttp = (source as? TachiyomiSourceAdapter)?.catalogueSource as? HttpSource
        val animeHttp = (source as? AniyomiSourceAdapter)?.catalogueSource as? AnimeHttpSource
        if (mangaHttp == null && animeHttp == null) {
            return@withContext "${source.name} doesn't expose an HTTP client for diagnostics."
        }

        val url = mangaHttp?.baseUrl ?: animeHttp!!.baseUrl
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        val client = mangaHttp?.client ?: animeHttp!!.client
        val headers = mangaHttp?.headers ?: animeHttp!!.headers
        val out = StringBuilder()

        out.appendLine("SOURCE")
        out.appendLine("  ${source.name}  (${source.id})")
        out.appendLine("  $url")
        out.appendLine()

        out.appendLine("BEFORE THE REQUEST")
        out.appendLine("  cf_clearance: ${if (hasClearance(url)) "present" else "absent"}")
        out.appendLine("  recorded UA:  ${ClearanceUserAgents.get(context, host) ?: "none"}")
        out.appendLine()

        out.append(
            probeHttpRequest(
                client = client,
                headers = headers,
                requestedUrl = url,
                sectionTitle = "BASE URL REQUEST",
            ),
        )

        if (mangaHttp != null) {
            out.appendLine()
            out.append(
                probePopularBrowse(
                    source = mangaHttp,
                    client = client,
                    headers = headers,
                ),
            )
        }

        out.appendLine()
        out.appendLine("AFTER THE REQUEST")
        out.appendLine("  cf_clearance: ${if (hasClearance(url)) "present" else "absent"}")
        out.toString()
    }

private fun probeHttpRequest(
    client: OkHttpClient,
    headers: Headers,
    requestedUrl: String,
    sectionTitle: String,
    archiveSelector: String? = null,
): String {
    return runCatching {
        client.newCall(GET(requestedUrl, headers)).execute().use { response ->
            val sent = response.request.header("User-Agent")
            val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
            val finalUrl = response.request.url.toString()

            buildString {
                appendLine(sectionTitle)
                appendLine("  requested URL: $requestedUrl")
                appendLine("  final URL:     $finalUrl")
                appendLine("  status:        ${response.code} ${response.message}")
                appendLine("  protocol:      ${response.protocol}")
                appendLine("  UA sent:       ${sent ?: "none"}")
                appendLine()
                appendLine("CLOUDFLARE")
                appendLine("  cf-mitigated: ${response.header("cf-mitigated") ?: "absent"}")
                appendLine("  cf-ray:       ${response.header("cf-ray") ?: "absent"}")
                appendLine("  server:       ${response.header("server") ?: "absent"}")
                appendLine("  set-cookie:   ${cookieNames(response.headers.values("set-cookie"))}")

                if (archiveSelector != null && body.isNotBlank()) {
                    appendLine()
                    append(selectorDiagnostics(body, finalUrl, archiveSelector))
                }

                appendLine()
                appendLine("BODY (first ${BODY_CHARS} chars)")
                appendLine("  ${bodyPreview(body)}")
            }
        }
    }.getOrElse { error ->
        buildString {
            appendLine(sectionTitle)
            appendLine("  requested URL: $requestedUrl")
            appendLine("  NO RESPONSE")
            appendLine("  ${error::class.java.simpleName}")
            appendLine("  ${error.message ?: "no message"}")
        }
    }
}

private suspend fun probePopularBrowse(
    source: HttpSource,
    client: OkHttpClient,
    headers: Headers,
): String {
    val selector = reflectedString(source, "archiveSelector")
    val popularUrl = reflectedPopularArchiveUrl(source)
    val parsed = runCatching { source.getPopularManga(1) }

    val browseReport = buildString {
        appendLine("POPULAR BROWSE DIAGNOSTIC")
        appendLine("  parser class: ${source.javaClass.name}")
        appendLine("  archive selector: ${selector ?: "unavailable"}")

        if (popularUrl != null) {
            appendLine()
            append(
                probeHttpRequest(
                    client = client,
                    headers = headers,
                    requestedUrl = popularUrl,
                    sectionTitle = "POPULAR HTTP REQUEST",
                    archiveSelector = selector,
                ),
            )
        } else {
            appendLine("  popular URL: unavailable (source does not expose a Madara-style archive builder)")
        }

        appendLine()
        appendLine("POPULAR SOURCE PARSER")
        parsed.fold(
            onSuccess = { page ->
                appendLine("  titles returned: ${page.mangas.size}")
                appendLine("  has next page:   ${page.hasNextPage}")
                page.mangas.take(5).forEachIndexed { index, manga ->
                    appendLine("  ${index + 1}. ${manga.title}  [${manga.url}]")
                }
            },
            onFailure = { error ->
                appendLine("  FAILED: ${error::class.java.simpleName}")
                appendLine("  ${error.message ?: "no message"}")
            },
        )
    }

    val manga = parsed.getOrNull()?.mangas
        ?.firstOrNull { it.title.equals("Overgeared", ignoreCase = true) }
        ?: parsed.getOrNull()?.mangas?.firstOrNull()
        ?: return browseReport

    return browseReport + "\n\n" + probeSeriesContent(source, client, headers, manga)
}

/**
 * The base and Popular probes do not exercise the request that actually opens
 * a series. This follows one real manga through the combined update API and
 * its first non-premium chapter page list, with bounded waits and no cookie
 * values in the report.
 */
private suspend fun probeSeriesContent(
    source: HttpSource,
    client: OkHttpClient,
    headers: Headers,
    manga: SManga,
): String {
    val mangaUrl = runCatching { source.getMangaUrl(manga) }.getOrNull()
    val pageSnapshot = mangaUrl?.let {
        probeHttpRequest(
            client = client,
            headers = headers,
            requestedUrl = it,
            sectionTitle = "SERIES PAGE HTTP SNAPSHOT (basic GET)",
        )
    } ?: "SERIES PAGE HTTP SNAPSHOT\n  URL unavailable: ${source.name} could not build the manga URL"

    val updateStarted = SystemClock.elapsedRealtime()
    var update: eu.kanade.tachiyomi.source.model.SMangaUpdate? = null
    var updateError: Throwable? = null
    try {
        update = withTimeout(30_000L) {
            source.getMangaUpdateWithLock(manga, emptyList(), true, true)
        }
    } catch (timeout: TimeoutCancellationException) {
        updateError = timeout
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        updateError = error
    }
    val updateElapsed = SystemClock.elapsedRealtime() - updateStarted

    val chapter = update?.chapters.orEmpty()
        .firstOrNull { chapter ->
            !runCatching { chapter.name }.getOrDefault("").startsWith("🔒")
        }
        ?: update?.chapters.orEmpty().firstOrNull()

    var pageCount: Int? = null
    var pageError: Throwable? = null
    var chapterUrl: String? = null
    var pageElapsed = 0L
    if (chapter != null) {
        chapterUrl = runCatching { source.getChapterUrl(chapter) }
            .getOrElse {
                pageError = it
                null
            }
        if (chapterUrl != null) {
            val pageStarted = SystemClock.elapsedRealtime()
            try {
                pageCount = withTimeout(30_000L) { source.getPageList(chapter).size }
            } catch (timeout: TimeoutCancellationException) {
                pageError = timeout
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                pageError = error
            }
            pageElapsed = SystemClock.elapsedRealtime() - pageStarted
        }
    }

    return buildString {
        appendLine("SERIES CONTENT DIAGNOSTIC")
        appendLine("  title: ${runCatching { manga.title }.getOrDefault("(unavailable)")}")
        appendLine("  source URL: ${runCatching { manga.url }.getOrDefault("(unavailable)")}")
        appendLine("  memo has slug: ${runCatching { manga.memo.containsKey("slug") }.getOrDefault(false)}")
        appendLine("  extension manga URL: ${mangaUrl ?: "(unavailable)"}")
        appendLine()
        appendLine(pageSnapshot)
        appendLine()
        appendLine("COMBINED DETAILS + CHAPTERS")
        appendLine("  elapsed: ${updateElapsed}ms")
        if (updateError != null) {
            appendLine("  FAILED: ${updateError!!::class.java.simpleName}")
            appendLine("  ${updateError!!.message ?: "no message"}")
        } else {
            appendLine("  details title: ${update?.manga?.let { runCatching { it.title }.getOrDefault("(unavailable)") } ?: "(none returned)"}")
            appendLine("  chapters returned: ${update?.chapters?.size ?: 0}")
        }
        if (chapter != null) {
            appendLine()
            appendLine("FIRST AVAILABLE CHAPTER")
            appendLine("  chapter URL: ${chapterUrl ?: "(getChapterUrl failed)"}")
            if (pageCount != null) {
                appendLine("  page-list count: $pageCount")
                appendLine("  elapsed: ${pageElapsed}ms")
            } else if (pageError != null) {
                appendLine("  page-list FAILED: ${pageError!!::class.java.simpleName}")
                appendLine("  ${pageError!!.message ?: "no message"}")
            }
        } else if (updateError == null) {
            appendLine("  no chapter available to test page loading")
        }
    }
}

/**
 * Current Keiyoushi MadaraNoAjax builds Popular with archiveUrlBuilder(1, "views", ...).
 * Reflection keeps diagnostics out of the extension ABI while still showing the exact URL
 * that matters for sources such as HentaiSco. If a source is not Madara-style, we simply
 * skip this extra HTTP request instead of guessing.
 */
private fun reflectedPopularArchiveUrl(source: HttpSource): String? {
    val mangaSubString = reflectedString(source, "getMangaSubString") ?: return null
    val method = findMethod(source, "archiveUrlBuilder", 4) ?: return null
    val path = "/${mangaSubString.trim('/')}/"
    return runCatching {
        method.isAccessible = true
        val builder = method.invoke(source, 1, "views", path, "") as? HttpUrl.Builder
            ?: return@runCatching null
        builder.build().toString()
    }.getOrNull()
}

private fun selectorDiagnostics(body: String, finalUrl: String, selector: String): String {
    val document = Jsoup.parse(body, finalUrl)
    val selectors = selector
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    return buildString {
        appendLine("SELECTOR COUNTS")
        appendLine("  combined [$selector]: ${runCatching { document.select(selector).size }.getOrDefault(-1)}")
        selectors.forEach { item ->
            appendLine("  $item: ${runCatching { document.select(item).size }.getOrDefault(-1)}")
        }
        appendLine("  .post-title a: ${document.select(".post-title a").size}")
        appendLine("  a[href]: ${document.select("a[href]").size}")
    }
}

private fun reflectedString(target: Any, methodName: String): String? =
    findMethod(target, methodName, 0)?.let { method ->
        runCatching {
            method.isAccessible = true
            method.invoke(target) as? String
        }.getOrNull()
    }

private fun findMethod(target: Any, name: String, parameterCount: Int): java.lang.reflect.Method? {
    var type: Class<*>? = target.javaClass
    while (type != null) {
        type.declaredMethods.firstOrNull { method ->
            method.name == name && method.parameterCount == parameterCount
        }?.let { return it }
        type = type.superclass
    }
    return null
}

private const val BODY_CHARS = 600

/** Names only. A cookie's value is a credential and has no business in a report. */
private fun cookieNames(values: List<String>): String =
    if (values.isEmpty()) "none"
    else values.joinToString(", ") { it.substringBefore('=').trim() }

/** Collapsed to one line: a Cloudflare block page is mostly whitespace and markup. */
private fun bodyPreview(body: String): String {
    if (body.isBlank()) return "(empty)"
    val flat = body
        .replace(Regex("""<script[\s\S]*?</script>"""), " ")
        .replace(Regex("""<style[\s\S]*?</style>"""), " ")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val text = flat.ifBlank { body.replace(Regex("""\s+"""), " ").trim() }
    return if (text.length <= BODY_CHARS) text else text.take(BODY_CHARS) + "…"
}

private fun hasClearance(url: String): Boolean =
    runCatching {
        CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.any { it.substringBefore("=").trim() == "cf_clearance" } == true
    }.getOrDefault(false)

/** Runs [probeSource] and shows the result. Selectable so it can be pasted into a bug report. */
@Composable
internal fun NetworkProbeDialog(source: Source, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var report by remember(source.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(source.id) {
        report = runCatching { probeSource(context, source) }
            .getOrElse { "Probe failed: ${it.message ?: it::class.java.simpleName}" }
    }

    val text = report
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection probe") },
        text = {
            if (text == null) {
                Column {
                    Text("Requesting…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Testing the base URL, Popular, a real series page, combined chapter loading, " +
                            "and one chapter's page list. This can take up to 90 seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}
