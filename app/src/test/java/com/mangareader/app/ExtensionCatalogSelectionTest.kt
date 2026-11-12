package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCatalogSelectionTest {
    private fun extension(
        pkg: String,
        version: String,
        name: String = pkg,
        isNsfw: Boolean = false,
        installedVersion: String? = null,
    ) = Extension(
        name = name,
        pkgName = pkg,
        versionName = version,
        apkUrl = "https://example.org/$pkg-$version.apk",
        isInstalled = installedVersion != null,
        installedVersion = installedVersion,
        isNsfw = isNsfw,
    )

    @Test fun prefersNewestVersionEvenWhenListedAfterOldVersion() {
        val old = extension("com.example.reader", "1.9", installedVersion = "1.9")
        val newer = extension("com.example.reader", "1.10", installedVersion = "1.9")
        val selected = selectLatestExtensions(listOf(old, newer))
        assertEquals(1, selected.size)
        assertEquals("1.10", selected.single().versionName)
        assertTrue(selected.single().hasUpdate)
        assertEquals(newer.apkUrl, selected.single().apkUrl)
    }

    @Test fun keepsFirstRepositoryChoiceForEqualAndOlderVersions() {
        val first = extension("com.example.reader", "2.0", name = "first")
        val same = extension("com.example.reader", "2.0", name = "second")
        val older = extension("com.example.reader", "1.8", name = "older")
        assertEquals("first", selectLatestExtensions(listOf(first, same, older)).single().name)
    }

    @Test fun retainsFirstOccurrenceOrderWhenReplacingVersion() {
        val first = extension("com.example.first", "1.0")
        val second = extension("com.example.second", "1.0")
        val upgraded = extension("com.example.first", "2.0")
        val selected = selectLatestExtensions(listOf(first, second, upgraded))
        assertEquals(listOf("com.example.first", "com.example.second"), selected.map { it.pkgName })
        assertEquals("2.0", selected[0].versionName)
        assertEquals("1.0", selected[1].versionName)
    }

    @Test fun retainsAdultFlagFromEitherDuplicateRepository() {
        val adultOld = extension("com.example.reader", "1.0", isNsfw = true)
        val newer = extension("com.example.reader", "2.0", isNsfw = false)
        val selected = selectLatestExtensions(listOf(adultOld, newer)).single()
        assertTrue(selected.isNsfw)
        assertEquals("2.0", selected.versionName)
        val reverse = selectLatestExtensions(listOf(newer, adultOld)).single()
        assertTrue(reverse.isNsfw)
        assertFalse(selectLatestExtensions(listOf(newer)).single().isNsfw)
    }

    @Test fun emptyInputProducesEmptyOutput() {
        assertTrue(selectLatestExtensions(emptyList()).isEmpty())
    }
}
