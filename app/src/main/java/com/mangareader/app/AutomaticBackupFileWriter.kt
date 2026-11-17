package com.mangareader.app

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Publish an automatic backup only after both the generated payload and the
 * on-disk bytes pass the same validation as a real restore.
 *
 * A failed write never prunes old backups or advances the last-backup timestamp.
 * Unique filenames avoid replacing an existing backup when two runs land in
 * the same minute. A stale .part file is never mistaken for a valid archive.
 */
internal object AutomaticBackupFileWriter {
    @Synchronized
    fun writeVerified(
        folder: File,
        bytes: ByteArray,
        timestamp: Long,
        keep: Int = 5,
        beforePublish: (File) -> Unit = {},
    ): Boolean {
        if (!folder.isDirectory || keep <= 0) return false

        // Check the input *before* touching the backup directory or its
        // previously saved archives.
        val fingerprint = runCatching {
            BackupVerification.validate(bytes).report.fingerprint
        }.getOrElse { return false }

        val temp = runCatching {
            File.createTempFile(".yomu_backup_", ".part", folder)
        }.getOrElse { return false }

        var published: File? = null
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.flush()
                stream.fd.sync()
            }
            beforePublish(temp)

            fun verify(file: File): Boolean {
                val actual = file.inputStream().use {
                    BackupVerification.validate(BackupVerification.readBounded(it)).report.fingerprint
                }
                return actual == fingerprint
            }

            check(verify(temp)) { "Temporary backup differs from verified payload." }

            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US)
                .format(Date(timestamp))
            val destination = File(folder, "yomu_${stamp}_${UUID.randomUUID()}.json")
            check(!destination.exists()) { "Backup destination already exists." }
            check(temp.renameTo(destination)) { "Could not finalize backup file." }
            published = destination

            // Verify the actual final file before deleting any older archive.
            check(verify(destination)) { "Published backup failed verification." }

            pruneOldBackups(folder, keep)
            published = null
            return true
        } catch (_: Exception) {
            // Only a file created by THIS attempt may be cleaned up. Existing
            // backups and incomplete .part files from other runs are untouched.
            published?.delete()
            return false
        } finally {
            temp.delete()
        }
    }

    /** Never prune on failure; this is called only after the new file verifies. */
    private fun pruneOldBackups(folder: File, keep: Int) {
        runCatching {
            folder.listFiles()
                ?.filter { it.isFile && it.name.startsWith("yomu_") && it.name.endsWith(".json") }
                ?.sortedBy { it.name }
                ?.dropLast(keep)
                ?.forEach { it.delete() }
        }
    }
}
