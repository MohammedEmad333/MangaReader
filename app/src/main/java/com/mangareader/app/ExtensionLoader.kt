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
 */
object ExtensionLoader {

    private const val TAG = "ExtensionLoader"

    // Tachiyomi's real discovery contract — NOT an intent filter.
    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    // Which extensions-lib versions your host implements. Widen only once
    // you've actually implemented the API surface those versions require.
    private const val LIB_VERSION_MIN = 1.4
    private const val LIB_VERSION_MAX = 1.6

    data class LoadResult(
        val pkgName: String,
        val label: String,
        val sources: List<Any>,   // eu.kanade.tachiyomi.source.Source instances
        val error: Throwable? = null,
    )

    fun loadAll(context: Context): List<LoadResult> {
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val candidates = pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS)
            .filter { pkg -> pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } }

        Log.d(TAG, "Found ${candidates.size} extension packages")
        return candidates.map { loadOne(context, pm, it) }
    }

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
            LoadResult(pkgName, label, sources)
        } catch (t: Throwable) {
            // Catch Throwable, not Exception: NoClassDefFoundError is an Error.
            Log.e(TAG, "Failed to load $pkgName", t)
            LoadResult(pkgName, label, emptyList(), t)
        }
    }

    /**
     * Human-readable report of what loadAll() found and why each package failed.
     * Wire this to the existing diagnostic button in ExtensionsScreen.
     */
    fun diagnose(context: Context): String {
        val out = StringBuilder()
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val candidates = runCatching {
            pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS)
                .filter { pkg -> pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } }
        }.getOrElse {
            return "getInstalledPackages threw: $it"
        }

        out.appendLine("Packages declaring $EXTENSION_FEATURE: ${candidates.size}")
        out.appendLine("Supported lib versions: $LIB_VERSION_MIN - $LIB_VERSION_MAX")
        out.appendLine()

        val results = candidates.map { loadOne(context, pm, it) }
        val ok = results.count { it.error == null }
        out.appendLine("Loaded OK: $ok / ${results.size}")
        out.appendLine("Total sources: ${results.sumOf { it.sources.size }}")
        out.appendLine()

        for (r in results.take(5)) {
            out.appendLine("• ${r.pkgName}")
            out.appendLine("  label: ${r.label}")

            val md = runCatching {
                pm.getApplicationInfo(r.pkgName, PackageManager.GET_META_DATA).metaData
            }.getOrNull()
            out.appendLine("  class:   ${md?.getString(METADATA_SOURCE_CLASS) ?: "-"}")
            out.appendLine("  (factory status is only knowable after instantiation)")
            out.appendLine("  version: ${runCatching { pm.getPackageInfo(r.pkgName, 0).versionName }.getOrNull()}")

            if (r.error == null) {
                out.appendLine("  ✓ ${r.sources.size} source(s)")
            } else {
                out.appendLine("  ✗ ${r.error.rootCauseChain()}")
            }
            out.appendLine()
        }

        if (results.size > 5) out.appendLine("(${results.size - 5} more not shown)")
        return out.toString()
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

    /** Flattens the cause chain — the last entry is the thing actually missing. */
    private fun Throwable.rootCauseChain(): String {
        val parts = mutableListOf<String>()
        var t: Throwable? = this
        var depth = 0
        while (t != null && depth < 6) {
            parts += "${t.javaClass.simpleName}: ${t.message?.take(120)}"
            t = t.cause
            depth++
        }
        return parts.joinToString("\n     caused by ")
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
