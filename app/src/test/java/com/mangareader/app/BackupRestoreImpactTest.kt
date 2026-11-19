package com.mangareader.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupRestoreImpactTest {
    private fun entry(type: String, value: Any): JSONObject =
        JSONObject().put("t", type).put("v", value)

    @Test fun countsAdditionsChangesRemovalsAndUnchangedWithoutExposingValues() {
        val current = JSONObject()
            .put("newer_only", entry("s", "private-token"))
            .put("same", entry("b", true))
            .put("update", entry("i", 2))
        val incoming = JSONObject()
            .put("same", entry("b", true))
            .put("update", entry("i", 3))
            .put("new", entry("s", "private-data"))
        val result = calculateBackupRestoreImpact(
            archive = mapOf("manga_reader" to incoming),
            current = mapOf("manga_reader" to current),
        )
        assertEquals(1, result.storesReplaced)
        assertEquals(1, result.settingsAdded)
        assertEquals(1, result.settingsChanged)
        assertEquals(1, result.settingsRemoved)
        assertEquals(1, result.settingsUnchanged)
        assertFalse(result.toString().contains("private-token"))
        assertFalse(result.toString().contains("private-data"))
    }

    @Test fun stringSetsHaveOrderIndependentComparison() {
        val current = JSONObject().put(
            "languages", entry("ss", JSONArray().put("en").put("ar")),
        )
        val incoming = JSONObject().put(
            "languages", entry("ss", JSONArray().put("ar").put("en")),
        )
        val result = calculateBackupRestoreImpact(
            archive = mapOf("source_demo" to incoming),
            current = mapOf("source_demo" to current),
        )
        assertEquals(1, result.settingsUnchanged)
        assertEquals(0, result.settingsChanged)
    }

    @Test fun missingSourceStoreIsCountedButUnlistedStoresRemainUntouched() {
        val backup = JSONObject().put("extension_state", entry("s", "active"))
        val result = calculateBackupRestoreImpact(
            archive = mapOf("source_new" to backup),
            current = mapOf("source_other" to JSONObject().put("session", entry("s", "keep"))),
        )
        assertEquals(1, result.storesReplaced)
        assertEquals(1, result.settingsAdded)
        assertEquals(0, result.settingsRemoved)
    }

    @Test fun preservedLocalStorageDirectoryDoesNotInflateImpact() {
        val existing = JSONObject()
            .put("storage_dir", entry("s", "/storage/card"))
            .put("bookmark", entry("s", "old"))
        val backup = JSONObject()
            .put("storage_dir", entry("s", "/different-device"))
            .put("bookmark", entry("s", "new"))
        val result = calculateBackupRestoreImpact(
            archive = mapOf("manga_reader" to backup),
            current = mapOf("manga_reader" to existing),
        )
        assertEquals(0, result.settingsAdded)
        assertEquals(1, result.settingsChanged)
        assertEquals(0, result.settingsRemoved)
    }

    @Test fun unknownPreferenceTypesAreNotCountedAsWritten() {
        val incoming = JSONObject()
            .put("unsupported", entry("future", "not-restored"))
            .put("known", entry("i", 7))
        val result = calculateBackupRestoreImpact(
            archive = mapOf("manga_reader" to incoming),
            current = emptyMap(),
        )
        assertEquals(1, result.settingsAdded)
        assertEquals(0, result.settingsRemoved)
    }
}
