package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal replacement for Tachiyomi's NetworkHelper.
 *
 * The original pulled in NetworkPreferences (PreferenceStore, from :core),
 * a Cloudflare/WebView interceptor stack, and DoH provider switching. None of
 * that is reachable in this app, so this keeps only what extensions actually
 * touch: `client`, `cloudflareClient`, `cookieJar`, and the user-agent hook.
 */
class NetworkHelper(context: Context) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        // HTTP/1.1 only, deliberately.
        //
        // Under HTTP/2 okhttp multiplexes several requests onto one connection,
        // and at least one CDN in use (cdn.manhwatoon.me) rejects a share of
        // those streams with a bare 400 — a minority of pages of any chapter,
        // well-formed URLs, and the very same URL succeeding later on a fresh
        // connection. Retrying in place doesn't help because the retry lands on
        // the same connection; dropping to 1.1 gives each concurrent request its
        // own connection and the failures go away.
        //
        // The cost is losing multiplexing. At this app's request volume that's
        // not measurable, and it's a one-line revert if a future source needs it.
        .protocols(listOf(Protocol.HTTP_1_1))
        .cache(
            Cache(
                directory = File(context.cacheDir, "network_cache"),
                maxSize = 5L * 1024 * 1024, // 5 MiB
            ),
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        // Inlined rather than using UserAgentInterceptor, so this file doesn't
        // depend on that class's exact constructor signature.
        //
        // Accept and Accept-Language ride along because a request carrying a
        // browser's User-Agent and nothing else a browser sends is a fairly
        // obvious tell. Both are only filled in when the extension hasn't set
        // them itself — a source that knows what it wants always wins.
        .addInterceptor { chain ->
            val request = chain.request()
            val patched = request.newBuilder()
            var changed = false

            if (request.header("User-Agent").isNullOrEmpty()) {
                patched.header("User-Agent", defaultUserAgentProvider())
                changed = true
            }
            if (request.header("Accept").isNullOrEmpty()) {
                patched.header("Accept", DEFAULT_ACCEPT)
                changed = true
            }
            if (request.header("Accept-Language").isNullOrEmpty()) {
                patched.header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
                changed = true
            }

            chain.proceed(if (changed) patched.build() else request)
        }
        .build()

    /**
     * Many extensions reference this expecting Cloudflare bypass. There is no
     * bypass here — it is the same client. Sources behind Cloudflare will fail;
     * that is a known, accepted limitation of dropping the WebView stack.
     */
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        /**
         * Kept roughly current on purpose.
         *
         * This previously claimed Chrome 120, which shipped in late 2023 — a
         * version that old is not a neutral default, it's a signal, and some
         * WAFs reject it outright. Worth bumping the major version every so
         * often; the exact number matters much less than not being years stale.
         */
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

        const val DEFAULT_ACCEPT: String =
            "text/html,application/xhtml+xml,application/xml;q=0.9," +
                "image/avif,image/webp,*/*;q=0.8"

        const val DEFAULT_ACCEPT_LANGUAGE: String = "en-US,en;q=0.9"
    }
}
