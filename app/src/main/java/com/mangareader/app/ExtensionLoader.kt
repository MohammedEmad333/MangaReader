package com.mangareader.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import dalvik.system.PathClassLoader

/**
 * Loads Tachiyomi/Mihon extension APKs.
 *
 * IMPORTANT: this file only compiles and only works once your app actually
 * contains the `eu.kanade.tachiyomi.source.*` API classes (see notes at the
 * bottom). Discovery alone is not enough — the extension dex links against
 * those classes at load time and will throw NoClassDefFoundError without them.
 *
 * CACHING: `loadAll` classloads and instantiates every installed extension on
 * every call, which is expensive (26 APKs / 95 sources on this device). Callers
 * that just want the current source list should use [loadAllCached], which does
 * the cheap PackageManager enumeration, compares it against the last one, and
 * only re-instantiates when the installed set has actually changed.
 */
object ExtensionLoader {

    private const val TAG = "ExtensionLoader"

    // Tachiyomi's real discovery contract — NOT an intent filter.
    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    // Which extensions-lib versions your host implements. Widen only once
    // you've actually implemented the API surface those versions require.
    //
    // 1.6 is currently claimed and not fully implemented, and that was found
    // the hard way: an Elite Babes build declaring 1.6 loaded cleanly, passed
    // this gate, and then closed the app on every open by reaching for a model
    // class the vendored source-api doesn't have.
    //
    // It is deliberately still 1.6, for two reasons. Narrowing to 1.5 would
    // also refuse 1.6 extensions that work — most of them never touch the parts
    // that are missing, and at least one on this device updated to 1.6 and was
    // fine. And this gate is the wrong instrument regardless: `versionName` is
    // what the extension *claims*, so it can refuse an honest mismatch and
    // cannot see a dishonest one. What catches the real failure is
    // `sourceFailureMessage`, which turns the LinkageError into a message
    // naming the missing symbol instead of a process that vanishes.
    //
    // Before widening past 1.6, implement the surface first and check it
    // against a real extension's dex rather than against this constant.
    private const val LIB_VERSION_MIN = 1.4
    private const val LIB_VERSION_MAX = 1.6

    data class LoadResult(
        val pkgName: String,
        val label: String,
        val sources: List<Any>,   // eu.kanade.tachiyomi.source.Source instances
        val error: Throwable? = null,
        /** From the tachiyomi.extension.nsfw meta-data key; drives the 18+ badge. */
        val isNsfw: Boolean = false,
    )

    // ---------- cache ----------

    /** Fingerprint of the installed extension set that [cachedResults] was built from. */
    private var cachedFingerprint: String? = null
    private var cachedResults: List<LoadResult>? = null

    /**
     * Same as [loadAll], but reuses the previously loaded sources when the set of
     * installed extension packages hasn't changed.
     *
     * The fingerprint covers package name, versionName and lastUpdateTime, so an
     * install, an uninstall, an update, and a same-version reinstall all miss the
     * cache. That keeps the ON_RESUME rescan honest — it still picks up a package
     * that arrived from the system installer — while costing one PackageManager
     * query instead of 26 PathClassLoaders.
     *
     * Synchronized because Browse, global search and the lifecycle observer can
     * all reach this concurrently from Dispatchers.IO; without it a cold start
     * can classload everything two or three times over.
     */
    @Synchronized
    fun loadAllCached(context: Context): List<LoadResult> {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val candidates = candidatePackages(pm)
        val fingerprint = candidates.fingerprint()

        val cached = cachedResults
        if (cached != null && fingerprint == cachedFingerprint) {
            Log.d(TAG, "Cache hit: ${cached.sumOf { it.sources.size }} sources")
            return cached
        }

        Log.d(TAG, "Cache miss — loading ${candidates.size} extension packages")
        val fresh = candidates.map { loadOne(appCtx, pm, it) }
        cachedResults = fresh
        cachedFingerprint = fingerprint
        return fresh
    }

    /**
     * Drops the cache so the next [loadAllCached] reloads from scratch. The
     * fingerprint already catches package changes, so this is only needed to
     * recover from a load that failed for a reason outside the package set
     * (e.g. an Injekt binding that wasn't registered yet).
     */
    @Synchronized
    fun invalidate() {
        cachedResults = null
        cachedFingerprint = null
    }

    /** Uncached. Instantiates every extension fresh; prefer [loadAllCached]. */
    fun loadAll(context: Context): List<LoadResult> {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val candidates = candidatePackages(pm)
        Log.d(TAG, "Found ${candidates.size} extension packages")
        return candidates.map { loadOne(appCtx, pm, it) }
    }

    /** Every installed package declaring the tachiyomi.extension feature. */
    @Suppress("DEPRECATION")
    private fun candidatePackages(pm: PackageManager): List<PackageInfo> {
        return pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS)
            .filter { pkg -> pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } }
    }

    /**
     * Cheap identity for the installed extension set. Sorted, because
     * getInstalledPackages makes no ordering guarantee and an unsorted join
     * would report a spurious change.
     */
    private fun List<PackageInfo>.fingerprint(): String =
        map { "${it.packageName}|${it.versionName}|${it.lastUpdateTime}" }
            .sorted()
            .joinToString(";")

    private fun loadOne(context: Context, pm: PackageManager, pkg: PackageInfo): LoadResult {
        val pkgName = pkg.packageName
        val label = pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkgName

        return try {
            // Lib version is derived from the versionName, e.g. "1.4.23" -> 1.4.
            // There is no `extension.lib.version` metadata key; that's why your
            // probe printed null for it.
            val versionName = pkg.versionName.orEmpty()
            val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
                ?: return LoadResult(pkgName, label, emptyList(),
                    IllegalStateException("Unparseable versionName: $versionName"))

            if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                return LoadResult(pkgName, label, emptyList(),
                    IllegalStateException("Lib version $libVersion outside supported range"))
            }

            val appInfo: ApplicationInfo =
                pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
            val metaData = appInfo.metaData
                ?: return LoadResult(pkgName, label, emptyList(),
                    IllegalStateException("No application meta-data"))

            val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1

            // Parent MUST be your app's classloader so the extension can see
            // the eu.kanade.tachiyomi.source classes you supply.
            val loader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)

            // Only ONE key matters. A single extension APK may name several
            // classes here, separated by ';'. Each may turn out to be either a
            // Source or a SourceFactory — you find out by instantiating it.
            val declared = metaData.getString(METADATA_SOURCE_CLASS).orEmpty()

            val sources = buildList<Any> {
                declared.splitClassNames(pkgName).forEach { fqcn ->
                    val obj = loader.loadClass(fqcn).getDeclaredConstructor().newInstance()
                    if (obj.isSourceFactory()) {
                        // Once source-api is on your classpath, replace this whole
                        // branch with: addAll((obj as SourceFactory).createSources())
                        val created = obj.javaClass
                            .getMethod("createSources")
                            .invoke(obj) as List<*>
                        addAll(created.filterNotNull())
                    } else {
                        add(obj)
                    }
                }
            }

            Log.d(TAG, "$pkgName -> ${sources.size} sources (nsfw=$isNsfw)")
            LoadResult(pkgName, label, sources, isNsfw = isNsfw)
        } catch (t: Throwable) {
            // Catch Throwable, not Exception: NoClassDefFoundError is an Error.
            Log.e(TAG, "Failed to load $pkgName", t)
            LoadResult(pkgName, label, emptyList(), t)
        }
    }

    /**
     * Human-readable report of a fresh extension load attempt.
     */
    fun diagnose(context: Context): String {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val candidates = runCatching { candidatePackages(pm) }.getOrElse {
            return "getInstalledPackages threw: $it"
        }
        val results = candidates.map { loadOne(appCtx, pm, it) }

        return ExtensionDiagnostics.build(
            pm = pm,
            candidates = candidates,
            results = results,
            supportedMin = LIB_VERSION_MIN,
            supportedMax = LIB_VERSION_MAX,
            metadataSourceClass = METADATA_SOURCE_CLASS,
            cacheSourceCount = cachedResults?.sumOf { result -> result.sources.size },
        )
    }

    /**
     * True if this object implements eu.kanade.tachiyomi.source.SourceFactory,
     * checked by name so it works before source-api is on the classpath.
     */
    private fun Any.isSourceFactory(): Boolean {
        var c: Class<*>? = javaClass
        while (c != null) {
            if (c.interfaces.any { it.name == "eu.kanade.tachiyomi.source.SourceFactory" }) {
                return true
            }
            c = c.superclass
        }
        return false
    }

    /** Metadata values are comma-separated; a leading '.' means "relative to package". */
    private fun String.splitClassNames(pkgName: String): List<String> =
        split(';', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkgName + it else it }
}

/*
 * WHY YOUR CURRENT CODE FINDS NOTHING, AND WHAT'S STILL MISSING
 * ------------------------------------------------------------
 * 1. Discovery: extensions declare <uses-feature android:name="tachiyomi.extension"/>,
 *    not an intent filter for com.mangareader.app.EXTENSION. queryIntentActivities
 *    will always return 0. Fixed above. You can also drop the <queries> block for
 *    your custom action from the manifest.
 *
 * 2. Linking: extension APKs depend on eu.kanade.tachiyomi.source.* as compileOnly.
 *    Those classes are NOT inside the APK — the host app must provide the real
 *    implementations. Until it does, every load fails with NoClassDefFoundError,
 *    exactly as in your log. Vendor Mihon's `source-api` module (Apache-2.0) into
 *    your project, along with its runtime deps: kotlinx-coroutines, OkHttp, jsoup,
 *    kotlinx-serialization, and Injekt.
 *
 * 3. Injekt bindings: extension constructors commonly do Injekt.get<NetworkHelper>()
 *    or injectLazy(). Register those bindings BEFORE calling loadAll(), or
 *    instantiation throws.
 *
 * 4. Adapter: eu.kanade.tachiyomi.source.CatalogueSource is not your
 *    com.mangareader.app.Source. Write an adapter that maps
 *      getPopularManga/getSearchManga -> listSeries()
 *      getChapterList                 -> listChapters()
 *      getPageList + image download   -> loadPages(): List<File>
 *    Note loadPages must download each Page.imageUrl through the source's own
 *    OkHttp client and headers; hotlinking without them will 403 on most sites.
 */
