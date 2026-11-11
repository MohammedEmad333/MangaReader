package com.mangareader.app

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

/**
 * Validation for untrusted extension repository metadata and APK download streams.
 * Kept Android-free so redirect and size boundary cases are covered by JVM tests.
 */
internal object ExtensionApkDownloadPolicy {
    const val MAX_REDIRECTS = 5
    const val MAX_APK_BYTES = 128L * 1024L * 1024L

    private val packageNamePattern =
        Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")

    fun requirePackageName(packageName: String) {
        if (!packageNamePattern.matches(packageName)) {
            throw IOException("Invalid extension package name")
        }
    }

    fun secureUrl(value: String): URL = requireHttps(URL(value))

    fun redirectUrl(current: URL, location: String?, redirectsFollowed: Int): URL {
        if (redirectsFollowed >= MAX_REDIRECTS) {
            throw IOException("Too many extension APK redirects")
        }
        if (location.isNullOrBlank()) {
            throw IOException("Extension APK redirect has no Location header")
        }
        return requireHttps(URL(current, location))
    }

    private fun requireHttps(url: URL): URL {
        if (!url.protocol.equals("https", ignoreCase = true) ||
            url.host.isBlank() ||
            url.userInfo != null
        ) {
            throw IOException("Extension APK downloads require an HTTPS URL")
        }
        return url
    }

    fun validateContentLength(length: Long) {
        // -1 means unknown: the streaming limit still applies.
        if (length == 0L || length < -1L || length > MAX_APK_BYTES) {
            throw IOException("Extension APK has an invalid or excessive Content-Length")
        }
    }

    fun copyWithLimit(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long = MAX_APK_BYTES,
    ): Long {
        require(maxBytes > 0L)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) continue
            if (count.toLong() > maxBytes - total) {
                throw IOException("Extension APK exceeds the download size limit")
            }
            output.write(buffer, 0, count)
            total += count
        }
        if (total == 0L) throw IOException("Extension APK download is empty")
        return total
    }

    fun validateZipHeader(apk: File) {
        val zipHeader = intArrayOf(0x50, 0x4B, 0x03, 0x04)
        FileInputStream(apk).use { stream ->
            if (zipHeader.any { stream.read() != it }) {
                throw IOException("Downloaded file is not an APK/ZIP archive")
            }
        }
    }
}
