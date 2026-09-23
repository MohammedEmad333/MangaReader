package com.mangareader.app

import android.content.pm.PackageInfo
import android.content.pm.PackageManager

internal object ExtensionDiagnostics {
    fun build(
        pm: PackageManager,
        candidates: List<PackageInfo>,
        results: List<ExtensionLoader.LoadResult>,
        supportedMin: Double,
        supportedMax: Double,
        metadataSourceClass: String,
        cacheSourceCount: Int?,
    ): String {
        val out = StringBuilder()

        out.appendLine("Packages declaring tachiyomi.extension: ${candidates.size}")
        out.appendLine("Supported lib versions: $supportedMin - $supportedMax")
        out.appendLine(
            "Cache: " +
                (cacheSourceCount?.let { "$it sources held" } ?: "empty"),
        )
        out.appendLine()

        val ok = results.count { it.error == null }
        out.appendLine("Loaded OK: $ok / ${results.size}")
        out.appendLine("Total sources: ${results.sumOf { it.sources.size }}")
        out.appendLine()

        for (result in results.take(5)) {
            out.appendLine("• ${result.pkgName}")
            out.appendLine("  label: ${result.label}")

            val metadata = runCatching {
                pm.getApplicationInfo(
                    result.pkgName,
                    PackageManager.GET_META_DATA,
                ).metaData
            }.getOrNull()

            out.appendLine(
                "  class:   " +
                    (metadata?.getString(metadataSourceClass) ?: "-"),
            )
            out.appendLine(
                "  (factory status is only knowable after instantiation)",
            )
            out.appendLine(
                "  version: " +
                    runCatching {
                        pm.getPackageInfo(result.pkgName, 0).versionName
                    }.getOrNull(),
            )

            if (result.error == null) {
                out.appendLine("  ✓ ${result.sources.size} source(s)")
            } else {
                out.appendLine("  ✗ ${rootCauseChain(result.error)}")
            }
            out.appendLine()
        }

        if (results.size > 5) {
            out.appendLine("(${results.size - 5} more not shown)")
        }

        return out.toString()
    }

    private fun rootCauseChain(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0

        while (current != null && depth < 6) {
            parts += "${current.javaClass.simpleName}: ${current.message?.take(120)}"
            current = current.cause
            depth++
        }

        return parts.joinToString("\n     caused by ")
    }
}
