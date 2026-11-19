package com.mangareader.app

import org.json.JSONArray
import org.json.JSONObject

/** Counts only. Never serialize, log, or display preference names or values. */
internal data class BackupRestoreImpact(
    val storesReplaced: Int,
    val settingsAdded: Int,
    val settingsChanged: Int,
    val settingsRemoved: Int,
    val settingsUnchanged: Int,
)

internal data class BackupRestorePreview(
    val report: BackupVerificationReport,
    val impact: BackupRestoreImpact,
)

/**
 * Compare recognized typed settings in stores that the backup will replace.
 * Local storage_dir is device-specific and is explicitly preserved by restore.
 * A missing store in the archive is not touched by the current restore path.
 */
internal fun calculateBackupRestoreImpact(
    archive: Map<String, JSONObject>,
    current: Map<String, JSONObject>,
): BackupRestoreImpact {
    var added = 0
    var changed = 0
    var removed = 0
    var unchanged = 0
    for ((store, backupEntries) in archive) {
        val existing = current[store] ?: JSONObject()
        val keys = (existing.keys().asSequence() + backupEntries.keys().asSequence()).toSet()
        for (key in keys) {
            if (store == "manga_reader" && key == "storage_dir") continue
            val before = existing.optJSONObject(key)?.let(::normalizedBackupPreference)
            val after = backupEntries.optJSONObject(key)?.let(::normalizedBackupPreference)
            when {
                before == null && after != null -> added++
                before != null && after == null -> removed++
                before != null && after != null && before != after -> changed++
                before != null && after != null -> unchanged++
            }
        }
    }
    return BackupRestoreImpact(
        storesReplaced = archive.size,
        settingsAdded = added,
        settingsChanged = changed,
        settingsRemoved = removed,
        settingsUnchanged = unchanged,
    )
}

/** Stable comparisons for string sets regardless of JSON element ordering. */
private fun normalizedBackupPreference(entry: JSONObject): Pair<String, Any>? {
    val type = entry.optString("t")
    val value: Any = when (type) {
        "b" -> entry.optBoolean("v")
        "i" -> entry.optInt("v")
        "l" -> entry.optLong("v")
        "f" -> entry.optDouble("v").toFloat()
        "s" -> entry.optString("v")
        "ss" -> {
            val arr = entry.optJSONArray("v") ?: return null
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
        else -> return null
    }
    return type to value
}
