package com.mangareader.app

import android.content.Context
import android.net.Uri
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
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One request to a source, reported in full.
 *
 * **Why this exists.** Every network failure in this app arrives as a formatted
 * string — `HTTP error 403` — and everything else the server said is thrown
 * away. Twice now that has cost real time: the covers that turned out to be
 * pointed at `127.0.0.1` after two speculative rounds, and the manhwatoon 400s
 * in §0, still open after four partial fixes, every one of them inferred from
 * failure *rates* while the response body sat unread. §5's rule is to spend the
 * cycle making the symptom specific rather than on a candidate fix. This is that
 * cycle, made reusable.
 *
 * It requests [HttpSource.baseUrl] through [HttpSource.client] — the extension's
 * own client, with its own headers, its cookie jar and the Cloudflare
 * interceptor. That matters: the point is to reproduce what the extension does,
 * not to make a request that happens to succeed.
 *
 * The single most useful thing it settles is whether a site the visible WebView
 * loads happily also refuses OkHttp on the *same URL*. If it does, the block
 * isn't a challenge and isn't path-scoped — it's the client itself being
 * recognised, which no amount of solving fixes.
 */
internal suspend fun probeSource(context: Context, source: Source): String =
    withContext(Dispatchers.IO) {
        val http = (source as? TachiyomiSourceAdapter)?.catalogueSource as? HttpSource
            ?: return@withContext "${source.name} isn't an HTTP source, so there's " +
                "nothing to probe — local folders don't make requests."

        val url = http.baseUrl
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        val out = StringBuilder()

        out.appendLine("SOURCE")
        out.appendLine("  ${source.name}  (${source.id})")
        out.appendLine("  $url")
        out.appendLine()

        out.appendLine("BEFORE THE REQUEST")
        out.appendLine("  cf_clearance: ${if (hasClearance(url)) "present" else "absent"}")
        out.appendLine("  recorded UA:  ${ClearanceUserAgents.get(context, host) ?: "none"}")
        out.appendLine()

        val result = runCatching {
            http.client.newCall(GET(url, http.headers)).execute().use { response ->
                // response.request, not the request built above: interceptors
                // rewrite headers on the way out, and the UA that actually left
                // the phone is the only one worth reporting.
                val sent = response.request.header("User-Agent")
                val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")

                buildString {
                    appendLine("RESPONSE")
                    appendLine("  status:       ${response.code} ${response.message}")
                    appendLine("  protocol:     ${response.protocol}")
                    appendLine("  UA sent:      ${sent ?: "none"}")
                    appendLine()
                    appendLine("CLOUDFLARE")
                    // cf-mitigated is the header that names the reason outright:
                    // "challenge" means solvable, anything else generally isn't.
                    appendLine("  cf-mitigated: ${response.header("cf-mitigated") ?: "absent"}")
                    appendLine("  cf-ray:       ${response.header("cf-ray") ?: "absent"}")
                    appendLine("  server:       ${response.header("server") ?: "absent"}")
                    appendLine("  set-cookie:   ${cookieNames(response.headers.values("set-cookie"))}")
                    appendLine()
                    appendLine("BODY (first ${BODY_CHARS} chars)")
                    appendLine("  ${bodyPreview(body)}")
                }
            }
        }

        out.append(
            result.getOrElse { error ->
                // A transport-level failure never reaches a status code, and the
                // exception class is the diagnosis: an SSL handshake failure and
                // a timeout mean completely different things here.
                buildString {
                    appendLine("NO RESPONSE")
                    appendLine("  ${error::class.java.simpleName}")
                    appendLine("  ${error.message ?: "no message"}")
                }
            }
        )

        out.appendLine()
        out.appendLine("AFTER THE REQUEST")
        out.appendLine("  cf_clearance: ${if (hasClearance(url)) "present" else "absent"}")
        out.toString()
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
    return if (text.length <= BODY_CHARS) text else text.take(BODY_CHARS) + "\u2026"
}

private fun hasClearance(url: String): Boolean =
    runCatching {
        CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.any { it.substringBefore("=").trim() == "cf_clearance" } == true
    }.getOrDefault(false)

/**
 * Runs [probeSource] and shows the result.
 *
 * Selectable, because the useful thing to do with this is paste it somewhere.
 */
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
                    Text("Requesting\u2026", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "The Cloudflare interceptor gets a headless attempt first, " +
                            "so a blocked source can take up to 30 seconds to answer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
