package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionBatchUpdatesTest {
    private fun extension(pkg: String, installed: String?, offered: String) = Extension(
        name = pkg,
        pkgName = pkg,
        versionName = offered,
        apkUrl = "https://example.org/$pkg.apk",
        isInstalled = installed != null,
        installedVersion = installed,
    )

    @Test fun onlyInstalledOutdatedPackagesAreQueued() {
        val old = extension("com.example.old", "1.0", "1.1")
        val current = extension("com.example.current", "2.0", "2.0")
        val new = extension("com.example.new", null, "1.0")
        val queue = extensionsEligibleForBulkUpdate(listOf(old, current, new, old))
        assertEquals(listOf("com.example.old"), queue.map { it.pkgName })
    }

    @Test fun completionCountsSuccessAndFailuresIndependently() {
        var progress = ExtensionBatchProgress(3)
        progress = progress.start("One", ExtensionBatchPhase.DOWNLOADING)
        assertEquals(0, progress.attempted)
        assertEquals("One", progress.currentName)
        progress = progress.start("One", ExtensionBatchPhase.INSTALLING)
        assertEquals(ExtensionBatchPhase.INSTALLING, progress.phase)
        progress = progress.record("One", true)
        progress = progress.record("Two", false, "Cancelled by user")
        assertEquals(2, progress.attempted)
        assertEquals(1, progress.updated)
        assertEquals(1, progress.failures.size)
        assertEquals(1, progress.remaining)
        assertFalse(progress.finished)
        progress = progress.record("Three", true).finish()
        assertEquals(3, progress.attempted)
        assertEquals(2, progress.updated)
        assertTrue(progress.finished)
        assertEquals(0, progress.skipped)
    }

    @Test fun cancelOnlySkipsNotYetStartedItems() {
        val progress = ExtensionBatchProgress(4)
            .record("One", true)
            .cancelRemaining()
            .finish()
        assertEquals(1, progress.updated)
        assertEquals(3, progress.skipped)
        assertTrue(progress.cancelRequested)
    }
}
