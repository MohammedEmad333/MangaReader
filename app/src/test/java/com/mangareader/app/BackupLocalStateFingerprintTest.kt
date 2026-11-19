package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupLocalStateFingerprintTest {
    @Test fun stableRegardlessOfStoreKeyOrSetIterationOrder() {
        val first = mapOf(
            "manga_reader" to mapOf<String, Any>(
                "library" to "saved",
                "languages" to linkedSetOf("en", "ar"),
                "chapter" to 5,
                "read" to true,
            ),
            "source_demo" to mapOf<String, Any>("session" to "token"),
        )
        val reordered = mapOf(
            "source_demo" to mapOf<String, Any>("session" to "token"),
            "manga_reader" to mapOf<String, Any>(
                "read" to true,
                "chapter" to 5,
                "languages" to linkedSetOf("ar", "en"),
                "library" to "saved",
            ),
        )
        val a = backupLocalStateFingerprint(first)
        assertEquals(64, a.length)
        assertEquals(a, backupLocalStateFingerprint(reordered))
    }

    @Test fun changeOfValueOrTypeOrRemovalChangesFingerprint() {
        val original = mapOf("manga_reader" to mapOf<String, Any>(
            "bookmark" to "12",
            "enabled" to true,
        ))
        val baseline = backupLocalStateFingerprint(original)
        assertNotEquals(baseline, backupLocalStateFingerprint(
            mapOf("manga_reader" to mapOf("bookmark" to "13", "enabled" to true)),
        ))
        assertNotEquals(baseline, backupLocalStateFingerprint(
            mapOf("manga_reader" to mapOf("bookmark" to 12, "enabled" to true)),
        ))
        assertNotEquals(baseline, backupLocalStateFingerprint(
            mapOf("manga_reader" to mapOf("bookmark" to "12")),
        ))
        assertNotEquals(baseline, backupLocalStateFingerprint(
            mapOf("source_new" to original.getValue("manga_reader")),
        ))
    }

    @Test fun storageLocationAndBackgroundBackupTimestampDoNotCauseFalseWarning() {
        val baseline = mapOf("manga_reader" to mapOf<String, Any>(
            "bookmark" to "12",
            "storage_dir" to "/mnt/storage",
            "backup_last_at" to 10L,
            "backup_last_attempt_at" to 10L,
            "backup_last_attempt_succeeded" to true,
        ))
        val updated = mapOf("manga_reader" to mapOf<String, Any>(
            "bookmark" to "12",
            "storage_dir" to "/external/storage",
            "backup_last_at" to 20L,
            "backup_last_attempt_at" to 20L,
            "backup_last_attempt_succeeded" to false,
        ))
        assertEquals(
            backupLocalStateFingerprint(baseline),
            backupLocalStateFingerprint(updated),
        )
    }

    @Test fun prefixLengthPreventsJoiningTwoValuesFromColliding() {
        val a = mapOf("source_demo" to mapOf<String, Any>(
            "x" to "ab",
            "y" to "c",
        ))
        val b = mapOf("source_demo" to mapOf<String, Any>(
            "x" to "a",
            "y" to "bc",
        ))
        assertNotEquals(backupLocalStateFingerprint(a), backupLocalStateFingerprint(b))
    }
}
