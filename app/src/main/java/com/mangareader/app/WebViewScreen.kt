package com.mangareader.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeBackoff
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
 * `AndroidCookieJar` and WebView share `android.webkit.CookieManager`, so
 * cookies written by the visible browser are already available to OkHttp.
 *
 * A protected page can also load successfully in WebView without Cloudflare
 * issuing a `cf_clearance` cookie. In that case the browser's native User-Agent
 * still matters: OkHttp must use the same host-specific UA or it can be blocked
 * even though the visible page loaded. A successful, same-host main-frame load
 * therefore syncs both the browser cookies and its UA before retrying Yomu.
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
    var clearancePresent by remember(url) { mutableStateOf(hasClearanceCookie(url)) }
    var browserPageReady by remember(url) { mutableStateOf(false) }
    var mainFrameHttpStatusCode by remember(url) { mutableIntStateOf(0) }

    // The renderer is a separate process and can die under us. When it does the
    // WebView is unusable and must not be touched again — but the screen is
    // still here, so it says so and offers the way out rather than showing a
    // blank rectangle. See the client below for why not handling this at all
    // would close the app.
    var rendererDied by remember(url) { mutableStateOf(false) }

    // The UA this WebView presents. Read rather than set. Once this exact
    // host's page is accessible, OkHttp reuses the same UA for its requests.
    var nativeUserAgent by remember(url) { mutableStateOf<String?>(null) }

    val webView = remember(url) {
        // This screen is entered only after the source request was challenged.
        // If cf_clearance is still present, Cloudflare has already rejected that
        // session. Drop it (and its bound UA) before loading the challenge so a
        // fresh verification can actually be issued.
        if (hasClearanceCookie(url)) {
            clearClearanceCookie(url)
            clearancePresent = false
            ClearanceUserAgents.remove(context, hostOf(url))
        }

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
            override fun onPageStarted(
                view: WebView?,
                pageUrl: String?,
                favicon: Bitmap?
            ) {
                if (
                    pageUrl != null &&
                    isSameHostOrSubdomain(hostOf(url), hostOf(pageUrl))
                ) {
                    mainFrameHttpStatusCode = 200
                    browserPageReady = false
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (
                    request?.isForMainFrame == true &&
                    isSameHostOrSubdomain(hostOf(url), request.url.host.orEmpty())
                ) {
                    mainFrameHttpStatusCode = errorResponse?.statusCode ?: 599
                }
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                if (
                    view == null ||
                    finishedUrl.isNullOrBlank() ||
                    !isSameHostOrSubdomain(hostOf(url), hostOf(finishedUrl))
                ) {
                    return
                }

                val responseCode = mainFrameHttpStatusCode
                view.evaluateJavascript(CLOUDFLARE_CHALLENGE_DETECT_SCRIPT) { result ->
                    if (rendererDied) return@evaluateJavascript
                    val challengeDetected = result == "true"
                    if (shouldAcceptWebViewPage(responseCode, challengeDetected)) {
                        browserPageReady = true
                    }
                }
            }

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

    // A cookie is the strongest success signal, but not the only one: Asura can
    // serve the complete manga page to WebView without issuing cf_clearance.
    // In that case the successful main-frame response is enough to sync the
    // shared browser cookies and native UA before OkHttp retries.
    LaunchedEffect(url) {
        while (!rendererDied) {
            clearancePresent = hasClearanceCookie(url)
            if (clearancePresent || browserPageReady) break
            delay(POLL_MS)
        }
        if (rendererDied || (!clearancePresent && !browserPageReady)) return@LaunchedEffect

        runCatching { CookieManager.getInstance().flush() }
        // Reuse the exact UA that loaded this host in WebView. This is required
        // both for UA-bound Cloudflare clearance and for hosts that allow the
        // browser page but reject the app's desktop default UA.
        nativeUserAgent?.let { ua ->
            runCatching { ClearanceUserAgents.set(context, hostOf(url), ua) }
        }
        // The hidden OkHttp attempt may have recorded a 60-second host cooldown
        // before the user opened this visible WebView. Clear that shared state
        // before returning, otherwise the immediate chapter retry is rejected
        // without making a network request.
        CloudflareChallengeBackoff.clearHost(hostOf(url))
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

    // The toolbar arrow and Android Back both navigate WebView history first.
    // Never query a renderer after it has been destroyed.
    fun navigateBack() {
        if (!rendererDied && webView.canGoBack()) webView.goBack() else onBack()
    }
    BackHandler { navigateBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = hostOf(url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = { BackButton { navigateBack() } },
            actions = {
                // Reload is useful when a challenge widget did not render.
                // A normal successful page load will return automatically after
                // its cookies and native User-Agent are synced to OkHttp.
                TextButton(
                    onClick = {
                        if (!rendererDied && !clearancePresent && !browserPageReady) {
                            mainFrameHttpStatusCode = 0
                            webView.reload()
                        }
                    },
                    enabled = !rendererDied && !clearancePresent && !browserPageReady,
                ) { Text("Reload page") }
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
                solved -> "Browser session synced \u2014 returning to Yomu\u2026"
                clearancePresent -> "Cloudflare clearance received. Returning automatically."
                browserPageReady -> "The series page loaded successfully. Syncing its cookies and browser identity with Yomu..."
                else -> "Waiting for Cloudflare. Complete any check shown in the page. " +
                    "If the site page loads without a check, Yomu will sync that browser session automatically."
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                rendererDied -> MaterialTheme.colorScheme.error
                solved || clearancePresent || browserPageReady -> MaterialTheme.colorScheme.primary
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
