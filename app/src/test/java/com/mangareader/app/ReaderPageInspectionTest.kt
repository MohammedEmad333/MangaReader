package com.mangareader.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageInspectionTest {
    @Test fun avoidsDecodingBoundsForNormalModeAndInvalidFiles() {
        val dir = java.nio.file.Files.createTempDirectory("reader-inspection").toFile()
        try {
            val valid = File(dir, "page.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val invalid = File(dir, "missing")
            var checks = 0
            val bounds: (File) -> Pair<Int, Int> = {
                checks++
                2000 to 1000
            }
            val normal = readerPageInspection(valid, dir, ReaderWidePageMode.FIT, bounds)
            assertTrue(normal.usable)
            assertFalse(normal.rotateWide)
            assertEquals(0, checks)
            val broken = readerPageInspection(invalid, dir, ReaderWidePageMode.ROTATE_RIGHT, bounds)
            assertFalse(broken.usable)
            assertFalse(broken.rotateWide)
            assertEquals(0, checks)
            val rotated = readerPageInspection(valid, dir, ReaderWidePageMode.ROTATE_RIGHT, bounds)
            assertTrue(rotated.rotateWide)
            assertEquals(1, checks)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun onlyCurrentPageAndModeCanUseAsyncInspection() {
        val first = File("/tmp/reader-a")
        val second = File("/tmp/reader-b")
        val cached = ReaderPageInspection(
            file = first,
            wideMode = ReaderWidePageMode.FIT,
            revision = requireNotNull(readerImageFileRevision(first)),
            usable = true,
            rotateWide = false,
            canRedownload = false,
        )
        assertEquals(cached, currentReaderPageInspection(cached, first, ReaderWidePageMode.FIT))
        assertEquals(null, currentReaderPageInspection(cached, second, ReaderWidePageMode.FIT))
        assertEquals(null, currentReaderPageInspection(cached, first, ReaderWidePageMode.ROTATE_RIGHT))
        assertEquals(null, currentReaderPageInspection(cached, null, ReaderWidePageMode.FIT))
        assertEquals(null, currentReaderPageInspection(null, first, ReaderWidePageMode.FIT))
    }

    @Test fun samePathReplacementInvalidatesPriorInspection() {
        val page = File.createTempFile("reader-revision", ".img")
        try {
            page.writeBytes(byteArrayOf(1, 2, 3))
            val original = requireNotNull(readerImageFileRevision(page))
            val checked = readerPageInspection(
                page, page.parentFile!!, ReaderWidePageMode.FIT,
                bounds = { throw AssertionError("No bounds in FIT mode") },
                revision = original,
            )
            page.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
            val replaced = requireNotNull(readerImageFileRevision(page))
            assertEquals(page, replaced.file)
            assertFalse(original == replaced)
            assertEquals(null, currentReaderPageInspection(checked, page, ReaderWidePageMode.FIT, replaced))
            assertEquals(replaced, readerPageInspection(
                page, page.parentFile!!, ReaderWidePageMode.FIT,
                bounds = { throw AssertionError("No bounds in FIT mode") },
                revision = replaced,
            ).revision)
        } finally {
            page.delete()
        }
    }

    @Test fun onlyReaderCacheFilesAreEligibleForRedownload() {
        val root = java.nio.file.Files.createTempDirectory("reader-page-root").toFile()
        try {
            val cached = File(root, "pages/chapter/0001").apply {
                parentFile!!.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val downloaded = File(root, "offline/0001").apply {
                parentFile!!.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val ignore: (File) -> Pair<Int, Int> = { throw AssertionError("bounds not needed") }
            assertTrue(readerPageInspection(
                cached, root, ReaderWidePageMode.FIT, ignore
            ).canRedownload)
            assertFalse(readerPageInspection(
                downloaded, root, ReaderWidePageMode.FIT, ignore
            ).canRedownload)
        } finally {
            root.deleteRecursively()
        }
    }
}
