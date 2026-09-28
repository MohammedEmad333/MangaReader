package com.mangareader.app

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    val interceptedMedia = remember { linkedSetOf<String>() }

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
    // Network interception above is the fast path: as soon as the page requests
    // a media manifest/file we can hand it to the player. DOM polling remains a
    // fallback for players that hide the final URL behind page-side state.
    //
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
            view.evaluateJavascript(EmbeddedPlayerScripts.SILENT_PLAY, null)
            delay(500)
            var done = false
            view.evaluateJavascript(EmbeddedPlayerScripts.MEDIA_URLS) { raw ->
                val found = EmbeddedPlayerScripts.parseMediaUrls(raw)
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
                    webView?.evaluateJavascript(EmbeddedPlayerScripts.PLAY, null)
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = {
                    webView?.evaluateJavascript(EmbeddedPlayerScripts.MEDIA_URLS) { raw ->
                        val found = EmbeddedPlayerScripts.parseMediaUrls(raw)
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
        EmbeddedPlayerWebView(
            url = url,
            referer = referer,
            reveal = reveal,
            onWebViewReady = { webView = it },
            onProgress = { progress = it },
            onTitle = { pageTitle = it },
            onMediaRequest = { mediaUrl ->
                if (interceptedMedia.add(mediaUrl)) {
                    searched = true
                    onMediaFound(interceptedMedia.toList())
                }
            },
            onShowFullscreen = { view, callback ->
                fullscreenExit = callback
                fullscreenView = view
            },
            onHideFullscreen = {
                fullscreenExit = null
                fullscreenView = null
            },
        )

        EmbeddedPlayerStatus(
            reveal = reveal,
            searched = searched,
            onReveal = { reveal = true },
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
