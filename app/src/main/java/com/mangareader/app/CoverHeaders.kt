package com.mangareader.app

import android.content.Context
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Gives cover images the same request headers their source uses.
 *
 * `App.newImageLoader` already routes Coil through the shared client, which
 * carries the User-Agent, the cookie jar and the Cloudflare interceptor. That
 * fixed the sources that only check those. It does **not** carry `Referer`,
 * because Referer is not a property of the client — it is a property of the
 * *source*, built by `HttpSource.headersBuilder()` from its own `baseUrl`.
 *
 * So the app ended up asymmetric. Page images go out through
 * `http.getImage(page)` (`TachiyomiSourceAdapter`), which builds the request
 * from the source and keeps Referer. Covers go out as
 * `AsyncImage(model = "https://…")` — a bare URL string with no source attached
 * — and a site with hotlink protection answers 403. The visible result is a
 * catalogue that lists every title correctly above a grid of grey boxes, which
 * is the "no covers on this source" report.
 *
 * ### Why an interceptor rather than a parameter
 *
 * The obvious fix is to hand `CoverImage` a source and build the request from
 * it. That works on the browse screens, and fails everywhere else: the library
 * grid, history, and global search all draw covers with no single source in
 * scope, and those are exactly the screens where the grey boxes also appear.
 * Matching on the *host* covers all of them at one point, with no call-site
 * churn and nothing to remember at the next call site someone adds.
 *
 * ### Matching
 *
 * A cover is frequently served from a CDN subdomain rather than from
 * `baseUrl`'s exact host, so exact-host matching misses. This walks the label
 * boundary in both directions — `cdn.example.com` matches a source at
 * `example.com`, and a source at `www.example.com` matches a cover on
 * `example.com` — which is deliberately not a public-suffix lookup. The failure
 * mode of being too loose here is sending a Referer a site didn't need; the
 * failure mode of being too tight is the bug. Note this cannot help a source
 * whose covers live on an unrelated domain — those need the source in scope.
 *
 * Everything is wrapped so a lookup failure can never break image loading: the
 * worst case is the bare request that is already today's behaviour.
 */
object CoverHeaders {

    /** host of an `HttpSource.baseUrl` → that source's headers. */
    @Volatile
    private var byHost: Map<String, Headers>? = null

    /**
     * Built once and reused. Populating it calls [SourceManager.listAllSources],
     * which classloads extension APKs on first use — so this must never run on
     * the main thread. It doesn't: the only caller is [interceptor], and OkHttp
     * runs interceptors on its own dispatcher.
     */
    private fun table(context: Context): Map<String, Headers> {
        byHost?.let { return it }
        val built = runCatching {
            val map = HashMap<String, Headers>()
            SourceManager.listAllSources(context).forEach { src ->
                val http = (src as? TachiyomiSourceAdapter)
                    ?.catalogueSource as? HttpSource ?: return@forEach
                val host = runCatching {
                    java.net.URI(http.baseUrl).host
                }.getOrNull()?.lowercase().orEmpty()
                if (host.isNotBlank() && !map.containsKey(host)) {
                    map[host] = http.headers
                }
            }
            map
        }.getOrDefault(emptyMap())
        byHost = built
        return built
    }

    /**
     * Drops the cached table. Call after installing or uninstalling an
     * extension, or after editing a source — otherwise a newly added source
     * serves bare cover requests until the process restarts.
     */
    fun invalidate() {
        byHost = null
    }

    /** True when [coverHost] is the same site as [sourceHost], or a subdomain. */
    private fun matches(coverHost: String, sourceHost: String): Boolean =
        coverHost == sourceHost ||
            coverHost.endsWith(".$sourceHost") ||
            sourceHost.endsWith(".$coverHost")

    /**
     * Adds the owning source's headers to a cover request, without overwriting
     * anything already set. Coil's own headers win, and a request that matches
     * no source goes out exactly as it does today.
     */
    fun interceptor(context: Context) = Interceptor { chain ->
        val request = chain.request()
        val patched = runCatching {
            val host = request.url.host.lowercase()
            val source = table(context).entries
                .firstOrNull { matches(host, it.key) }
                ?.value
                ?: return@runCatching null

            val builder = request.newBuilder()
            var changed = false
            source.forEach { (name, value) ->
                // Only fill gaps. A header Coil or the shared client already
                // set is the more specific one and stays.
                if (request.header(name) == null) {
                    builder.header(name, value)
                    changed = true
                }
            }
            if (changed) builder.build() else null
        }.getOrNull()

        chain.proceed(patched ?: request)
    }
}
