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
internal const val CLEARANCE_COOKIE = "cf_clearance"
internal const val POLL_MS = 250L
internal const val SETTLE_MS = 600L

/**
 * Whether the browser cookie store holds clearance for [url].
 *
 * Parsed into names rather than a substring check on the raw header, so a
 * cookie whose *value* happens to contain the string can't report a false pass.
 */
internal fun hasClearanceCookie(url: String): Boolean =
    runCatching {
        CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.any { it.substringBefore("=").trim() == CLEARANCE_COOKIE } == true
    }.getOrDefault(false)

/**
 * Drops a clearance cookie that Cloudflare has already rejected.
 *
 * ChallengeWebViewScreen is only opened after the source request was challenged,
 * so an existing cf_clearance at that point is stale. Keeping it causes
 * Cloudflare to keep evaluating the same rejected session forever.
 */
internal fun clearClearanceCookie(url: String) {
    runCatching {
        CookieManager.getInstance().apply {
            setCookie(url, "$CLEARANCE_COOKIE=; Max-Age=0; Path=/")
            flush()
        }
    }
}

/** Host part of a URL for the title bar, falling back to the whole string. */
internal fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host ?: url }.getOrDefault(url)
