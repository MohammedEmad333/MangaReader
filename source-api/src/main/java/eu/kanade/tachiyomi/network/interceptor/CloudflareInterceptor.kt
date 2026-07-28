package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.AndroidCookieJar
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
 * **The User-Agent has to match.** `cf_clearance` is issued against the UA that
 * solved the challenge and is rejected if a later request presents a different
 * one. The WebView is therefore set to whatever UA the outgoing request carries,
 * which is why this interceptor must be added *after* the one in `NetworkHelper`
 * that fills the UA in.
 *
 * **What this does not do.** Cloudflare's interactive challenges — the ones with
 * a checkbox — cannot be solved by a WebView nobody can see. Those still fail,
 * and fail with the original 403 so the error message stays honest. Handling
 * them needs a visible WebView the user can tap, which is a UI change rather
 * than a networking one.
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
        return chain.proceed(request)
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

        val userAgent = request.header("User-Agent") ?: userAgentProvider()
        // The WebView is created on the main thread and torn down from this one,
        // so the reference crosses threads and needs to actually be published.
        val webView = AtomicReference<WebView?>(null)

        handler.post {
            runCatching {
                val view = WebView(context)
                webView.set(view)
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = userAgent
                }
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
