package com.mangareader.app

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android file:// ContentResolver and SharedPreferences tests for the
 * audited-archive restore path. Files are isolated inside test app cache.
 */
@RunWith(AndroidJUnit4::class)
class BackupArchiveRestoreDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun archive(value: String): String {
        val setting = JSONObject().put("t", "s").put("v", value)
        val prefs = JSONObject().put("test_archive_restore_marker", setting)
        return JSONObject()
            .put("version", 1)
            .put("app", "0.325-device-test")
            .put("createdAt", 123456L)
            .put("stores", JSONObject().put("manga_reader", prefs))
            .toString()
    }

    private fun withBackupFolder(block: (File) -> Unit) {
        val folder = Files.createTempDirectory(context.cacheDir.toPath(), "audit-device-").toFile()
        try {
            block(folder)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun choosingVerifiedArchiveOnlyPreviewsAndDoesNotOverwritePreferences() =
        withBackupFolder { folder ->
            val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
            val key = "test_archive_restore_marker"
            val previous = prefs.getString(key, null)
            val file = File(folder, "yomu_2026-11-18_150000.json")
            val bytes = archive("from-backup").toByteArray(Charsets.UTF_8)
            file.writeBytes(bytes)
            try {
                assertTrue(prefs.edit().putString(key, "current-library").commit())
                val audit = auditAutomaticBackupArchives(folder)
                assertEquals(1, audit.verified)
                assertEquals(file.name, audit.newestVerified?.fileName)
                val resolved = resolveVerifiedBackupArchive(folder, file.name)
                assertEquals(file.canonicalFile, resolved)
                val report = Backup.verifyFrom(context, Uri.fromFile(resolved!!)).getOrThrow()
                assertEquals(1, report.settingCount)
                // Closing/cancelling the confirmation doesn't invoke restore.
                assertEquals("current-library", prefs.getString(key, null))
                assertArrayEquals(bytes, file.readBytes())
            } finally {
                val editor = prefs.edit()
                if (previous == null) editor.remove(key) else editor.putString(key, previous)
                assertTrue(editor.commit())
            }
        }

    @Test fun changedArchiveAfterPreviewCannotReplaceOriginalPreferences() =
        withBackupFolder { folder ->
            val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
            val key = "test_archive_restore_marker"
            val previous = prefs.getString(key, null)
            val file = File(folder, "yomu_2026-11-18_150000.json")
            try {
                assertTrue(prefs.edit().putString(key, "keep-this").commit())
                file.writeText(archive("original"))
                val selected = resolveVerifiedBackupArchive(folder, file.name)!!
                val verified = Backup.verifyFrom(context, Uri.fromFile(selected)).getOrThrow()
                file.writeText(archive("changed-by-external-app"))
                val restored = Backup.restoreFrom(
                    context, Uri.fromFile(selected), expectedFingerprint = verified.fingerprint,
                )
                assertTrue(restored.isFailure)
                assertEquals("keep-this", prefs.getString(key, null))
            } finally {
                val editor = prefs.edit()
                if (previous == null) editor.remove(key) else editor.putString(key, previous)
                assertTrue(editor.commit())
            }
        }

    @Test fun unsafeOrUnreadableCandidatesCannotBeSelectedForRestore() =
        withBackupFolder { folder ->
            val good = File(folder, "yomu_2026-11-18_110000.json")
            good.writeText(archive("safe"))
            val bad = File(folder, "yomu_2026-11-18_120000.json")
            bad.writeText("broken")
            File(folder, "yomu_temporary.part").writeText("unfinished")
            val external = Files.createTempFile(context.cacheDir.toPath(), "outside-", ".json")
            try {
                Files.write(external, archive("not-in-folder").toByteArray(Charsets.UTF_8))
                val symlink = File(folder, "yomu_2026-11-18_130000.json")
                Files.createSymbolicLink(symlink.toPath(), external)

                val audit = auditAutomaticBackupArchives(folder)
                assertEquals(1, audit.verified)
                assertEquals(1, audit.invalid)
                assertEquals(1, audit.unreadable)
                assertEquals(good.name, audit.newestVerified?.fileName)
                assertNull(resolveVerifiedBackupArchive(folder, symlink.name))
                assertNull(resolveVerifiedBackupArchive(folder, "../" + good.name))
                assertNull(resolveVerifiedBackupArchive(folder, "yomu_temporary.part"))
                assertNull(resolveVerifiedBackupArchive(folder, "yomu_missing.json"))
                // Naming alone is insufficient: the restore verification still
                // rejects a corrupt archive after direct-file resolution.
                val selectedBad = resolveVerifiedBackupArchive(folder, bad.name)!!
                assertFalse(Backup.verifyFrom(context, Uri.fromFile(selectedBad)).isSuccess)
            } finally {
                Files.deleteIfExists(external)
            }
        }
}
