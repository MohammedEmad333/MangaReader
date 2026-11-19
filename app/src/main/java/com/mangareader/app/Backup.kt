package com.mangareader.app

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Backup and restore.
 *
 * **What a backup is here: every SharedPreferences file, verbatim.** That reads
 * like laziness and isn't. This app has no database — [Library], [Categories],
 * [History], [ReadState], [SourceManager], [ExtensionRepos], [SourcePrefs] and
 * [ReaderPrefs] are all JSON strings or scalars in one store called
 * `manga_reader`, and each installed extension's own settings are in a
 * `source_<id>` store written by [SourceSettings]. Copying those files copies
 * the library, the categories, the read flags, every resume position, the
 * reader settings, the theme, the pinned sources, the hidden languages, the
 * repo list and the per-source logins, with no per-model serialiser to write
 * and — more to the point — no per-model serialiser to forget to update the
 * next time a field is added. Mihon needs a schema because it has a database
 * with foreign keys; this doesn't.
 *
 * The cost is that a backup is only portable to this app, and that the format
 * has to survive a pref key being renamed. [VERSION] is here for the second
 * one. Restore drops anything it doesn't recognise rather than failing.
 *
 * **Downloaded chapters are not in it.** They're page images under `filesDir`,
 * measured in gigabytes, and a backup that big isn't a backup. What is included
 * is the chapter *lists*, so a restored library opens offline; the pages come
 * back by downloading them again.
 */
internal object Backup {

    /** Bumped when the payload shape changes, not when a pref key is added. */
    private const val VERSION = 1

    private const val APP_PREFS = "manga_reader"

    /**
     * The one key deliberately left out of the payload — [StorageLocation]'s.
     *
     * It holds a filesystem path, and a path is device-local even when it looks
     * portable: `/storage/1A2B-3C4D/Manga` is a card that exists in one phone.
     * Carrying it across would point a restored install at a folder that isn't
     * there and silently strand its downloads in internal storage, with a
     * settings row still naming the card. Restore keeps whatever the local
     * install already had.
     */
    private const val KEY_DIR = "storage_dir"
    private const val KEY_FREQUENCY = "backup_frequency_hours"
    private const val KEY_LAST = "backup_last_at"
    private const val KEY_ATTEMPT = "backup_last_attempt_at"
    private const val KEY_OUTCOME = "backup_last_attempt_succeeded"

    /** How many automatic backups are kept in the folder before the oldest go. */
    private const val KEEP = 5

    private const val WORK_NAME = "auto_backup"

    // ---------- settings ----------

    /**
     * Where automatic backups land: a `backups` folder beside the chapters,
     * inside whatever [StorageLocation] resolves to.
     *
     * This used to be its own SAF tree with its own picker, which meant two
     * "storage location" settings that could disagree. One folder holding both
     * is what the user was asking for and one less thing to keep in sync — and
     * with the location now a real path, a backup is an ordinary file write
     * rather than a document-provider transaction.
     */
    fun backupsDir(context: Context): File =
        File(StorageLocation.base(context), StorageLocation.BACKUPS)

    fun frequency(context: Context): BackupFrequency =
        BackupFrequency.from(prefs(context).getInt(KEY_FREQUENCY, 0))

    fun setFrequency(context: Context, value: BackupFrequency) {
        prefs(context).edit().putInt(KEY_FREQUENCY, value.hours).apply()
        schedule(context, value)
    }

    /** Epoch millis of the last successful automatic backup, or 0. */
    fun lastBackupAt(context: Context): Long = prefs(context).getLong(KEY_LAST, 0L)

    fun lastAttemptAt(context: Context): Long = prefs(context).getLong(KEY_ATTEMPT, 0L)

    fun lastAttemptSucceeded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OUTCOME, false)

    // ---------- scheduling ----------

    /**
     * WorkManager rather than a check on app start.
     *
     * A launch-time check would be a third of the code and would make the label
     * on the settings row false: "every 12 hours" would mean "the next time you
     * happen to open the app", which for the fortnight you don't open it means
     * no backup at all — exactly the fortnight in which losing the library
     * hurts. WorkManager persists its own schedule across reboots and app
     * updates, so nothing here has to re-arm it.
     */
    fun schedule(context: Context, value: BackupFrequency) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (value.hours <= 0) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            value.hours.toLong(),
            TimeUnit.HOURS
        )
            // Writing a few hundred KB shouldn't wake a phone that's nearly flat.
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    // ---------- writing ----------

    /** The whole payload as JSON text. Small enough to hold in memory. */
    fun payload(context: Context): String {
        val stores = JSONObject()
        BackupCodec.storeNames(context, APP_PREFS).forEach { name ->
            val map = context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .all
            stores.put(name, BackupCodec.encode(map, skip = if (name == APP_PREFS) setOf(KEY_DIR) else emptySet()))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("app", BuildConfig.VERSION_NAME)
            .put("createdAt", System.currentTimeMillis())
            .put("stores", stores)
            .toString()
    }

    /** Manual "Create backup": writes to a file the user picked. */
    fun writeTo(context: Context, uri: Uri): Result<Unit> = runCatching {
        val bytes = payload(context).toByteArray()
        context.contentResolver.openOutputStream(uri, "wt")
            ?.use { it.write(bytes) }
            ?: error("Couldn't open the file for writing")
    }

    /**
     * One automatic backup into the chosen folder, oldest pruned.
     *
     * Returns false rather than throwing when there's no folder set or the
     * grant has been revoked — both are states the worker should stop retrying
     * on, because no amount of retrying fixes them.
     */
    fun runAutomatic(context: Context): Boolean {
        val attemptAt = System.currentTimeMillis()
        val saved = runCatching {
            val folder = backupsDir(context)
            if (!StorageLocation.ensureWritable(folder)) return@runCatching false

            AutomaticBackupFileWriter.writeVerified(
                folder = folder,
                bytes = payload(context).toByteArray(Charsets.UTF_8),
                timestamp = attemptAt,
                keep = KEEP,
            )
        }.getOrDefault(false)

        // Commit the outcome so the settings screen can distinguish a missed
        // backup from an old success. Do not overwrite the last-good timestamp
        // on failure. Do not store paths, source settings or error details.
        prefs(context).edit()
            .putLong(KEY_ATTEMPT, attemptAt)
            .putBoolean(KEY_OUTCOME, saved)
            .apply()
        if (saved) {
            prefs(context).edit().putLong(KEY_LAST, attemptAt).apply()
        }
        return saved
    }

    // ---------- reading ----------

    /**
     * Replaces every store with the backup's, and returns how many keys landed.
     *
     * `commit()`, not `apply()`: the caller restarts the Activity on the next
     * line, and an `apply()` is only promised to reach disk eventually.
     * Clearing first is what makes a restore a restore — merging would leave a
     * series in the library that the backup had deleted.
     */
    fun verifyFrom(context: Context, uri: Uri): Result<BackupVerificationReport> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)
            ?.use { BackupVerification.readBounded(it) }
            ?: error("Couldn't open the file")
        BackupVerification.validate(bytes).report
    }

    /**
     * Read-only impact preview. Never applies, clears, or rewrites preferences.
     * Only backup-listed stores are compared; storage_dir is kept device-local.
     * The final restore revalidates the complete file and its fingerprint.
     */
    fun previewFrom(context: Context, uri: Uri): Result<BackupRestorePreview> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)
            ?.use { BackupVerification.readBounded(it) }
            ?: error("Couldn't open the file")
        val verified = BackupVerification.validate(bytes)
        val current = verified.stores.keys.associateWith { name ->
            val values = context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE).all
            BackupCodec.encode(
                values,
                skip = if (name == APP_PREFS) setOf(KEY_DIR) else emptySet(),
            )
        }
        BackupRestorePreview(
            report = verified.report,
            impact = calculateBackupRestoreImpact(verified.stores, current),
        )
    }

    fun restoreFrom(
        context: Context,
        uri: Uri,
        expectedFingerprint: String? = null,
        /** Android device tests can simulate an interruption after a prior store commits. */
        beforeStoreCommit: (String) -> Unit = {},
    ): Result<Int> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)
            ?.use { BackupVerification.readBounded(it) }
            ?: error("Couldn't open the file")
        val verified = BackupVerification.validate(bytes)
        if (expectedFingerprint != null) {
            require(verified.report.fingerprint == expectedFingerprint) {
                "The backup file changed since verification. Inspect it again before restoring."
            }
        }
        val accepted = verified.stores

        // Held across the wipe: a path belonging to this device, not the backup.
        val localDir = prefs(context).getString(KEY_DIR, null)

        // Capture a complete in-memory snapshot before the first destructive write.
        // If a later store fails, restore all touched stores, including the one
        // whose commit failed. This is a best-effort rollback, not a transaction
        // across Android SharedPreferences files or a power-loss guarantee.
        val original = accepted.keys.associateWith { name ->
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                .all.toMap()
        }
        var restored = 0
        val touched = mutableListOf<String>()
        try {
            accepted.entries.sortedWith(compareBy { it.key != APP_PREFS })
                .forEach { (name, entries) ->
                val editor = context.applicationContext
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                touched += name
                beforeStoreCommit(name)
                editor.clear()
                val written = BackupCodec.decodeInto(editor, entries)
                if (name == APP_PREFS && localDir != null) editor.putString(KEY_DIR, localDir)
                check(editor.commit()) { "Could not write backup settings." }
                restored += written
            }
        } catch (failure: Exception) {
            val rollbackFailures = mutableListOf<String>()
            touched.asReversed().forEach { name ->
                val reverted = runCatching {
                    val editor = context.applicationContext
                        .getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                    editor.clear()
                    BackupCodec.writeValues(editor, original.getValue(name))
                    check(editor.commit()) { "Could not roll back backup settings." }
                }
                if (reverted.isFailure) rollbackFailures += name
            }
            if (rollbackFailures.isNotEmpty()) {
                error("Restore failed and some settings could not be rolled back: " +
                    rollbackFailures.joinToString(", "))
            }
            throw failure
        }

        // The frequency came out of the backup; the schedule has to be re-armed
        // to match it, or the restored setting is a label with nothing behind it.
        schedule(context, frequency(context))
        restored
    }


}
