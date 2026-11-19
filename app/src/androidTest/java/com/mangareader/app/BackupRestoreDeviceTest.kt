package com.mangareader.app

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android ContentResolver and SharedPreferences, isolated to the emulator.
 * Never uses a user's external storage, installed backups or downloaded pages.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun archive(
        file: File,
        value: String,
        includeLocalMetadata: Boolean = false,
    ): Uri {
        val store = JSONObject().put(
            "test_saved_bookmark",
            JSONObject().put("t", "s").put("v", value),
        )
        if (includeLocalMetadata) {
            store.put("storage_dir", JSONObject().put("t", "s").put("v", "/archive/device"))
            store.put("backup_last_at", JSONObject().put("t", "l").put("v", 10L))
            store.put("backup_last_attempt_at", JSONObject().put("t", "l").put("v", 20L))
            store.put("backup_last_attempt_succeeded", JSONObject().put("t", "b").put("v", true))
        }
        val payload = JSONObject()
            .put("version", 1)
            .put("app", "0.319-test")
            .put("stores", JSONObject().put("manga_reader", store))
        file.writeText(payload.toString())
        return Uri.fromFile(file)
    }

    @Test fun verifyThenRestoreRoundTripUsesRealAndroidPreferences() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val key = "test_saved_bookmark"
        val before = prefs.getString(key, null)
        val file = File(context.cacheDir, "device-backup-roundtrip.json")
        try {
            val uri = archive(file, "chapter-12-page-7")
            val checked = Backup.verifyFrom(context, uri).getOrThrow()
            assertEquals(1, checked.storeCount)
            assertEquals(1, checked.settingCount)
            // Inspecting the archive must not change existing local preferences.
            assertEquals(before, prefs.getString(key, null))
            assertTrue(prefs.edit().putString(key, "local-newer-value").commit())
            val restored = Backup.restoreFrom(context, uri, checked.fingerprint).getOrThrow()
            assertEquals(1, restored)
            assertEquals("chapter-12-page-7", prefs.getString(key, null))
        } finally {
            val editor = prefs.edit()
            if (before == null) editor.remove(key) else editor.putString(key, before)
            editor.commit()
            file.delete()
        }
    }

    @Test fun impactPreviewDoesNotChangeAndroidPreferences() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val key = "test_saved_bookmark"
        val before = prefs.getString(key, null)
        val file = File(context.cacheDir, "device-backup-impact-preview.json")
        try {
            assertTrue(prefs.edit().putString(key, "local-value").commit())
            val uri = archive(file, "archive-value")
            val preview = Backup.previewFrom(context, uri).getOrThrow()
            assertEquals(1, preview.impact.settingsChanged)
            assertEquals(1, preview.impact.storesReplaced)
            assertEquals("local-value", prefs.getString(key, null))
            assertEquals(
                Backup.verifyFrom(context, uri).getOrThrow().fingerprint,
                preview.report.fingerprint,
            )
        } finally {
            val editor = prefs.edit()
            if (before == null) editor.remove(key) else editor.putString(key, before)
            assertTrue(editor.commit())
            file.delete()
        }
    }

    @Test fun staleLocalSettingsPreviewCannotOverwriteNewReadingProgress() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val key = "test_saved_bookmark"
        val previous = prefs.getString(key, null)
        val file = File(context.cacheDir, "device-backup-stale-preview.json")
        try {
            assertTrue(prefs.edit().putString(key, "page-15").commit())
            val uri = archive(file, "page-8")
            val preview = Backup.previewFrom(context, uri).getOrThrow()
            assertEquals(1, preview.impact.settingsChanged)
            assertTrue(prefs.edit().putString(key, "page-16").commit())
            val result = Backup.restoreFrom(
                context = context,
                uri = uri,
                expectedFingerprint = preview.report.fingerprint,
                expectedCurrentStateFingerprint = preview.currentStateFingerprint,
            )
            assertTrue(result.isFailure)
            assertEquals("page-16", prefs.getString(key, null))
        } finally {
            val editor = prefs.edit()
            if (previous == null) editor.remove(key) else editor.putString(key, previous)
            assertTrue(editor.commit())
            file.delete()
        }
    }

    @Test fun unchangedLocalSettingsPermitConfirmedRestore() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val key = "test_saved_bookmark"
        val original = prefs.all.toMap()
        val file = File(context.cacheDir, "device-backup-confirmed-preview.json")
        try {
            assertTrue(prefs.edit().putString(key, "page-15").commit())
            val uri = archive(file, "page-8")
            val preview = Backup.previewFrom(context, uri).getOrThrow()
            val restored = Backup.restoreFrom(
                context = context,
                uri = uri,
                expectedFingerprint = preview.report.fingerprint,
                expectedCurrentStateFingerprint = preview.currentStateFingerprint,
            )
            assertTrue(restored.isSuccess)
            assertEquals("page-8", prefs.getString(key, null))
        } finally {
            val editor = prefs.edit().clear()
            BackupCodec.writeValues(editor, original)
            assertTrue(editor.commit())
            file.delete()
        }
    }

    @Test fun olderArchiveKeepsCurrentDeviceStorageAndBackupStatus() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val key = "test_saved_bookmark"
        val file = File(context.cacheDir, "device-backup-local-metadata.json")
        try {
            assertTrue(prefs.edit()
                .putString(key, "page-15")
                .putString("storage_dir", "/local/device")
                .putLong("backup_last_at", 200L)
                .putLong("backup_last_attempt_at", 300L)
                .putBoolean("backup_last_attempt_succeeded", false)
                .commit())

            val generated = JSONObject(Backup.payload(context))
                .getJSONObject("stores")
                .getJSONObject("manga_reader")
            assertFalse(generated.has("storage_dir"))
            assertFalse(generated.has("backup_last_at"))
            assertFalse(generated.has("backup_last_attempt_at"))
            assertFalse(generated.has("backup_last_attempt_succeeded"))

            val uri = archive(file, "page-8", includeLocalMetadata = true)
            val preview = Backup.previewFrom(context, uri).getOrThrow()
            assertEquals(1, preview.impact.settingsChanged)

            val restored = Backup.restoreFrom(
                context = context,
                uri = uri,
                expectedFingerprint = preview.report.fingerprint,
                expectedCurrentStateFingerprint = preview.currentStateFingerprint,
            )
            assertTrue(restored.isSuccess)
            assertEquals("page-8", prefs.getString(key, null))
            assertEquals("/local/device", prefs.getString("storage_dir", null))
            assertEquals(200L, prefs.getLong("backup_last_at", 0L))
            assertEquals(300L, prefs.getLong("backup_last_attempt_at", 0L))
            assertFalse(prefs.getBoolean("backup_last_attempt_succeeded", true))
        } finally {
            val editor = prefs.edit().clear()
            BackupCodec.writeValues(editor, original)
            assertTrue(editor.commit())
            file.delete()
        }
    }

    @Test fun interruptedMultiStoreRestoreRollsBackAllPreferences() {
        val appPrefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val extensionPrefs = context.getSharedPreferences("source_device_rollback", Context.MODE_PRIVATE)
        val originalApp = appPrefs.all.toMap()
        val originalExtension = extensionPrefs.all.toMap()
        val file = File(context.cacheDir, "device-backup-rollback.json")
        try {
            assertTrue(appPrefs.edit().putString("rollback_marker", "keep-original").commit())
            assertTrue(extensionPrefs.edit()
                .putString("secret", "original")
                .putBoolean("enabled", true)
                .putLong("count", 17L)
                .commit())

            val archive = JSONObject().put("version", 1).put("stores", JSONObject()
                .put("manga_reader", JSONObject().put("rollback_marker",
                    JSONObject().put("t", "s").put("v", "replacement")))
                .put("source_device_rollback", JSONObject().put("secret",
                    JSONObject().put("t", "s").put("v", "replacement"))))
            file.writeText(archive.toString())
            val uri = Uri.fromFile(file)
            val fingerprint = Backup.verifyFrom(context, uri).getOrThrow().fingerprint
            val outcome = Backup.restoreFrom(
                context = context,
                uri = uri,
                expectedFingerprint = fingerprint,
                beforeStoreCommit = { store ->
                    if (store == "source_device_rollback") throw IllegalStateException("Simulated write failure")
                },
            )
            assertTrue(outcome.isFailure)
            assertEquals("keep-original", appPrefs.getString("rollback_marker", null))
            assertEquals("original", extensionPrefs.getString("secret", null))
            assertTrue(extensionPrefs.getBoolean("enabled", false))
            assertEquals(17L, extensionPrefs.getLong("count", 0L))
        } finally {
            val appEditor = appPrefs.edit().clear()
            BackupCodec.writeValues(appEditor, originalApp)
            assertTrue(appEditor.commit())
            val sourceEditor = extensionPrefs.edit().clear()
            BackupCodec.writeValues(sourceEditor, originalExtension)
            assertTrue(sourceEditor.commit())
            file.delete()
        }
    }

    @Test fun changedBackupMustNotOverwriteLocalSettings() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val key = "test_saved_bookmark"
        val before = prefs.getString(key, null)
        val file = File(context.cacheDir, "device-backup-tamper.json")
        try {
            val uri = archive(file, "before")
            val fingerprint = Backup.verifyFrom(context, uri).getOrThrow().fingerprint
            assertTrue(prefs.edit().putString(key, "untouched-local").commit())
            archive(file, "after")
            val result = Backup.restoreFrom(context, uri, fingerprint)
            assertTrue(result.isFailure)
            assertEquals("untouched-local", prefs.getString(key, null))
        } finally {
            val editor = prefs.edit()
            if (before == null) editor.remove(key) else editor.putString(key, before)
            editor.commit()
            file.delete()
        }
    }

    @Test fun corruptArchiveDoesNotModifyLocalPreferences() {
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val key = "test_saved_bookmark"
        val before = prefs.getString(key, null)
        val file = File(context.cacheDir, "device-backup-invalid.json")
        try {
            assertTrue(prefs.edit().putString(key, "must-survive").commit())
            file.writeText("{\"version\":1,\"stores\":{")
            val uri = Uri.fromFile(file)
            assertTrue(Backup.verifyFrom(context, uri).isFailure)
            assertTrue(Backup.restoreFrom(context, uri, null).isFailure)
            assertEquals("must-survive", prefs.getString(key, null))
        } finally {
            val editor = prefs.edit()
            if (before == null) editor.remove(key) else editor.putString(key, before)
            editor.commit()
            file.delete()
        }
    }
}
