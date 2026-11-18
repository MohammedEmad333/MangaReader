package com.mangareader.app

import java.io.File

/** Read-only result; never include JSON values, source logins or private paths in UI. */
internal enum class BackupArchiveStatus { VERIFIED, INVALID, UNREADABLE }

internal data class BackupArchiveEntry(
    val fileName: String,
    val status: BackupArchiveStatus,
    val appVersion: String? = null,
    val byteCount: Int? = null,
    val reason: String? = null,
)

internal data class BackupArchiveAudit(
    val entries: List<BackupArchiveEntry>,
    val ignoredEntries: Int,
    val folderAvailable: Boolean,
) {
    val verified: Int get() = entries.count { it.status == BackupArchiveStatus.VERIFIED }
    val invalid: Int get() = entries.count { it.status == BackupArchiveStatus.INVALID }
    val unreadable: Int get() = entries.count { it.status == BackupArchiveStatus.UNREADABLE }
    val newestVerified: BackupArchiveEntry?
        get() = entries.firstOrNull { it.status == BackupArchiveStatus.VERIFIED }
}

/**
 * Enumerates only Yomu's published .json archives. Partial .part files,
 * unrelated files and subdirectories are never opened.
 *
 * A scan does NOT mark an archive safe until it passes the same strict parser
 * used by restore. No file is renamed, deleted, pruned, rewritten or restored.
 * Files may change after scanning; restore always validates them again.
 */
internal fun auditAutomaticBackupArchives(
    folder: File,
    limit: Int = 100,
): BackupArchiveAudit {
    require(limit in 1..1000) { "Invalid archive audit limit." }
    if (!folder.isDirectory) {
        return BackupArchiveAudit(emptyList(), ignoredEntries = 0, folderAvailable = false)
    }
    val children = folder.listFiles()
        ?: return BackupArchiveAudit(emptyList(), ignoredEntries = 0, folderAvailable = false)
    val matching = children.filter {
        it.isFile && it.name.startsWith("yomu_") && it.name.endsWith(".json")
    }.sortedByDescending { it.name }

    // Limit the number of file reads for a folder accidentally containing
    // thousands of archives. We report, rather than silently ignore, the rest.
    val entries = matching.take(limit).map { file ->
        val name = file.name
        if (!file.canRead()) {
            BackupArchiveEntry(name, BackupArchiveStatus.UNREADABLE, reason = "Cannot read this file.")
        } else {
            val result = runCatching {
                file.inputStream().use { stream ->
                    BackupVerification.validate(BackupVerification.readBounded(stream)).report
                }
            }
            result.fold(
                onSuccess = {
                    BackupArchiveEntry(
                        fileName = name,
                        status = BackupArchiveStatus.VERIFIED,
                        appVersion = it.appVersion,
                        byteCount = it.byteCount,
                    )
                },
                onFailure = {
                    // Exception messages from JSON parsers may contain raw
                    // backup text. Deliberately do not expose them here.
                    BackupArchiveEntry(
                        fileName = name,
                        status = BackupArchiveStatus.INVALID,
                        reason = "File failed backup verification.",
                    )
                },
            )
        }
    }
    return BackupArchiveAudit(
        entries = entries,
        ignoredEntries = matching.size - entries.size,
        folderAvailable = true,
    )
}
