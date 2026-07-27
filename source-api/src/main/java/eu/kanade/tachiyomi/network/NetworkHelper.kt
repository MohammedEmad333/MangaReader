package eu.kanade.tachiyomi.network

import android.content.Context
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
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent").isNullOrEmpty()) {
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", defaultUserAgentProvider())
                        .build(),
                )
            } else {
                chain.proceed(request)
            }
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
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
