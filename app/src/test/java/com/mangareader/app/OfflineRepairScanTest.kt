package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRepairScanTest {
    private fun record(id: String, seriesId: String = "series") =
        DownloadIndexRecord(id, id, "source", seriesId, "Sample", "")

    @Test fun scansAndQueuesOnlySafePartialChapters() {
        val base = Files.createTempDirectory("repair-audit").toFile()
        try {
            val complete = File(base, "complete").apply { mkdirs() }
            File(complete, ".complete").writeText("2")
            File(complete, "0000").writeBytes(byteArrayOf(1, 2))
            File(complete, "0001").writeBytes(byteArrayOf(3, 4))

            val repairable = File(base, "repairable").apply { mkdirs() }
            File(repairable, ".complete").writeText("3")
            File(repairable, "0000").writeBytes(byteArrayOf(1, 2))
            File(repairable, "0001").writeText("<html>blocked</html>")

            val ambiguous = File(base, "ambiguous").apply { mkdirs() }
            File(ambiguous, ".complete").writeText("2")
            File(ambiguous, "0000").writeBytes(byteArrayOf(1))
            File(ambiguous, "0000.jpg").writeBytes(byteArrayOf(1))

            val unknown = File(base, "unknown").apply { mkdirs() }
            File(unknown, "0000").writeBytes(byteArrayOf(1))

            val records = listOf(
                record("complete"), record("repairable"), record("repairable"),
                record("ambiguous"), record("unknown"), record("missing"),
            )
            val audit = scanIndexedOfflineRepairs(records) { File(base, it) }
            assertEquals(5, audit.summary.totalChapters)
            assertEquals(1, audit.summary.completeChapters)
            assertEquals(1, audit.summary.repairableChapters)
            assertEquals(3, audit.summary.blockedChapters)
            assertEquals(2, audit.summary.pagesToRedownload)
            assertEquals(listOf("repairable"), audit.repairItems.map { it.chapterId })
            assertTrue(File(repairable, "0000").isFile)
            assertTrue(File(repairable, "0001").isFile)
            assertFalse(File(repairable, "0002").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun sourceOrSeriesIdentityMissingDoesNotCreateRepairJob() {
        val base = Files.createTempDirectory("repair-identity").toFile()
        try {
            val broken = File(base, "broken").apply { mkdirs() }
            File(broken, ".complete").writeText("2")
            File(broken, "0000").writeBytes(byteArrayOf(1))
            val records = listOf(
                record("broken", seriesId = ""),
                record("no-source").copy(sourceId = ""),
                record(""),
            )
            val audit = scanIndexedOfflineRepairs(records) { File(base, it) }
            assertTrue(audit.repairItems.isEmpty())
            assertEquals(1, audit.summary.blockedChapters)
            assertEquals(0, audit.summary.pagesToRedownload)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun aliasPathsOnlyCountOnceAndLeaveFilesUnchanged() {
        val base = Files.createTempDirectory("repair-alias").toFile()
        try {
            val chapter = File(base, "chapter").apply { mkdirs() }
            File(chapter, ".complete").writeText("2")
            File(chapter, "0000").writeBytes(byteArrayOf(1))
            val audit = scanIndexedOfflineRepairs(listOf(record("a"), record("b"))) {
                if (it == "a") chapter else File(base, "chapter/../chapter")
            }
            assertEquals(1, audit.summary.totalChapters)
            assertEquals(1, audit.summary.repairableChapters)
            assertEquals(1, audit.repairItems.size)
            assertEquals(1, audit.summary.pagesToRedownload)
        } finally {
            base.deleteRecursively()
        }
    }
}
