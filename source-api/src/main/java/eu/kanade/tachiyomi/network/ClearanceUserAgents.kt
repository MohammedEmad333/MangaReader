package eu.kanade.tachiyomi.network

import android.content.Context

/**
 * Remembers which User-Agent earned Cloudflare clearance for a given host.
 *
 * **Why this exists.** `cf_clearance` is bound to the UA that solved the
 * challenge and rejected under any other, so the WebView and OkHttp have to
 * agree. The first attempt made them agree by forcing the WebView to claim the
 * app's default UA — a desktop Chrome string — and that is unsolvable: every
 * other signal an interactive challenge reads (touch support, platform, screen,
 * WebGL renderer) says Android phone, the contradiction is the whole point of
 * the checkbox, and the challenge simply re-serves itself. Forever.
 *
 * So the agreement is reached from the other end. The WebView keeps its own
 * honest UA, the challenge becomes passable, and whatever string passed it is
 * recorded here for OkHttp to reuse against that host.
 *
 * **Scoped per host on purpose.** Switching the app-wide default UA to a mobile
 * one would fix this source and quietly change what all the others are served —
 * some extensions parse a desktop layout. This touches only hosts where a
 * challenge was actually solved, which are hosts that returned 403 to the old UA
 * anyway, so there is nothing to regress.
 */
object ClearanceUserAgents {

    private const val PREFS = "cf_clearance_ua"

    /**
     * The UA to use for [host], or null if no challenge has been solved for it.
     *
     * Walks up the domain because clearance is issued for the registrable domain
     * while requests go to subdomains — a cookie earned on `example.com` covers
     * `cdn.example.com`, and the UA rule has to follow it. Stops before the TLD
     * so `com` can never be a lookup key.
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

    /** Records that [userAgent] passed a challenge for [host]. */
    fun set(context: Context, host: String, userAgent: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(host.removePrefix("www."), userAgent)
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
