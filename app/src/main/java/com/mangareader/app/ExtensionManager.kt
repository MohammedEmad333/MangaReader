package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Extension(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val apkUrl: String,
    val isInstalled: Boolean = false
)

object ExtensionManager {
    // The action that extension APKs must broadcast in their manifest
    private const val EXTENSION_ACTION = "com.mangareader.app.EXTENSION"

    /**
     * 1. FETCH: Reads the JSON lists from your saved repository URLs.
     * Expected JSON format: [{"name": "MangaSource", "pkg": "com.ext.source", "version": "1.0", "apk": "https://..."}]
     */
    suspend fun fetchAvailable(context: Context): List<Extension> = withContext(Dispatchers.IO) {
        val repos = ExtensionRepos.list(context)
        val available = mutableListOf<Extension>()
        val pm = context.packageManager

        for (repoUrl in repos) {
            try {
                val conn = URL(repoUrl).openConnection() as HttpURLConnection
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(jsonStr)
                
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pkg = obj.getString("pkg")
                    
                    val installed = try {
                        pm.getPackageInfo(pkg, 0)
                        true
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }

                    available.add(
                        Extension(
                            name = obj.getString("name"),
                            pkgName = pkg,
                            versionName = obj.getString("version"),
                            apkUrl = obj.getString("apk"),
                            isInstalled = installed
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace() // Skips broken or offline repos safely
            }
        }
        available
    }

    /**
     * 2. INSTALL: Downloads the APK to the cache and triggers the Android installer.
     */
    suspend fun install(context: Context, ext: Extension) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Download the APK file from the extension's download URL
                val url = java.net.URL(ext.downloadUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connect()

                val apkFile = File(context.cacheDir, "${ext.pkgName}.apk")
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. Trigger the system installation intent on the Main thread
                withContext(Dispatchers.Main) {
                    val apkUri = androidx.core.content.FileProvider.getUriForFile(
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
                e.printStackTrace()
            }
        }

    /**
     * 3. RUN: Finds installed extensions and loads their Source classes dynamically.
     */
    fun loadInstalledSources(context: Context): List<Source> {
        val pm = context.packageManager
        val intent = Intent(EXTENSION_ACTION)
        
        // Find all installed apps that declare our extension action
        val resolved = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val loadedSources = mutableListOf<Source>()
        
        for (info in resolved) {
            try {
                val pkg = info.activityInfo.packageName
                val appInfo = pm.getApplicationInfo(pkg, 0)
                
                // Read the target class name from the extension's manifest meta-data
                val className = info.activityInfo.metaData?.getString("source_class")
                
                if (className != null) {
                    // Use PathClassLoader to load external code safely
                    val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                    val clazz = Class.forName(className, false, classLoader)
                    
                    // Instantiate the external class as a local Source object
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
