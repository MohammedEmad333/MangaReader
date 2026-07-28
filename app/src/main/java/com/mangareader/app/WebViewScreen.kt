package com.mangareader.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
 * **The User-Agent must match the one OkHttp sends.** `cf_clearance` is bound to
 * the UA that earned it and is rejected when a later request presents a
 * different one — so the caller passes in the source's actual UA rather than
 * letting the WebView use the system default, which would produce a cookie that
 * looks valid here and 403s everywhere else. That failure mode is silent and
 * confusing, so it's worth not causing.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChallengeWebViewScreen(
    url: String,
    userAgent: String,
    onSolved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }

    // Whether clearance existed *before* the user got here. If it did, its
    // presence proves nothing — the request 403'd while holding it, so it was
    // stale or rejected — and auto-finishing on it would bounce straight back to
    // the same error. In that case the screen waits for the Done button instead.
    val hadClearance = remember(url) { hasClearanceCookie(url) }

    val webView = remember(url) {
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = userAgent
        view.settings.useWideViewPort = true
        view.settings.loadWithOverviewMode = true
        CookieManager.getInstance().let { cookies ->
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(view, true)
        }
        // Keeps navigation inside this WebView. Without a client set, a redirect
        // can be handed to the system browser — where the user would solve the
        // challenge into Chrome's cookie store, which this app cannot read.
        view.webViewClient = WebViewClient()
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
        while (!hasClearanceCookie(url)) {
            delay(POLL_MS)
        }
        // Flush before handing back: the cookie store is written asynchronously,
        // and the retry is about to read it from another process-level store.
        runCatching { CookieManager.getInstance().flush() }
        solved = true
        // A beat so the user sees the challenge complete rather than the screen
        // vanishing out from under the tap.
        delay(SETTLE_MS)
        onSolved()
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
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
            navigationIcon = { TextButton(onClick = onBack) { Text("\u2190") } },
            actions = {
                // Manual escape hatch: the poll only fires on a cookie that
                // appears while the screen is open, and some sources hand out
                // clearance in ways this can't see. Done retries regardless.
                TextButton(onClick = onSolved) { Text("Done") }
            }
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = if (solved) "Challenge solved \u2014 returning\u2026"
            else "Complete the check below. This closes by itself once it passes.",
            style = MaterialTheme.typography.bodySmall,
            color = if (solved) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
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
