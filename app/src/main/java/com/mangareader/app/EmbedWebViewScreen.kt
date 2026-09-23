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
    // THE PAGE IS NOT SHOWN BY DEFAULT, and that is the point of this screen
    // now. The embedded player provably never draws here — audio plays, every
    // frame decodes, the screen stays white, and SESSION_HANDOFF_0.188.md §9h
    // records what that rules out. Showing it meant handing someone a blank
    // white rectangle and hoping they found the link button.
    //
    // So the page loads offscreen, plays itself muted, and the url it reaches
    // for is offered directly. `reveal` exists for the case where nothing is
    // found: seeing the page beats being told nothing was there.
    var reveal by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
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
    // Plays it muted, then watches for the url. Both are needed and in this
    // order: the element sits PAUSED at t=0 until told otherwise, and neither
    // currentSrc nor the resource timeline exists before playback starts.
    //
    // Polling rather than a single shot after onPageFinished: the player builds
    // itself from script, so the video element does not exist when the page
    // reports finished. Ten seconds is generous for a fetch already in flight.
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        repeat(20) {
            delay(500)
            view.evaluateJavascript(SILENT_PLAY_JS, null)
            delay(500)
            var done = false
            view.evaluateJavascript(MEDIA_URLS_JS) { raw ->
                val found = raw.removeSurrounding("\"")
                    .replace("\\n", "\n")
                    .replace("\\/", "/")
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .map { it.substringAfter('|') }
                    .distinct()
                if (found.isNotEmpty()) {
                    done = true
                    onMediaFound(found)
                }
            }
            if (done) return@LaunchedEffect
        }
        // Nothing after ten seconds. Say so and offer the page, rather than
        // spinning forever on a promise that is not going to be kept.
        searched = true
    }

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
                // Only once the page is visible. With it hidden these duplicate
                // what the loop above already does, and a control that repeats
                // an automatic action is just a way to wonder whether it worked.
                if (!reveal) return@TopAppBar
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
            // One pixel when hidden rather than zero: a WebView with no size
            // does not lay out, and a player that never lays out never starts.
            modifier = if (reveal) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.size(1.dp)
            },
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
        // What the page used to occupy. Says what is happening instead of
        // showing a white rectangle that is doing something invisible.
        if (!reveal) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!searched) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(20.dp))
                    Text("Finding the video\u2026", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The player is loading in the background. When the video " +
                            "address turns up it opens in your video player.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("No video address found", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This player may build its stream entirely in the page, " +
                            "which leaves nothing an outside app can open.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(16.dp))
                    // Worth offering even knowing it does not draw: the page may
                    // carry a download link of its own, and being shown the
                    // thing beats being told about it.
                    TextButton(onClick = { reveal = true }) { Text("Show the page anyway") }
                }
            }
        }

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

/**
 * Mutes and starts the video, for the headless pass.
 *
 * MUTED MATTERS. This runs with the WebView invisible, so unmuted autoplay would
 * blare the soundtrack at someone who only asked for a link. Muted playback is
 * also the case browsers permit most freely, so it is likelier to start at all.
 *
 * Playing is not optional: the element sits at t=0 PAUSED until told otherwise,
 * and neither currentSrc nor the resource timeline is populated before it does.
 */
private val SILENT_PLAY_JS = """
    (function () {
      var v = document.querySelector('video');
      if (!v) return 'no video yet';
      v.muted = true;
      v.volume = 0;
      var p = v.play();
      if (p && p.catch) p.catch(function () {});
      return 'started';
    })()
""".trimIndent()
