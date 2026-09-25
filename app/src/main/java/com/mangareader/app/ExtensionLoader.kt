package com.mangareader.app

import android.content.Context
import android.util.Log

/**
 * Loads Tachiyomi/Mihon extension APKs.
 *
 * Discovery and per-package classloading live in [ExtensionPackageLoader].
 * This object owns cache policy, public loading entry points and diagnostics.
 */
object ExtensionLoader {
    private const val TAG = "ExtensionLoader"

    data class LoadResult(
        val pkgName: String,
        val label: String,
        val sources: List<Any>,
        val error: Throwable? = null,
        val isNsfw: Boolean = false,
    )

    /** Fingerprint of the installed extension set that [cachedResults] was built from. */
    private var cachedFingerprint: String? = null
    private var cachedResults: List<LoadResult>? = null

    @Synchronized
    fun loadAllCached(context: Context): List<LoadResult> {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val candidates = ExtensionPackageLoader.candidatePackages(pm)
        val fingerprint = ExtensionPackageLoader.run { candidates.fingerprint() }

        val cached = cachedResults
        if (cached != null && fingerprint == cachedFingerprint) {
            Log.d(TAG, "Cache hit: ${cached.sumOf { it.sources.size }} sources")
            return cached
        }

        Log.d(TAG, "Cache miss — loading ${candidates.size} extension packages")
        val fresh = candidates.map { ExtensionPackageLoader.loadOne(appCtx, pm, it) }
        cachedResults = fresh
        cachedFingerprint = fingerprint
        return fresh
    }

    @Synchronized
    fun invalidate() {
        cachedResults = null
        cachedFingerprint = null
    }

    /** Uncached. Instantiates every extension fresh; prefer [loadAllCached]. */
    fun loadAll(context: Context): List<LoadResult> {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val candidates = ExtensionPackageLoader.candidatePackages(pm)
        Log.d(TAG, "Found ${candidates.size} extension packages")
        return candidates.map { ExtensionPackageLoader.loadOne(appCtx, pm, it) }
    }

    /** Human-readable report of a fresh extension load attempt. */
    fun diagnose(context: Context): String {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val candidates = runCatching {
            ExtensionPackageLoader.candidatePackages(pm)
        }.getOrElse {
            return "getInstalledPackages threw: $it"
        }
        val results = candidates.map { ExtensionPackageLoader.loadOne(appCtx, pm, it) }

        return ExtensionDiagnostics.build(
            pm = pm,
            candidates = candidates,
            results = results,
            supportedMin = ExtensionPackageLoader.LIB_VERSION_MIN,
            supportedMax = ExtensionPackageLoader.LIB_VERSION_MAX,
            metadataSourceClass = ExtensionPackageLoader.METADATA_SOURCE_CLASS,
            cacheSourceCount = cachedResults?.sumOf { result -> result.sources.size },
        )
    }
}
