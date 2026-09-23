package com.mangareader.app

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

internal object ExtensionIndexParser {
    private const val SAFE = "CONTENT_WARNING_SAFE"

    fun parse(
        context: Context,
        json: String,
        repoUrl: String,
        pm: PackageManager,
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

            val names = HashMap<String, String>()
            val nsfw = HashMap<String, Boolean>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                runCatching { nestedEntry(obj, pm) }
                    .getOrNull()
                    ?.let(entries::add)
                runCatching { collectSourceNames(obj, names) }
                runCatching { collectSourceNsfw(obj, nsfw) }
            }

            runCatching { SourceNames.record(context, names) }
            runCatching { SourceNsfw.record(context, nsfw) }
        }

        return entries
    }

    private fun collectSourceNsfw(
        obj: JSONObject,
        into: MutableMap<String, Boolean>,
    ) {
        val srcs = obj.optJSONArray("sources") ?: return
        val flag = obj.optString("contentWarning", SAFE) != SAFE
        for (i in 0 until srcs.length()) {
            val src = srcs.optJSONObject(i) ?: continue
            val id = src.optString("id").takeIf { it.isNotBlank() } ?: continue
            into["tachi:$id"] = flag
        }
    }

    private fun collectSourceNames(
        obj: JSONObject,
        into: MutableMap<String, String>,
    ) {
        val srcs = obj.optJSONArray("sources") ?: return
        for (i in 0 until srcs.length()) {
            val src = srcs.optJSONObject(i) ?: continue
            val id = src.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = src.optString("name").takeIf { it.isNotBlank() } ?: continue
            into["tachi:$id"] = name
        }
    }

    private fun flatEntry(
        obj: JSONObject,
        repoUrl: String,
        pm: PackageManager,
    ): Extension {
        val pkg = obj.getString("pkg")
        val installedInfo = installedInfo(pm, pkg)
        val rawApkUrl = obj.getString("apk")
        val relativePath =
            if (rawApkUrl.startsWith("http")) rawApkUrl else "apk/$rawApkUrl"

        return Extension(
            name = obj.getString("name")
                .removePrefix("Tachiyomi: ")
                .removePrefix("Mihon: ")
                .removePrefix("Aniyomi: "),
            pkgName = pkg,
            versionName = obj.getString("version"),
            apkUrl = URL(URL(repoUrl), relativePath).toString(),
            isInstalled = installedInfo != null,
            lang = langLabel(obj.optString("lang", "")),
            isNsfw = obj.optInt("nsfw", 0) == 1,
            installedVersion = installedInfo?.versionName,
        )
    }

    private fun nestedEntry(
        obj: JSONObject,
        pm: PackageManager,
    ): Extension {
        val pkg = obj.getString("packageName")
        val installedInfo = installedInfo(pm, pkg)
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
                .removePrefix("Mihon: ")
                .removePrefix("Aniyomi: "),
            pkgName = pkg,
            versionName = obj.getString("versionName"),
            apkUrl = obj.getJSONObject("resources").getString("apkUrl"),
            isInstalled = installedInfo != null,
            lang = langLabel(langs.singleOrNull() ?: "all"),
            isNsfw = obj.optString("contentWarning", SAFE) != SAFE,
            installedVersion = installedInfo?.versionName,
        )
    }

    private fun installedInfo(pm: PackageManager, pkg: String) = try {
        pm.getPackageInfo(pkg, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
