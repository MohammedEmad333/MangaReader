package com.mangareader.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import dalvik.system.PathClassLoader

internal object ExtensionPackageLoader {
    private const val TAG = "ExtensionLoader"

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
    internal const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val ANIME_METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val ANIME_METADATA_NSFW = "tachiyomi.animeextension.nsfw"
    private const val ANIYOMIX_EXTENSION_LIB = "aniyomix.extensionLib"
    private const val ANIYOMIX_CONTENT_WARNING = "aniyomix.contentWarning"

    internal const val LIB_VERSION_MIN = 1.4
    internal const val LIB_VERSION_MAX = 1.6

    @Suppress("DEPRECATION")
    fun candidatePackages(pm: PackageManager): List<PackageInfo> =
        pm.getInstalledPackages(PackageManager.GET_CONFIGURATIONS)
            .filter { pkg ->
                pkg.reqFeatures.orEmpty().any {
                    it.name == EXTENSION_FEATURE || it.name == ANIME_EXTENSION_FEATURE
                }
            }

    private fun PackageInfo.isAnimeExtension(): Boolean =
        reqFeatures.orEmpty().any { it.name == ANIME_EXTENSION_FEATURE }

    fun List<PackageInfo>.fingerprint(): String =
        map { "${it.packageName}|${it.versionName}|${it.lastUpdateTime}" }
            .sorted()
            .joinToString(";")

    fun loadOne(
        context: Context,
        pm: PackageManager,
        pkg: PackageInfo,
    ): ExtensionLoader.LoadResult {
        val pkgName = pkg.packageName
        val label = pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkgName
        val anime = pkg.isAnimeExtension()

        return try {
            val appInfo: ApplicationInfo =
                pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
            val metaData = appInfo.metaData
                ?: return ExtensionLoader.LoadResult(
                    pkgName,
                    label,
                    emptyList(),
                    IllegalStateException("No application meta-data"),
                )

            val versionName = pkg.versionName.orEmpty()
            val libVersion = if (anime) {
                metaData.getInt(ANIYOMIX_EXTENSION_LIB, 0)
                    .takeIf { it != 0 }
                    ?.toDouble()
                    ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            } else {
                versionName.substringBeforeLast('.').toDoubleOrNull()
            } ?: return ExtensionLoader.LoadResult(
                pkgName,
                label,
                emptyList(),
                IllegalStateException("Unparseable versionName: $versionName"),
            )

            val supported = if (anime) {
                libVersion in setOf(14.0, 16.0, 17.0)
            } else {
                libVersion in LIB_VERSION_MIN..LIB_VERSION_MAX
            }
            if (!supported) {
                return ExtensionLoader.LoadResult(
                    pkgName,
                    label,
                    emptyList(),
                    IllegalStateException(
                        if (anime) {
                            "Anime extension lib $libVersion is unsupported (expected 14, 16 or 17)"
                        } else {
                            "Lib version $libVersion outside supported range"
                        },
                    ),
                )
            }

            val isNsfw = if (anime) {
                metaData.getInt(ANIYOMIX_CONTENT_WARNING, 0) > 0 ||
                    metaData.getInt(ANIME_METADATA_NSFW, 0) == 1
            } else {
                metaData.getInt(METADATA_NSFW, 0) == 1
            }

            val loader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
            val metadataKey =
                if (anime) ANIME_METADATA_SOURCE_CLASS else METADATA_SOURCE_CLASS
            val declared = metaData.getString(metadataKey).orEmpty()

            if (declared.isBlank()) {
                return ExtensionLoader.LoadResult(
                    pkgName,
                    label,
                    emptyList(),
                    IllegalStateException("Missing $metadataKey"),
                    isNsfw,
                )
            }

            val sources = buildList<Any> {
                declared.splitClassNames(pkgName).forEach { fqcn ->
                    val obj = loader.loadClass(fqcn).getDeclaredConstructor().newInstance()
                    if (obj.isSourceFactory()) {
                        val created = obj.javaClass
                            .getMethod("createSources")
                            .invoke(obj) as List<*>
                        addAll(created.filterNotNull())
                    } else {
                        add(obj)
                    }
                }
            }

            Log.d(
                TAG,
                "$pkgName -> ${sources.size} ${if (anime) "anime" else "manga"} sources (nsfw=$isNsfw)",
            )
            ExtensionLoader.LoadResult(pkgName, label, sources, isNsfw = isNsfw)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load $pkgName", t)
            ExtensionLoader.LoadResult(pkgName, label, emptyList(), t)
        }
    }

    private fun Any.isSourceFactory(): Boolean {
        var c: Class<*>? = javaClass
        while (c != null) {
            if (c.interfaces.any {
                    it.name == "eu.kanade.tachiyomi.source.SourceFactory" ||
                        it.name == "eu.kanade.tachiyomi.animesource.AnimeSourceFactory"
                }
            ) {
                return true
            }
            c = c.superclass
        }
        return false
    }

    private fun String.splitClassNames(pkgName: String): List<String> =
        split(';', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkgName + it else it }
}
