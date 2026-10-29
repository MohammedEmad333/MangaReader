package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineRepairBatchSummaryTest {
    @Test fun groupsCompleteRepairableAndBlockedChapters() {
        val base = Files.createTempDirectory("offline-repair-batch-").toFile()
        try {
            val complete = File(base, "complete").apply { mkdirs() }
            File(complete, ".complete").writeText("1")
            File(complete, "0000").writeBytes(byteArrayOf(1, 2))
            val repairable = File(base, "repairable").apply { mkdirs() }
            File(repairable, ".complete").writeText("3")
            File(repairable, "0000").writeBytes(byteArrayOf(1, 2))
            val ambiguous = File(base, "ambiguous").apply { mkdirs() }
            File(ambiguous, ".complete").writeText("2")
            File(ambiguous, "0000.jpg").writeBytes(byteArrayOf(1))
            val summary = summarizeOfflineRepairs(
                listOf(complete, repairable, ambiguous, repairable)
            )
            assertEquals(3, summary.totalChapters)
            assertEquals(1, summary.completeChapters)
            assertEquals(1, summary.repairableChapters)
            assertEquals(1, summary.blockedChapters)
            assertEquals(2, summary.pagesToRedownload)
        } finally { base.deleteRecursively() }
    }

    @Test fun unknownPageCountIsBlockedWithoutDownloading() {
        val base = Files.createTempDirectory("offline-repair-unknown-").toFile()
        try {
            val chapter = File(base, "chapter").apply { mkdirs() }
            File(chapter, "0000").writeBytes(byteArrayOf(1))
            val summary = summarizeOfflineRepairs(listOf(chapter))
            assertEquals(1, summary.blockedChapters)
            assertEquals(0, summary.pagesToRedownload)
        } finally { base.deleteRecursively() }
    }
}
