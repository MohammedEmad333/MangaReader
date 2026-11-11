package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object ExtensionInstaller {
    suspend fun install(context: Context, ext: Extension): Boolean =
        withContext(Dispatchers.IO) {
            var apkFile: File? = null
            try {
                ExtensionApkDownloadPolicy.requirePackageName(ext.pkgName)
                // Randomized cache file names avoid untrusted path components and collisions.
                val tempDir = File(context.cacheDir, "extension-apks")
                if (!tempDir.isDirectory && !tempDir.mkdirs()) {
                    throw IOException("Cannot create extension download directory")
                }
                apkFile = File.createTempFile("extension-", ".apk", tempDir)
                val destination = apkFile

                val connection = openDownloadConnection(ext.apkUrl)
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("Server returned HTTP ${connection.responseCode}")
                    }
                    ExtensionApkDownloadPolicy.validateContentLength(connection.contentLengthLong)
                    connection.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            ExtensionApkDownloadPolicy.copyWithLimit(input, output)
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                ExtensionApkDownloadPolicy.validateZipHeader(destination)
                @Suppress("DEPRECATION")
                val archiveInfo = context.packageManager.getPackageArchiveInfo(
                    destination.absolutePath,
                    PackageManager.GET_META_DATA,
                ) ?: throw IOException("Downloaded file is not a valid Android APK")

                if (archiveInfo.packageName != ext.pkgName) {
                    throw IOException("Downloaded APK package does not match the requested extension")
                }

                withContext(Dispatchers.Main) {
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        destination,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                true
            } catch (e: Exception) {
                apkFile?.delete()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Install Error: ${e.localizedMessage ?: e.message}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                e.printStackTrace()
                false
            }
        }

    private fun openDownloadConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = ExtensionApkDownloadPolicy.secureUrl(initialUrl)
        var redirectsFollowed = 0

        while (true) {
            val connection = currentUrl.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                )
                connection.instanceFollowRedirects = false
                val status = connection.responseCode
                val redirected = status == 301 || status == 302 || status == 303 ||
                    status == 307 || status == 308
                if (!redirected) return connection

                currentUrl = ExtensionApkDownloadPolicy.redirectUrl(
                    currentUrl,
                    connection.getHeaderField("Location"),
                    redirectsFollowed,
                )
                redirectsFollowed++
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
    }
}
