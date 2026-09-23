package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader

internal object ExtensionSourceLoader {
    private const val EXTENSION_ACTION = "com.mangareader.app.EXTENSION"

    fun load(context: Context): List<Source> {
        val pm = context.packageManager
        val intent = Intent(EXTENSION_ACTION)
        val resolved = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val loadedSources = mutableListOf<Source>()

        for (info in resolved) {
            try {
                val pkg = info.activityInfo.packageName
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val className = info.activityInfo.metaData?.getString("source_class")
                    ?: continue

                val classLoader = PathClassLoader(
                    appInfo.sourceDir,
                    null,
                    context.classLoader,
                )
                val clazz = Class.forName(className, false, classLoader)
                loadedSources += clazz.getDeclaredConstructor().newInstance() as Source
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return loadedSources
    }
}
