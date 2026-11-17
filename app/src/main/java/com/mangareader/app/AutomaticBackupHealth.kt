package com.mangareader.app

import java.io.File

/**
 * These are deliberately coarse user-facing failure categories. Never save raw
 * exceptions, storage paths, source URLs, or credential-bearing payloads in
 * the diagnostic preference keys.
 */
internal enum class AutomaticBackupFailure(val key: String, val help: String) {
    STORAGE("storage", "Backup storage is unavailable. Check the selected folder and permissions."),
    DATA("data", "Could not prepare backup data. Your existing backups were not changed."),
    WRITE("write", "Could not verify or save the new backup. Existing backups were kept.");

    companion object {
        fun from(key: String?): AutomaticBackupFailure? =
            entries.firstOrNull { it.key == key }
    }
}

internal data class AutomaticBackupHealth(
    val verifiedAt: Long? = null,
    val checkedArchives: Int = 0,
    val invalidArchives: Int = 0,
    val newestArchiveInvalid: Boolean = false,
    val moreArchivesToCheck: Boolean = false,
    val folderUnreadable: Boolean = false,
)

/**
 * Read-only integrity check of the latest backup files in the *current*
 * storage location. A successful worker timestamp is not enough: the bytes
 * on disk must still pass the same verifier used by Restore backup.
 *
 * The scan is bounded in file count and per-file bytes and must run on IO.
 * Older intact backups can be reported when a newer file is corrupted.
 */
internal fun inspectAutomaticBackupHealth(
    folder: File,
    maxArchives: Int = 10,
): AutomaticBackupHealth {
    require(maxArchives > 0) { "Scan must inspect at least one archive." }
    if (!folder.exists()) return AutomaticBackupHealth()
    if (!folder.isDirectory) return AutomaticBackupHealth(folderUnreadable = true)

    val archived = folder.listFiles()
        ?: return AutomaticBackupHealth(folderUnreadable = true)
    val candidates = archived.filter {
        it.isFile && it.name.startsWith("yomu_") && it.name.endsWith(".json")
    }.sortedByDescending { it.name }

    var invalid = 0
    var newestInvalid = false
    var verifiedAt: Long? = null
    val inspected = candidates.take(maxArchives)
    inspected.forEachIndexed { index, file ->
        val report = runCatching {
            if (file.length() <= 0L || file.length() > 64L * 1024L * 1024L) {
                error("Invalid size")
            }
            file.inputStream().use { stream ->
                BackupVerification.validate(BackupVerification.readBounded(stream)).report
            }
        }.getOrNull()
        if (report == null) {
            invalid++
            if (index == 0) newestInvalid = true
        } else if (verifiedAt == null) {
            verifiedAt = report.createdAt.takeIf { it > 0L }
                ?: file.lastModified().takeIf { it > 0L }
        }
    }
    return AutomaticBackupHealth(
        verifiedAt = verifiedAt,
        checkedArchives = inspected.size,
        invalidArchives = invalid,
        newestArchiveInvalid = newestInvalid,
        moreArchivesToCheck = candidates.size > inspected.size,
    )
}
