package com.mangareader.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExtensionApkDownloadPolicyTest {
    @Test fun rejectsInvalidPackageNames() {
        ExtensionApkDownloadPolicy.requirePackageName("org.example.reader")
        for (name in listOf("", "../bad", "org/example", "org.example..reader", "bad.apk", "com.example-reader")) {
            if (name == "bad.apk") continue // Syntactically a valid Java-style package name.
            assertThrows(IOException::class.java) {
                ExtensionApkDownloadPolicy.requirePackageName(name)
            }
        }
    }

    @Test fun acceptsOnlyHttpsAndRejectsCredentials() {
        assertEquals("https", ExtensionApkDownloadPolicy.secureUrl("https://example.org/app.apk").protocol)
        for (url in listOf("http://example.org/a.apk", "file:///tmp/a.apk", "https://user:pass@example.org/a.apk")) {
            assertThrows(IOException::class.java) {
                ExtensionApkDownloadPolicy.secureUrl(url)
            }
        }
    }

    @Test fun resolvesRelativeRedirectAndRejectsDowngradeOrLoops() {
        val origin = ExtensionApkDownloadPolicy.secureUrl("https://example.org/extensions/index.apk")
        assertEquals("https://example.org/apk/app.apk",
            ExtensionApkDownloadPolicy.redirectUrl(origin, "/apk/app.apk", 0).toString())
        assertThrows(IOException::class.java) {
            ExtensionApkDownloadPolicy.redirectUrl(origin, "http://example.org/app.apk", 0)
        }
        assertThrows(IOException::class.java) {
            ExtensionApkDownloadPolicy.redirectUrl(origin, "/app.apk", ExtensionApkDownloadPolicy.MAX_REDIRECTS)
        }
        assertThrows(IOException::class.java) {
            ExtensionApkDownloadPolicy.redirectUrl(origin, null, 0)
        }
    }

    @Test fun enforcesDeclaredAndStreamingSizes() {
        ExtensionApkDownloadPolicy.validateContentLength(-1)
        ExtensionApkDownloadPolicy.validateContentLength(1)
        assertThrows(IOException::class.java) { ExtensionApkDownloadPolicy.validateContentLength(0) }
        assertThrows(IOException::class.java) {
            ExtensionApkDownloadPolicy.validateContentLength(ExtensionApkDownloadPolicy.MAX_APK_BYTES + 1)
        }
        val out = ByteArrayOutputStream()
        assertEquals(4L, ExtensionApkDownloadPolicy.copyWithLimit(
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), out, 4))
        assertEquals(4, out.size())
        assertThrows(IOException::class.java) {
            ExtensionApkDownloadPolicy.copyWithLimit(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), 4)
        }
        assertThrows(IOException::class.java) {
            ExtensionApkDownloadPolicy.copyWithLimit(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        }
    }

    @Test fun checksArchivePrefix() {
        val archive = File.createTempFile("extension-policy-", ".apk")
        try {
            archive.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01))
            ExtensionApkDownloadPolicy.validateZipHeader(archive)
            archive.writeText("not an apk")
            assertThrows(IOException::class.java) { ExtensionApkDownloadPolicy.validateZipHeader(archive) }
        } finally {
            archive.delete()
        }
    }
}
