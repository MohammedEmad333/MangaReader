package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBackupFileWriterTest {
    private fun payload(value: String = "safe"): ByteArray =
        """{"version":1,"app":"0.321","createdAt":123,"stores":{"manga_reader":{"marker":{"t":"s","v":"$value"}}}}"""
            .toByteArray(Charsets.UTF_8)

    private fun withFolder(block: (File) -> Unit) {
        val folder = Files.createTempDirectory("yomu-auto-backup").toFile()
        try {
            block(folder)
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun archives(folder: File) =
        folder.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }

    @Test fun verifiedWriteCanBeReadByTheRestoreValidator() = withFolder { folder ->
        val data = payload()
        assertTrue(AutomaticBackupFileWriter.writeVerified(folder, data, 1000L))
        val files = archives(folder)
        assertEquals(1, files.size)
        assertArrayEquals(data, files.single().readBytes())
        assertEquals(
            BackupVerification.validate(data).report.fingerprint,
            BackupVerification.validate(files.single().readBytes()).report.fingerprint,
        )
        assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test fun invalidPayloadNeverChangesOlderBackups() = withFolder { folder ->
        val old = File(folder, "yomu_2026-01-01_1200.json")
        val bytes = payload()
        old.writeBytes(bytes)
        assertFalse(
            AutomaticBackupFileWriter.writeVerified(
                folder, "truncated".toByteArray(), timestamp = 1000L, keep = 1,
            ),
        )
        assertEquals(listOf(old.name), archives(folder).map { it.name })
        assertArrayEquals(bytes, old.readBytes())
    }

    @Test fun interruptedOrCorruptedTemporaryWritePreservesBackups() = withFolder { folder ->
        val old = File(folder, "yomu_2026-01-01_1200.json")
        val bytes = payload()
        old.writeBytes(bytes)
        val saved = AutomaticBackupFileWriter.writeVerified(
            folder = folder,
            bytes = payload("new"),
            timestamp = 1000L,
            keep = 1,
            beforePublish = { temp -> temp.writeText("corrupted") },
        )
        assertFalse(saved)
        assertArrayEquals(bytes, old.readBytes())
        assertEquals(listOf(old.name), archives(folder).map { it.name })
        assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".part") })

        val failed = AutomaticBackupFileWriter.writeVerified(
            folder = folder,
            bytes = payload("new"),
            timestamp = 1000L,
            keep = 1,
            beforePublish = { throw IllegalStateException("Simulated interruption") },
        )
        assertFalse(failed)
        assertEquals(listOf(old.name), archives(folder).map { it.name })
    }

    @Test fun sameTimestampNeverOverwritesAnExistingBackup() = withFolder { folder ->
        val first = payload("one")
        val second = payload("two")
        assertTrue(AutomaticBackupFileWriter.writeVerified(folder, first, 1700000000000L))
        assertTrue(AutomaticBackupFileWriter.writeVerified(folder, second, 1700000000000L))
        val files = archives(folder)
        assertEquals(2, files.size)
        assertTrue(files.map { it.name }.distinct().size == 2)
        val content = files.map { it.readBytes().toList() }
        assertTrue(first.toList() in content)
        assertTrue(second.toList() in content)
    }

    @Test fun pruningHappensOnlyAfterVerifiedSuccess() = withFolder { folder ->
        val old = File(folder, "yomu_2020-01-01_1200.json")
        old.writeBytes(payload("old"))
        val other = File(folder, "other.json")
        other.writeText("unrelated")
        val partial = File(folder, "yomu_previous.part")
        partial.writeText("incomplete")

        assertTrue(
            AutomaticBackupFileWriter.writeVerified(
                folder, payload("new"), timestamp = 1700000000000L, keep = 1,
            ),
        )
        assertEquals(1, archives(folder).count { it.name.startsWith("yomu_") })
        assertFalse(old.exists())
        assertEquals("unrelated", other.readText())
        assertEquals("incomplete", partial.readText())
    }

    @Test fun noWritableFolderOrInvalidRetentionNeverPublishes() = withFolder { folder ->
        assertFalse(AutomaticBackupFileWriter.writeVerified(File(folder, "missing"), payload(), 1L))
        assertFalse(AutomaticBackupFileWriter.writeVerified(folder, payload(), 1L, keep = 0))
        assertTrue(archives(folder).isEmpty())
    }
}
