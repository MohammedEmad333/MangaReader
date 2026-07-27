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
    val isInstalled: Boolean = false,
    /** Display label, already mapped from the index's language code. */
    val lang: String = "",
    /** From the index's "nsfw" field; drives the 18+ badge. */
    val isNsfw: Boolean = false
)

object ExtensionManager {
    // The action that extension APKs must broadcast in their manifest
    private const val EXTENSION_ACTION = "com.mangareader.app.EXTENSION"

    /**
     * 1. FETCH: Reads the JSON lists from your saved repository URLs.
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

                    // FIXED: Prepend "apk/" so it points to the correct subdirectory on GitHub
                    val rawApkUrl = obj.getString("apk")
                    val relativePath = if (rawApkUrl.startsWith("http")) rawApkUrl else "apk/$rawApkUrl"
                    val absoluteApkUrl = URL(URL(repoUrl), relativePath).toString()

                    available.add(
                        Extension(
                            // Index entries are named "Tachiyomi: Foo"; the prefix
                            // is noise on every single row.
                            name = obj.getString("name")
                                .removePrefix("Tachiyomi: ")
                                .removePrefix("Mihon: "),
                            pkgName = pkg,
                            versionName = obj.getString("version"),
                            apkUrl = absoluteApkUrl,
                            isInstalled = installed,
                            lang = langLabel(obj.optString("lang", "")),
                            isNsfw = obj.optInt("nsfw", 0) == 1
                        )
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace() 
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
