package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
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
        // HTTP/1.1 was forced here, globally, and has been removed. The history
        // is worth keeping because both halves of it are instructive.
        //
        // It went in for cdn.manhwatoon.me, which rejects a share of multiplexed
        // HTTP/2 streams with a bare 400. Dropping to 1.1 gave each concurrent
        // request its own connection and took that source's failure rate from
        // 33% to 18% — a real improvement, and never a fix. Three more attempts
        // followed. The handoff's §0 has said for a while that the next move is
        // to read the 400 response body rather than tune this further; the
        // connection probe added in 0.38 can now do exactly that.
        //
        // What it cost, meanwhile, was invisible until something measured it.
        // The client announces itself as Chrome on Android — via
        // ClearanceUserAgents, deliberately, so that Cloudflare clearance earned
        // in a WebView is honoured — and then could not negotiate h2, which real
        // Chrome always does. A client whose claimed identity contradicts its
        // observed behaviour is precisely what bot detection exists to catch,
        // and §5 already has a section about this app making that exact mistake
        // with User-Agents. Same error, one layer down.
        //
        // The symptom: allporncomic.com answered 200 to one phone and
        // `cf-mitigated: challenge` to another, where the only difference was
        // that the second is a tablet whose WebView UA omits the `Mobile` token
        // — so it claimed desktop Chrome over HTTP/1.1, a sharper contradiction
        // than mobile Chrome over HTTP/1.1. Neither phone could solve it, because
        // the WebView was never challenged and so no clearance was ever issued.
        //
        // If manhwatoon regresses, do NOT put this line back without first
        // probing it. Restoring it re-breaks the challenge path for every other
        // source to buy back a fix that never worked.
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

            // A host we hold Cloudflare clearance for gets the UA that earned
            // it, overriding even a UA the extension set itself. That looks
            // rude, and is nevertheless right: cf_clearance is rejected under
            // any other string, so honouring the extension's preference here
            // would throw away the challenge the user just solved by hand and
            // put the source straight back to 403.
            val clearanceUserAgent = ClearanceUserAgents.get(context, request.url.host)
            if (clearanceUserAgent != null) {
                patched.header("User-Agent", clearanceUserAgent)
                changed = true
            } else if (request.header("User-Agent").isNullOrEmpty()) {
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
        // After the UA interceptor, not before: the challenge is solved in a
        // WebView set to the same User-Agent the request carries, and cf_clearance
        // is rejected if a later request presents a different one.
        .addInterceptor(
            CloudflareInterceptor(
                context.applicationContext,
                cookieJar,
                // A lambda rather than the string, so this reads the companion
                // constant at call time instead of during construction.
                { defaultUserAgentProvider() }
            )
        )
        .build()

    /**
     * Historically the client with the Cloudflare bypass, as opposed to [client]
     * without it. Both now carry the interceptor, because most extensions reach
     * for [client] and would otherwise still hit a wall — the split only ever
     * made sense when the bypass was expensive.
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

        /**
         * Deliberately &#42;&#47;&#42; (a wildcard) rather than a browser's
         * document Accept.
         *
         * This header goes on every request the app makes, and most of them are
         * images. Announcing `text/html,application/xhtml+xml,…` while asking for
         * a JPEG is wrong, and a strict WAF is entitled to call that a bad
         * request. A browser sends a different Accept per request type; we can't
         * tell them apart here, so the honest answer is "anything".
         */
        const val DEFAULT_ACCEPT: String = "*/*"

        const val DEFAULT_ACCEPT_LANGUAGE: String = "en-US,en;q=0.9"
    }
}
