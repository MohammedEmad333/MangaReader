package com.mangareader.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveAuditTest {
    private fun json(version: String = "0.320"): String =
        """{"version":1,"app":"$version","createdAt":123,"stores":{"manga_reader":{"history":{"t":"s","v":"[]"}}}}"""

    private fun withFolder(block: (File) -> Unit) {
        val folder = Files.createTempDirectory("yomu-archive-audit").toFile()
        try { block(folder) } finally { folder.deleteRecursively() }
    }

    @Test fun checksPublishedBackupsWithoutMutatingAnything() = withFolder { dir ->
        val good = File(dir, "yomu_2026-11-18_150000.json").apply {
            writeText(json())
        }
        val bad = File(dir, "yomu_2026-11-18_140000.json").apply {
            writeText("not a backup: token=private")
        }
        val partial = File(dir, "yomu_2026-11-18_150100.part").apply {
            writeText("partial")
        }
        val other = File(dir, "notes.json").apply { writeText("personal") }
        File(dir, "yomu_fake.json").mkdir()
        val initial = listOf(good, bad, partial, other).associateWith { it.readBytes().toList() }

        val audit = auditAutomaticBackupArchives(dir)
        assertTrue(audit.folderAvailable)
        assertEquals(2, audit.entries.size)
        assertEquals(1, audit.verified)
        assertEquals(1, audit.invalid)
        assertEquals(0, audit.unreadable)
        assertEquals(good.name, audit.newestVerified?.fileName)
        assertEquals("0.320", audit.newestVerified?.appVersion)
        assertTrue(audit.entries.single { it.fileName == bad.name }.reason!!.contains("verification"))
        assertFalse(audit.entries.joinToString().contains("token=private"))
        initial.forEach { (file, contents) -> assertEquals(contents, file.readBytes().toList()) }
        assertTrue(File(dir, "yomu_fake.json").isDirectory)
    }

    @Test fun handlesAbsentFoldersAndEmptyFolders() = withFolder { dir ->
        val absent = auditAutomaticBackupArchives(File(dir, "missing"))
        assertFalse(absent.folderAvailable)
        assertNull(absent.newestVerified)
        val empty = auditAutomaticBackupArchives(dir)
        assertTrue(empty.folderAvailable)
        assertEquals(0, empty.entries.size)
    }

    @Test fun limitsReadsButKeepsTruncationVisible() = withFolder { dir ->
        for (n in 1..7) {
            File(dir, "yomu_2026-01-0$n.json").writeText(json())
        }
        val audit = auditAutomaticBackupArchives(dir, limit = 3)
        assertEquals(3, audit.verified)
        assertEquals(4, audit.ignoredEntries)
        assertEquals(3, audit.entries.size)
        assertEquals("yomu_2026-01-07.json", audit.newestVerified?.fileName)
    }

    @Test fun restoreSelectionRejectsTraversalAndUnrelatedFiles() = withFolder { dir ->
        val good = File(dir, "yomu_2026-11-18_120000.json").apply { writeText(json()) }
        val unrelated = File(dir, "private.json").apply { writeText(json()) }
        assertEquals(good.canonicalFile, resolveVerifiedBackupArchive(dir, good.name))
        assertNull(resolveVerifiedBackupArchive(dir, unrelated.name))
        assertNull(resolveVerifiedBackupArchive(dir, "../${good.name}"))
        assertNull(resolveVerifiedBackupArchive(dir, "yomu_../../private.json"))
        assertNull(resolveVerifiedBackupArchive(dir, "yomu_missing.json"))
        assertNull(resolveVerifiedBackupArchive(dir, "yomu_partial.part"))
    }

    @Test fun restoreSelectionRejectsSymlinkOutsideBackupDirectory() = withFolder { dir ->
        val external = Files.createTempFile("yomu-external", ".json")
        try {
            Files.write(external, json().toByteArray(Charsets.UTF_8))
            val link = File(dir, "yomu_2026-11-18_140000.json")
            Files.createSymbolicLink(link.toPath(), external)
            assertNull(resolveVerifiedBackupArchive(dir, link.name))
            val audit = auditAutomaticBackupArchives(dir)
            assertEquals(0, audit.verified)
            assertEquals(1, audit.unreadable)
        } finally {
            Files.deleteIfExists(external)
        }
    }

    @Test fun invalidBackupIsNotClaimedAsSafeEvenIfNamedLikeOne() = withFolder { dir ->
        File(dir, "yomu_2026-12-31.json").writeText("""{"version":999,"stores":{}}""")
        File(dir, "yomu_2026-01-01.json").writeText(json("0.316"))
        val audit = auditAutomaticBackupArchives(dir)
        assertEquals(1, audit.invalid)
        assertEquals(1, audit.verified)
        assertEquals("yomu_2026-01-01.json", audit.newestVerified?.fileName)
        assertNotNull(audit.entries.firstOrNull { it.status == BackupArchiveStatus.INVALID })
    }
}
