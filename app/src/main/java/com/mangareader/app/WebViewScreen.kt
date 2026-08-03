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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
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
    /** Media urls scraped out of the player, for an external app to open. */
    onMediaFound: (List<String>) -> Unit,
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
    // The live WebView, kept so the DOM can be interrogated.
    var webView by remember { mutableStateOf<WebView?>(null) }

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
            navigationIcon = { BackButton(onBack) },
            actions = {
                // THE VIDEO IS THERE AND PAUSED AT ZERO. Frames have decoded
                // (media 1440x1080) and the page has given it a real box
                // (392x728), so nothing is broken about loading or layout —
                // nothing has told it to start. The player's own overlay is
                // presumably what would, and it is not drawing.
                //
                // mediaPlaybackRequiresUserGesture is already false, so this
                // should be permitted; if it is refused, the console message
                // lands in the strip below and says why.
                IconButton(onClick = {
                    webView?.evaluateJavascript(PLAY_JS, null)
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = {
                    webView?.evaluateJavascript(MEDIA_URLS_JS) { raw ->
                        val found = raw.removeSurrounding("\"")
                            .replace("\\n", "\n")
                            .replace("\\/", "/")
                            .replace("\\u003C", "<", ignoreCase = true)
                            .split("\n")
                            .filter { it.isNotBlank() }
                            .map { it.substringAfter('|') }
                            .distinct()
                        onMediaFound(found)
                    }
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Get video link")
                }
            }
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
                    // NO setLayerType HERE, AND THAT IS DELIBERATE. 0.183 set
                    // LAYER_TYPE_HARDWARE on the reasoning that inline video
                    // needs a hardware layer; frames went from 341 to 1352 and
                    // the screen stayed white, so it did nothing — and forcing
                    // a WebView into an offscreen hardware layer is a known way
                    // to BREAK video overlays, because the video composites
                    // outside the texture the layer captures. A change that did
                    // not help and can hurt does not get to stay.
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    // The player's host is the only place this screen may go.
                    val allowedHost = runCatching { Uri.parse(url).host }.getOrNull()
                    webViewClient = object : WebViewClient() {
                        // POPUNDERS. These free embed hosts monetise clicks: a
                        // tap anywhere on the page navigates the whole WebView
                        // to an advertiser, and the player is gone. Blocking
                        // off-host navigation is not politeness, it is the
                        // difference between a usable player and one that
                        // cannot be touched.
                        //
                        // Host-scoped rather than a blocklist: the page may
                        // legitimately move within its own domain, and naming
                        // advertisers one at a time is a race nobody wins.
                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.host ?: return false
                            return allowedHost != null && target != allowedHost
                        }

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
                    // window.open and target=_blank take a different path and
                    // would sail past the check above. Refused outright: this
                    // screen has one job.
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = false
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
                }.also { webView = it }
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



/**
 * Starts the first <video> and reports what happened.
 *
 * A promise rejection here is the answer to "why is it paused" — autoplay
 * policy, a decode failure, or a source the element could not open all reject
 * with a named error, and the message goes straight to the strip.
 */
private val PLAY_JS = """
    (function () {
      var v = document.querySelector('video');
      if (!v) return 'no <video> to play';
      var p = v.play();
      if (p && p.catch) {
        p.catch(function (e) {
          var el = document.getElementById('yomu-note');
          if (!el) {
            el = document.createElement('div');
            el.id = 'yomu-note';
            document.body.appendChild(el);
          }
          el.textContent = 'play() rejected: ' + e.name + ' ' + e.message;
        });
      }
      return 'play() called';
    })()
""".trimIndent()

/**
 * Pulls the media URL out of the player, rather than trying to render it.
 *
 * Ten releases went into making the video DRAW in this WebView and it never
 * did — audio plays, frames decode, nothing reaches the screen. But the point
 * was never to render it here; it was to watch it. The element knows its own
 * source, and the browser keeps a record of every file the player fetched, so
 * the URL is available even though the picture is not.
 *
 * TWO SOURCES, because one of them often is not usable:
 *   - currentSrc: what the element is playing. If it is an ordinary https url,
 *     any player can open it.
 *   - performance resource entries: every media file actually requested. This
 *     is what saves the case where currentSrc is a `blob:` — Media Source
 *     Extensions feeds the element from JavaScript, and a blob url means
 *     nothing outside this page. The real segments or manifest still show up
 *     here.
 */
private val MEDIA_URLS_JS = """
    (function () {
      var out = [];
      var v = document.querySelector('video');
      if (v && v.currentSrc) out.push('src|' + v.currentSrc);
      try {
        var res = performance.getEntriesByType('resource');
        for (var i = 0; i < res.length; i++) {
          var u = res[i].name;
          if (/\.(m3u8|mpd|mp4|webm|mkv|ts)(\?|$)/i.test(u)) out.push('net|' + u);
        }
      } catch (e) {}
      // Longest first: a manifest or a whole file beats one segment of it.
      return out.filter(function (x, i) { return out.indexOf(x) === i; }).join('\n');
    })()
""".trimIndent()
