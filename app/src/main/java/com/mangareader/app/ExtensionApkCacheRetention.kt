package com.mangareader.app

import java.io.File

/**
 * Android's package installer can read a shared APK after our activity returns.
 * Keep recent files intact and remove only old installer-owned cache entries.
 */
internal object ExtensionApkCacheRetention {
    const val MAX_AGE_MS = 48L * 60L * 60L * 1000L

    private val generatedApkName = Regex("""extension-[A-Za-z0-9]+\.apk""")

    fun cleanup(directory: File, nowMillis: Long = System.currentTimeMillis()): Int {
        if (!directory.isDirectory) return 0
        var removed = 0
        for (file in directory.listFiles().orEmpty()) {
            if (!file.isFile || !generatedApkName.matches(file.name)) continue
            val modifiedAt = file.lastModified()
            // Unknown, future and recently modified timestamps must not be deleted.
            if (modifiedAt <= 0L || nowMillis < modifiedAt ||
                nowMillis - modifiedAt < MAX_AGE_MS
            ) continue
            if (file.delete()) removed++
        }
        return removed
    }
}
