package com.mangareader.app

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import kotlinx.coroutines.delay

/**
 * A WebView the user can actually see and tap.
 *
 * `CloudflareInterceptor` already answers the *JavaScript* challenge in a
 * headless WebView, and that covers most protected sources. What it cannot do is
 * the interactive kind — the checkbox, or a managed challenge that decides it
 * wants one — because there is nobody there to click it. That is the entire
 * reason this screen exists: it is the same WebView, on screen, with the user
 * supplying the one thing automation can't.
 *
 * **There is no cookie plumbing here either**, for the same reason there is none
 * in the interceptor: `AndroidCookieJar` reads `android.webkit.CookieManager`,
 * which is the store this WebView writes to. Solving the challenge is the whole
 * job; OkHttp picks up `cf_clearance` by itself on the next request.
 *
 * **The WebView keeps its own User-Agent, and OkHttp follows it.** `cf_clearance`
 * is bound to the UA that earned it, so the two have to agree — but the first
 * attempt made them agree by forcing this WebView to claim the app's desktop
 * Chrome default, and that is a challenge nobody can pass: the checkbox exists
 * to catch a client whose UA and runtime disagree, and inside an Android WebView
 * a Windows UA disagrees with everything. It looped on the checkbox forever.
 *
 * So the agreement runs the other way. The WebView presents what it honestly is,
 * and whatever string passes gets recorded in [ClearanceUserAgents] for OkHttp to
 * reuse against that host.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChallengeWebViewScreen(
    url: String,
    onSolved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }

    // The renderer is a separate process and can die under us. When it does the
    // WebView is unusable and must not be touched again — but the screen is
    // still here, so it says so and offers the way out rather than showing a
    // blank rectangle. See the client below for why not handling this at all
    // would close the app.
    var rendererDied by remember(url) { mutableStateOf(false) }

    // Whether clearance existed *before* the user got here. If it did, its
    // presence proves nothing — the request 403'd while holding it, so it was
    // stale or rejected — and auto-finishing on it would bounce straight back to
    // the same error. In that case the screen waits for the Done button instead.
    val hadClearance = remember(url) { hasClearanceCookie(url) }

    // The UA this WebView presents. Read rather than set — see the note above —
    // and held so it can be recorded against the host once a challenge passes.
    var nativeUserAgent by remember(url) { mutableStateOf<String?>(null) }

    val webView = remember(url) {
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.useWideViewPort = true
        view.settings.loadWithOverviewMode = true
        nativeUserAgent = view.settings.userAgentString
        CookieManager.getInstance().let { cookies ->
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(view, true)
        }
        // Keeps navigation inside this WebView. Without a client set, a redirect
        // can be handed to the system browser — where the user would solve the
        // challenge into Chrome's cookie store, which this app cannot read.
        //
        // And it answers for a dead renderer. That runs in its own sandboxed
        // process; if it dies and nothing claims to have handled it, Android
        // kills the app — with no Java exception, so CrashLog never sees it and
        // the user just watches the app vanish. Returning true keeps us alive.
        view.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(
                v: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                rendererDied = true
                // Detached before destroying: a WebView still in the hierarchy
                // must not be destroyed, and the composition is about to stop
                // drawing it anyway.
                runCatching {
                    (v?.parent as? ViewGroup)?.removeView(v)
                    v?.destroy()
                }
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {
                progress = newProgress
            }
        }
        view.loadUrl(url)
        view
    }

    // Polling, not onPageFinished: a challenge runs through several navigations
    // and the only event that means anything is the cookie turning up. Same
    // reasoning as the interceptor, which learned it the hard way.
    LaunchedEffect(url, hadClearance) {
        if (hadClearance) return@LaunchedEffect
        // A dead renderer will never write the cookie, so the poll has to end
        // rather than spin for as long as the screen is open.
        while (!rendererDied && !hasClearanceCookie(url)) {
            delay(POLL_MS)
        }
        if (rendererDied) return@LaunchedEffect
        // Flush before handing back: the cookie store is written asynchronously,
        // and the retry is about to read it from another process-level store.
        runCatching { CookieManager.getInstance().flush() }
        // Record the UA that passed, or the retry goes out under the app default
        // and the clearance just earned is rejected on arrival.
        nativeUserAgent?.let { ua ->
            runCatching { ClearanceUserAgents.set(context, hostOf(url), ua) }
        }
        solved = true
        // A beat so the user sees the challenge complete rather than the screen
        // vanishing out from under the tap.
        delay(SETTLE_MS)
        onSolved()
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            // Already destroyed on the renderer-death path. Destroying twice is
            // not something to rely on being harmless.
            if (!rendererDied) {
                runCatching {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }
    }

    BackHandler {
        // canGoBack() on a destroyed WebView is exactly the kind of call this
        // whole change exists to avoid making.
        if (!rendererDied && webView.canGoBack()) webView.goBack() else onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = hostOf(url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = { BackButton(onBack) },
            actions = {
                // Manual escape hatch: the poll only fires on a cookie that
                // appears while the screen is open, and some sources hand out
                // clearance in ways this can't see. Done retries regardless —
                // recording the UA first, because if clearance *was* obtained
                // it belongs to this WebView's UA and not the app's.
                TextButton(
                    onClick = {
                        nativeUserAgent?.let { ua ->
                            runCatching { ClearanceUserAgents.set(context, hostOf(url), ua) }
                        }
                        onSolved()
                    }
                ) { Text("Done") }
            }
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = when {
                rendererDied -> "The browser view stopped unexpectedly. Go " +
                    "back and try again."
                solved -> "Challenge solved \u2014 returning\u2026"
                else -> "Complete the check below. This closes by itself once " +
                    "it passes."
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                rendererDied -> MaterialTheme.colorScheme.error
                solved -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Dropped from the tree once the renderer is gone. The view has been
        // destroyed by then, and handing a destroyed WebView to AndroidView is
        // the second way this could take the app down.
        if (!rendererDied) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Name of the cookie Cloudflare issues once a challenge has been passed. */
private const val CLEARANCE_COOKIE = "cf_clearance"
private const val POLL_MS = 250L
private const val SETTLE_MS = 600L

/**
 * Whether the browser cookie store holds clearance for [url].
 *
 * Parsed into names rather than a substring check on the raw header, so a
 * cookie whose *value* happens to contain the string can't report a false pass.
 */
private fun hasClearanceCookie(url: String): Boolean =
    runCatching {
        CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.any { it.substringBefore("=").trim() == CLEARANCE_COOKIE } == true
    }.getOrDefault(false)

/** Host part of a URL for the title bar, falling back to the whole string. */
private fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host ?: url }.getOrDefault(url)
