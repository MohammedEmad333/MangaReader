package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionApkCacheRetentionTest {
    @Test fun removesOnlyOldGeneratedApks() {
        val dir = Files.createTempDirectory("extension-cleanup-").toFile()
        try {
            val now = 2_000_000_000_000L
            val stale = File(dir, "extension-123456789.apk").apply {
                writeText("old")
                assertTrue(setLastModified(now - ExtensionApkCacheRetention.MAX_AGE_MS - 1))
            }
            val recent = File(dir, "extension-987654321.apk").apply {
                writeText("recent")
                assertTrue(setLastModified(now - 10_000L))
            }
            val unrelated = File(dir, "some-other.apk").apply {
                writeText("unrelated")
                assertTrue(setLastModified(now - ExtensionApkCacheRetention.MAX_AGE_MS - 1))
            }
            val future = File(dir, "extension-777777.apk").apply {
                writeText("future")
                assertTrue(setLastModified(now + 60_000L))
            }
            val directory = File(dir, "extension-999999.apk").apply {
                assertTrue(mkdir())
                assertTrue(setLastModified(now - ExtensionApkCacheRetention.MAX_AGE_MS - 1))
            }

            assertEquals(1, ExtensionApkCacheRetention.cleanup(dir, now))
            assertFalse(stale.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
            assertTrue(future.exists())
            assertTrue(directory.isDirectory)
            assertEquals(0, ExtensionApkCacheRetention.cleanup(dir, now))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun keepsFilesBeforeRetentionThresholdAndMissingDirectories() {
        val dir = Files.createTempDirectory("extension-cleanup-boundary-").toFile()
        try {
            val now = 2_000_000_000_000L
            val file = File(dir, "extension-123456.apk").apply {
                writeText("boundary")
                assertTrue(setLastModified(now - ExtensionApkCacheRetention.MAX_AGE_MS + 1L))
            }
            assertEquals(0, ExtensionApkCacheRetention.cleanup(dir, now))
            assertTrue(file.exists())
            assertEquals(0, ExtensionApkCacheRetention.cleanup(File(dir, "missing"), now))
        } finally {
            dir.deleteRecursively()
        }
    }
}
