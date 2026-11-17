package com.mangareader.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBackupHealthTest {
    private fun payload(created: Long = 12345L): String =
        """{"version":1,"app":"0.322","createdAt":$created,"stores":{"manga_reader":{"test":{"t":"s","v":"safe"}}}}"""

    @Test fun noFolderOrBackupsDoesNotInventSuccess() {
        val parent = Files.createTempDirectory("yomu-health-empty").toFile()
        try {
            val missing = inspectAutomaticBackupHealth(parent.resolve("missing"))
            assertNull(missing.verifiedAt)
            assertEquals(0, missing.checkedArchives)
            assertFalse(missing.folderUnreadable)
            val file = parent.resolve("not-directory").apply { writeText("not a folder") }
            assertTrue(inspectAutomaticBackupHealth(file).folderUnreadable)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test fun detectsCorruptNewestBackupAndPreservesOldValidBackup() {
        val folder = Files.createTempDirectory("yomu-health-previous").toFile()
        try {
            folder.resolve("yomu_2026-01-01_120000.json").writeText(payload(11000))
            folder.resolve("yomu_2026-01-02_120000.json").writeText("truncated")
            folder.resolve(".yomu_interrupted.part").writeText("unrelated")
            val health = inspectAutomaticBackupHealth(folder)
            assertEquals(11000L, health.verifiedAt)
            assertEquals(2, health.checkedArchives)
            assertEquals(1, health.invalidArchives)
            assertTrue(health.newestArchiveInvalid)
            assertFalse(health.moreArchivesToCheck)
            assertEquals("truncated", folder.resolve("yomu_2026-01-02_120000.json").readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun boundedScanDoesNotPretendOlderBackupsWereChecked() {
        val folder = Files.createTempDirectory("yomu-health-bounded").toFile()
        try {
            repeat(4) { i ->
                folder.resolve("yomu_2026-01-0${i + 1}_1200.json").writeText(
                    if (i == 3) "bad json" else payload(1000L + i)
                )
            }
            val health = inspectAutomaticBackupHealth(folder, maxArchives = 1)
            assertNull(health.verifiedAt)
            assertEquals(1, health.checkedArchives)
            assertEquals(1, health.invalidArchives)
            assertTrue(health.moreArchivesToCheck)
            assertTrue(health.newestArchiveInvalid)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun invalidAndUnrelatedFilesAreIgnoredWithoutDeletingAnything() {
        val folder = Files.createTempDirectory("yomu-health-unrelated").toFile()
        try {
            folder.resolve("other.json").writeText("private")
            folder.resolve("yomu_part.part").writeText("invalid")
            folder.resolve("yomu_2026-01-01_1200.json").writeText(payload())
            val scan = inspectAutomaticBackupHealth(folder)
            assertEquals(1, scan.checkedArchives)
            assertEquals(0, scan.invalidArchives)
            assertEquals(12345L, scan.verifiedAt)
            assertEquals("private", folder.resolve("other.json").readText())
            assertEquals("invalid", folder.resolve("yomu_part.part").readText())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun diagnosticReasonsAreFixedCategoriesNotRawExceptionMessages() {
        assertNull(AutomaticBackupFailure.from("https://host/private?token=abc"))
        assertEquals(AutomaticBackupFailure.WRITE, AutomaticBackupFailure.from("write"))
        assertEquals(AutomaticBackupFailure.STORAGE, AutomaticBackupFailure.from("storage"))
        assertEquals(AutomaticBackupFailure.DATA, AutomaticBackupFailure.from("data"))
    }
}
