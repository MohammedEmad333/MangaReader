package eu.kanade.tachiyomi.network

import android.content.Context

/**
 * Remembers the User-Agent of a WebView session that successfully reached a host.
 *
 * **Why this exists.** Some hosts allow the real Android WebView to load a page
 * while rejecting the app's desktop-default User-Agent. Cloudflare may issue
 * `cf_clearance` during that visit, but some pages load without that cookie.
 * OkHttp still needs to use the browser's native UA, and the shared
 * `CookieManager` supplies the browser cookies.
 *
 * **Scoped per host on purpose.** Switching the app-wide default UA to a mobile
 * one would change what every extension receives — some parse a desktop layout.
 * Only hosts whose browser page was explicitly confirmed as accessible receive
 * this override.
 */
object ClearanceUserAgents {

    private const val PREFS = "cf_clearance_ua"

    /**
     * The browser UA to use for [host], or null until a page is confirmed accessible.
     *
     * Walks up the domain so a browser session accepted on `example.com` also
     * supplies its UA to `cdn.example.com`. Stops before the TLD so `com`
     * can never be a lookup key.
     */
    fun get(context: Context, host: String): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var candidate = host.removePrefix("www.")
        while (true) {
            prefs.getString(candidate, null)?.let { return it }
            val dot = candidate.indexOf('.')
            if (dot < 0) return null
            val parent = candidate.substring(dot + 1)
            if (!parent.contains('.')) return null
            candidate = parent
        }
    }

    /** Records the WebView UA after a successful page load for [host]. */
    fun set(context: Context, host: String, userAgent: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(host.removePrefix("www."), userAgent)
            .apply()
    }

    /** Forgets the recorded browser UA for [host] after the host rejects the session. */
    fun remove(context: Context, host: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(host.removePrefix("www."))
            .apply()
    }

    /** Forgets every recorded UA. For a settings screen, if one ever wants it. */
    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
