package com.mangareader.app

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/**
 * A plain WebView for an embedded player, loaded WITH A REFERER.
 *
 * Separate from [ChallengeWebViewScreen] deliberately: that one polls for a
 * clearance cookie and reports success to a caller waiting on it. This one just
 * shows a page. Sharing them would give the challenge screen a second meaning,
 * which is the fault this project keeps finding in its own labels.
 *
 * **The Referer is the whole point.** cossora.stream/embed/<uuid> is the player
 * behind CosplayTele's galleries, and opening it directly in a browser answers
 * `{"error": true, "message": "Unknown Error xD"}` — an embed-only player
 * refusing a request that did not come from a page allowed to embed it. Sent
 * from the gallery it is embedded on, it should behave as it does in the site's
 * own page.
 *
 * If it still errors with the header set, the check is not the referer — it
 * would then be a cookie, a session, or a token bound to the embedding page,
 * and none of those are reachable this way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmbedWebViewScreen(
    url: String,
    referer: String,
    onBack: () -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    // The page's own title, shown in the bar. A blank page WITH a title means
    // the page loaded and the problem is rendering; a blank page with no title
    // means it never arrived. Cheap, and it separates two very different
    // failures without another release.
    var pageTitle by remember { mutableStateOf<String?>(null) }
    // The view a player hands over when it asks for HTML5 fullscreen.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    // Held so leaving fullscreen can TELL THE PAGE. Dropping the view without
    // calling this leaves the player believing it is still fullscreen, and the
    // next tap on its own exit button does nothing.
    var fullscreenExit by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Back leaves fullscreen first, then the screen. Without the first step the
    // only way out of a fullscreen video is to leave the player entirely.
    fun leaveFullscreen() {
        fullscreenExit?.onCustomViewHidden()
        fullscreenExit = null
        fullscreenView = null
    }
    BackHandler { if (fullscreenView != null) leaveFullscreen() else onBack() }

    // OVERLAID, NOT SWAPPED. Returning early here and drawing only the
    // fullscreen view would take the WebView out of the composition, and a
    // WebView that leaves the tree stops playing — the video would die at the
    // moment it went fullscreen. So the page stays mounted underneath and the
    // handed-over view is drawn on top of it.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    pageTitle?.takeIf { it.isNotBlank() } ?: "Player",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = { BackButton(onBack) }
        )
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        AndroidView(
            // weight, not fillMaxSize: as the last child of a Column that
            // already spent height on the bar, fillMaxSize asks for the whole
            // screen and the bottom of the page falls off it.
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // Players are the main reason this exists, and many will not
                    // start without it.
                    settings.mediaPlaybackRequiresUserGesture = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(
                            v: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            runCatching {
                                (v?.parent as? ViewGroup)?.removeView(v)
                                v?.destroy()
                            }
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(v: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onReceivedTitle(v: WebView?, title: String?) {
                            pageTitle = title
                        }

                        // WITHOUT THIS THE PAGE GOES BLANK AND THE VIDEO PLAYS
                        // ANYWAY. A player asking for HTML5 fullscreen pulls its
                        // video out of the document and hands it here; a
                        // WebChromeClient that does not implement this drops it,
                        // so the page renders empty while the media keeps
                        // streaming. That is exactly what 0.177 did — a white
                        // screen at 1MB/s.
                        override fun onShowCustomView(
                            view: View?,
                            callback: CustomViewCallback?
                        ) {
                            fullscreenExit = callback
                            fullscreenView = view
                        }

                        override fun onHideCustomView() {
                            fullscreenExit = null
                            fullscreenView = null
                        }
                    }
                    loadUrl(url, mapOf("Referer" to referer))
                }
            }
        )
    }

    val handedOver = fullscreenView
    if (handedOver != null) {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            factory = { handedOver }
        )
    }
    }
}
