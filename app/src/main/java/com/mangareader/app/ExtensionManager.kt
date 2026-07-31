package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    // The action that extension APKs must broadcast in their manifest
    private const val EXTENSION_ACTION = "com.mangareader.app.EXTENSION"

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

                available += parseIndex(context, jsonStr, repoUrl, pm)
            } catch (e: Exception) {
                // A repo that fails now keeps whatever it last served, so one
                // unreachable repo doesn't empty the screen of the others.
                e.printStackTrace()
                cache[repoUrl]?.let { stale ->
                    runCatching { available += parseIndex(context, stale.json, repoUrl, pm) }
                }
            }
        }

        indexCache = cache
        available
    }

    /**
     * Reads a repository index in either of the two shapes now in the wild.
     *
     * **Flat (original):** a JSON array of
     * `{name, pkg, apk, lang, version, nsfw}`, where `apk` is a filename
     * relative to the index's own `apk/` directory.
     *
     * **Nested (current Keiyoushi):** an object of repo metadata carrying
     * `extensionList.extensions[]`, each
     * `{name, packageName, versionName, contentWarning, resources.apkUrl,
     * sources[].language}` — absolute apk URLs, language moved down onto the
     * sources, and the boolean nsfw flag replaced by a three-way warning.
     *
     * Reading only the flat shape doesn't fail loudly, which is what made this
     * hard to see: Keiyoushi left the old `index.min.json` path serving a
     * two-entry stub named "Outdated App" and "Update to Mihon 0.20.1+", so the
     * screen showed two plausible-looking extensions instead of an error, and
     * 1366 others were simply gone. The full list moved to `index.json` on the
     * same branch — **a repo URL ending in `index.min.json` still needs
     * changing by hand; this parser cannot conjure entries the stub omits.**
     */
    private fun parseIndex(
        context: Context,
        json: String,
        repoUrl: String,
        pm: PackageManager
    ): List<Extension> {
        val trimmed = json.trimStart()
        val entries = mutableListOf<Extension>()

        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                runCatching { flatEntry(arr.getJSONObject(i), repoUrl, pm) }
                    .getOrNull()
                    ?.let(entries::add)
            }
        } else {
            val arr = JSONObject(trimmed)
                .optJSONObject("extensionList")
                ?.optJSONArray("extensions")
                ?: return emptyList()
            // Every source in the catalogue, named, in one pass — including the
            // ones that aren't installed. `SourceManager.listAllSources` can
            // only name what is present, so a source whose extension was
            // removed before 0.94 shipped would otherwise never be named by
            // anything: nothing will ever list it again. This index is the only
            // place those names still exist.
            val names = HashMap<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                runCatching { nestedEntry(obj, pm) }
                    .getOrNull()
                    ?.let(entries::add)
                // Outside the runCatching above: a malformed entry that can't
                // become an Extension may still carry usable source names, and
                // one that can't is skipped here on its own.
                runCatching { collectSourceNames(obj, names) }
            }
            runCatching { SourceNames.record(context, names) }
        }
        return entries
    }

    /**
     * Pulls `sources[].id` / `sources[].name` out of one index entry.
     *
     * The index gives a bare numeric id; this app prefixes every extension
     * source with `tachi:`, matching `TachiyomiSourceAdapter.id`. Getting that
     * wrong would store 1367 names under keys nothing ever looks up, silently.
     */
    private fun collectSourceNames(obj: JSONObject, into: MutableMap<String, String>) {
        val srcs = obj.optJSONArray("sources") ?: return
        for (i in 0 until srcs.length()) {
            val src = srcs.optJSONObject(i) ?: continue
            val id = src.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = src.optString("name").takeIf { it.isNotBlank() } ?: continue
            into["tachi:$id"] = name
        }
    }

    private fun flatEntry(obj: JSONObject, repoUrl: String, pm: PackageManager): Extension {
        val pkg = obj.getString("pkg")

        // Keep the installed versionName, not just the boolean: it's what tells
        // an out-of-date extension from an up-to-date one.
        val installedInfo = installedInfo(pm, pkg)

        // Relative filenames point at the index's own apk/ subdirectory.
        val rawApkUrl = obj.getString("apk")
        val relativePath = if (rawApkUrl.startsWith("http")) rawApkUrl else "apk/$rawApkUrl"

        return Extension(
            // Index entries are named "Tachiyomi: Foo"; the prefix is noise on
            // every single row.
            name = obj.getString("name")
                .removePrefix("Tachiyomi: ")
                .removePrefix("Mihon: "),
            pkgName = pkg,
            versionName = obj.getString("version"),
            apkUrl = URL(URL(repoUrl), relativePath).toString(),
            isInstalled = installedInfo != null,
            lang = langLabel(obj.optString("lang", "")),
            isNsfw = obj.optInt("nsfw", 0) == 1,
            installedVersion = installedInfo?.versionName
        )
    }

    private fun nestedEntry(obj: JSONObject, pm: PackageManager): Extension {
        val pkg = obj.getString("packageName")
        val installedInfo = installedInfo(pm, pkg)

        // Language lives on the sources now. One distinct language means that
        // language; a package whose sources disagree is a multi-language
        // extension, which is what the flat index's "all" meant.
        val langs = mutableSetOf<String>()
        obj.optJSONArray("sources")?.let { srcs ->
            for (i in 0 until srcs.length()) {
                srcs.optJSONObject(i)
                    ?.optString("language")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(langs::add)
            }
        }

        return Extension(
            name = obj.getString("name")
                .removePrefix("Tachiyomi: ")
                .removePrefix("Mihon: "),
            pkgName = pkg,
            versionName = obj.getString("versionName"),
            // Required: an entry with no downloadable apk is not installable,
            // and getString throwing here drops it rather than listing a row
            // whose Install button can only fail.
            apkUrl = obj.getJSONObject("resources").getString("apkUrl"),
            isInstalled = installedInfo != null,
            lang = langLabel(langs.singleOrNull() ?: "all"),
            // Three values now: SAFE, MIXED, NSFW. Anything but SAFE carries the
            // badge — nothing filters on this flag, it only labels, so erring
            // towards showing it costs nothing and hides nothing.
            isNsfw = obj.optString("contentWarning", SAFE) != SAFE,
            installedVersion = installedInfo?.versionName
        )
    }

    private const val SAFE = "CONTENT_WARNING_SAFE"

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
     * 2. INSTALL: Downloads the APK to the cache and triggers the Android installer.
     */
        suspend fun install(context: Context, ext: Extension) {
        withContext(Dispatchers.IO) {
            try {
                var currentUrl = ext.apkUrl
                var connection: HttpURLConnection
                
                // Loop to handle potential HTTP redirects (e.g., GitHub releases)
                while (true) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connection.instanceFollowRedirects = false
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                        val redirectedUrl = connection.getHeaderField("Location")
                        if (redirectedUrl != null) {
                            currentUrl = redirectedUrl
                            continue
                        }
                    }
                    break
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val apkFile = File(context.cacheDir, "${ext.pkgName}.apk")
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Trigger the system installation intent on the Main thread
                withContext(Dispatchers.Main) {
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Install Error: ${e.localizedMessage ?: e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                e.printStackTrace()
            }
        }
    }


    /**
     * 3. RUN: Finds installed extensions and loads their Source classes dynamically.
     */
    fun loadInstalledSources(context: Context): List<Source> {
        val pm = context.packageManager
        val intent = Intent(EXTENSION_ACTION)
        
        val resolved = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val loadedSources = mutableListOf<Source>()
        
        for (info in resolved) {
            try {
                val pkg = info.activityInfo.packageName
                val appInfo = pm.getApplicationInfo(pkg, 0)
                
                val className = info.activityInfo.metaData?.getString("source_class")
                
                if (className != null) {
                    val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                    val clazz = Class.forName(className, false, classLoader)
                    
                    val source = clazz.getDeclaredConstructor().newInstance() as Source
                    loadedSources.add(source)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return loadedSources
    }
}
