package com.mangareader.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal object ExtensionInstaller {
    suspend fun install(context: Context, ext: Extension) {
        withContext(Dispatchers.IO) {
            try {
                val connection = openDownloadConnection(ext.apkUrl)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val apkFile = File(context.cacheDir, "${ext.pkgName}.apk")
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile,
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            apkUri,
                            "application/vnd.android.package-archive",
                        )
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
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun openDownloadConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl

        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            )
            connection.instanceFollowRedirects = false
            connection.connect()

            val redirected =
                connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    connection.responseCode == HttpURLConnection.HTTP_SEE_OTHER

            if (!redirected) return connection

            val next = connection.getHeaderField("Location")
            if (next == null) return connection

            connection.disconnect()
            currentUrl = next
        }
    }
}
