package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.ClearanceUserAgents
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Answers Cloudflare's JavaScript challenge in a WebView, then retries.
 *
 * The WebView stack was stripped out of this vendored API, which left every
 * Cloudflare-protected source returning a flat 403. This puts back the smallest
 * part that actually matters.
 *
 * **Why this is short.** `AndroidCookieJar` is backed by
 * `android.webkit.CookieManager` — the same store a WebView writes to. So there
 * is no cookie plumbing to write: the WebView solves the challenge, the browser
 * cookie store gains `cf_clearance`, and OkHttp picks it up on the next request
 * because it was already reading from there. All this class has to do is notice
 * the challenge, drive a WebView at it, and wait.
 *
 * **The User-Agent has to match, and the WebView picks it.** `cf_clearance` is
 * issued against the UA that solved the challenge and rejected if a later
 * request presents a different one. This used to force the WebView to claim the
 * app's default UA so they agreed; that default is a desktop Chrome string, and
 * a challenge run inside an Android WebView weighs platform, touch and renderer
 * alongside it. They contradicted each other and the challenge was unpassable.
 * So the WebView now keeps its own UA and the winning string is recorded in
 * [ClearanceUserAgents], which the UA interceptor consults per host.
 *
 * **What this does not do.** Cloudflare's interactive challenges — the ones with
 * a checkbox — cannot be solved by a WebView nobody can see. Those still fail,
 * and fail with the original 403 so the error message stays honest; the browse
 * screen turns that into an "Open in WebView" button, which is where a human
 * answers it.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    private val userAgentProvider: () -> String
) : Interceptor {

    private val handler = Handler(Looper.getMainLooper())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isCloudflareChallenge()) return response

        // Deliberately not closed and replaced with an exception on failure: the
        // original response still carries the headers that let awaitSuccess()
        // name Cloudflare, and "blocked by Cloudflare" is a better thing for the
        // user to read than whatever this interceptor would invent.
        val solved = runCatching { solveChallenge(request) }.getOrDefault(false)
        if (!solved) return response

        response.close()
        // The UA interceptor ran *before* this one, so `request` still carries
        // whatever UA was current when clearance didn't exist. Retrying with it
        // would present a different string than the one that just passed and be
        // rejected — the retry has to be rebuilt, not reused.
        val earned = ClearanceUserAgents.get(context, request.url.host)
        val retry = if (earned != null) {
            request.newBuilder().header("User-Agent", earned).build()
        } else {
            request
        }
        return chain.proceed(retry)
    }

    private fun Response.isCloudflareChallenge(): Boolean =
        (code == 403 || code == 503) &&
            (
                header("cf-mitigated") != null ||
                    header("cf-ray") != null ||
                    header("server")?.contains("cloudflare", ignoreCase = true) == true
                )

    /**
     * Loads the URL in a WebView and waits for clearance.
     *
     * Blocks the calling thread, which is an OkHttp one — never the main thread,
     * and the guard below makes that explicit rather than deadlocking if it ever
     * changes. The wait is a poll of the cookie store rather than a callback on
     * `onPageFinished`, because a challenge involves several navigations and the
     * only signal that actually matters is the cookie appearing.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun solveChallenge(request: Request): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false

        val origin = request.url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()

        if (hasClearance(origin)) return true

        // Deliberately *not* set to the outgoing request's UA any more.
        //
        // Doing that was the bug: the app's default claims desktop Windows
        // Chrome, and a challenge evaluated inside an Android WebView reads
        // platform, touch support and renderer as well as the UA string. Those
        // contradict it, which is exactly what a challenge is for, so it was
        // never passable — the visible version of this looped on the checkbox
        // indefinitely. The WebView's own UA is the one it can defend, and
        // whatever passes gets recorded so OkHttp presents the same thing.
        val webView = AtomicReference<WebView?>(null)
        val solvedWith = AtomicReference<String?>(null)

        handler.post {
            runCatching {
                val view = WebView(context)
                webView.set(view)
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                solvedWith.set(view.settings.userAgentString ?: userAgentProvider())
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }
                // A plain client keeps navigation inside the WebView; without one
                // a redirect can be handed off to the system browser, where the
                // cookie would land somewhere this app can't read.
                view.webViewClient = WebViewClient()
                view.loadUrl(request.url.toString())
            }
        }

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var solved = false
        while (System.currentTimeMillis() < deadline) {
            if (hasClearance(origin)) {
                solved = true
                break
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                // The call was cancelled. Restore the flag so OkHttp still sees
                // it, and stop waiting.
                Thread.currentThread().interrupt()
                break
            }
        }

        handler.post {
            runCatching {
                webView.get()?.stopLoading()
                webView.get()?.destroy()
            }
        }
        runCatching { CookieManager.getInstance().flush() }

        if (solved) {
            // Recorded before returning, because the retry in intercept() reads
            // it back immediately.
            solvedWith.get()?.let { ua ->
                runCatching { ClearanceUserAgents.set(context, origin.host, ua) }
            }
        }

        return solved
    }

    private fun hasClearance(origin: HttpUrl): Boolean =
        runCatching { cookieJar.get(origin).any { it.name == CLEARANCE_COOKIE } }
            .getOrDefault(false)

    private companion object {
        const val CLEARANCE_COOKIE = "cf_clearance"

        /** A JS challenge normally resolves in well under ten seconds. This has
         *  to stay comfortably inside OkHttp's two-minute call timeout. */
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 250L
    }
}
