package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Extension(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val apkUrl: String,
    val isInstalled: Boolean = false,
    /** Display label, already mapped from the index's language code. */
    val lang: String = "",
    /** From the index's "nsfw" field; drives the 18+ badge. */
    val isNsfw: Boolean = false,
    /** versionName of the APK actually on the device, null when not installed. */
    val installedVersion: String? = null
) {
    /** Installed, but the repo index carries a different (newer) version. */
    val hasUpdate: Boolean
        get() = isInstalled &&
            installedVersion != null &&
            compareVersions(versionName, installedVersion) > 0
}

/**
 * Compares dotted version strings numerically: "1.4.9" is older than "1.4.10",
 * which a plain string comparison gets backwards. Non-numeric parts compare as 0,
 * so a malformed version degrades to "equal" rather than claiming a false update.
 */
internal fun compareVersions(a: String, b: String): Int {
    val left = a.split('.')
    val right = b.split('.')
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
        val r = right.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
        if (l != r) return l.compareTo(r)
    }
    return 0
}

object ExtensionManager {
    /**
     * How long a downloaded repo index is reused before being re-fetched.
     * Matches the `maxAge` `Requests.kt` puts on ordinary GETs, so the two
     * caches agree about how stale a catalogue is allowed to be.
     */
    private const val INDEX_TTL_MS = 10 * 60 * 1000L

    private class CachedIndex(val json: String, val fetchedAt: Long)

    @Volatile
    private var indexCache: Map<String, CachedIndex> = emptyMap()

    /** Drops the cached indexes, so the next fetch goes to the network. */
    fun invalidateIndexCache() {
        indexCache = emptyMap()
    }

    /**
     * 1. FETCH: Reads the JSON lists from your saved repository URLs.
     *
     * **The download is cached; the parse is not.** `available` in
     * `BrowseScreen` is composable-local state, so leaving the Extensions tab
     * drops it and returning re-runs this — which meant re-downloading the full
     * Keiyoushi `index.json`, well over a thousand entries, on every single
     * visit. That is the "why does it load whenever I open the extensions tab"
     * report. Note this path uses a bare [HttpURLConnection] rather than the
     * shared OkHttp client, so it does not get the 10-minute response cache the
     * rest of the app has; the cache below is that cache, by hand.
     *
     * Re-parsing every time is deliberate and is what makes caching safe here.
     * [Extension.isInstalled] and [Extension.installedVersion] are resolved
     * against the [PackageManager] inside [parseIndex], so a cached *parse*
     * would keep claiming an extension is installed after it was removed.
     * Caching the raw text and re-parsing keeps install state exact while still
     * removing the network round trip, and parsing a few thousand entries is
     * milliseconds against a multi-megabyte download.
     *
     * @param force skips the cache — for an explicit user-initiated refresh.
     *   Install and uninstall do **not** need it: those change install state,
     *   which the re-parse already picks up.
     */
    suspend fun fetchAvailable(
        context: Context,
        force: Boolean = false
    ): List<Extension> = withContext(Dispatchers.IO) {
        val repos = ExtensionRepos.list(context)
        val available = mutableListOf<Extension>()
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val cache = HashMap(indexCache)

        for (repoUrl in repos) {
            try {
                val cached = cache[repoUrl]
                val fresh = !force &&
                    cached != null &&
                    now - cached.fetchedAt < INDEX_TTL_MS

                val jsonStr = if (fresh) {
                    cached!!.json
                } else {
                    val conn = URL(repoUrl).openConnection() as HttpURLConnection
                    val downloaded = conn.inputStream.bufferedReader().use { it.readText() }
                    cache[repoUrl] = CachedIndex(downloaded, now)
                    downloaded
                }

                available += ExtensionIndexParser.parse(context, jsonStr, repoUrl, pm)
            } catch (e: Exception) {
                // A repo that fails now keeps whatever it last served, so one
                // unreachable repo doesn't empty the screen of the others.
                e.printStackTrace()
                cache[repoUrl]?.let { stale ->
                    runCatching { available += ExtensionIndexParser.parse(context, stale.json, repoUrl, pm) }
                }
            }
        }

        indexCache = cache
        available
    }

    /**
     * The intent that asks the system to uninstall a package.
     *
     * Returned rather than started, because the caller launches it through an
     * `ActivityResultLauncher`: that keeps the system dialog inside this app's
     * task and gives a callback when it closes. The first version of this
     * called `startActivity` with `FLAG_ACTIVITY_NEW_TASK` and got neither —
     * the dialog went somewhere else and the app learned nothing, which is a
     * poor way to find out that the request was being refused for want of
     * `REQUEST_DELETE_PACKAGES` in the manifest.
     *
     * The system still shows its own confirmation. This app has no business
     * removing a package without one.
     */
    fun uninstallIntent(pkgName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))

    /** Whether [pkgName] is installed right now. Used to check an uninstall took. */
    fun isInstalled(context: Context, pkgName: String): Boolean =
        installedInfo(context.packageManager, pkgName) != null

    private fun installedInfo(pm: PackageManager, pkg: String) = try {
        pm.getPackageInfo(pkg, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * 2. INSTALL: Downloads the APK and opens Android's package installer.
     */
    suspend fun install(context: Context, ext: Extension) {
        ExtensionInstaller.install(context, ext)
    }

    /**
     * 3. RUN: Finds installed extensions and loads their Source classes dynamically.
     */
    fun loadInstalledSources(context: Context): List<Source> =
        ExtensionSourceLoader.load(context)

}
