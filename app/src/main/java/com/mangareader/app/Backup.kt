package com.mangareader.app

import android.content.Context
import android.content.SharedPreferences
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
import org.json.JSONArray
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
        storeNames(context).forEach { name ->
            val map = context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .all
            stores.put(name, encode(map, skip = if (name == APP_PREFS) setOf(KEY_DIR) else emptySet()))
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
    fun runAutomatic(context: Context): Boolean = runCatching {
        val folder = backupsDir(context)
        if (!StorageLocation.ensureWritable(folder)) return false

        val name = "yomu_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".json"
        // Written to a temp name and renamed, so a backup killed halfway through
        // isn't left looking like a complete one for the next restore to trust.
        val temp = File(folder, "$name.part")
        temp.writeText(payload(context))
        val target = File(folder, name)
        if (!temp.renameTo(target)) {
            temp.delete()
            return false
        }

        prune(folder)
        prefs(context).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        true
    }.getOrDefault(false)

    /** Names carry a sortable timestamp, so lexicographic order is age order. */
    private fun prune(folder: File) {
        runCatching {
            folder.listFiles()
                ?.filter { it.isFile && it.name.startsWith("yomu_") && it.name.endsWith(".json") }
                ?.sortedBy { it.name }
                ?.dropLast(KEEP)
                ?.forEach { it.delete() }
        }
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
    fun restoreFrom(context: Context, uri: Uri): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Couldn't open the file")

        val root = JSONObject(text)
        val version = root.optInt("version", -1)
        if (version < 1) error("This doesn't look like a Yomu backup")
        if (version > VERSION) error("That backup is from a newer version of Yomu")

        val stores = root.optJSONObject("stores") ?: error("Backup has no data in it")

        // Everything is validated before anything is cleared. The alternative —
        // wipe, then discover halfway down that the file was truncated — turns a
        // bad file into data loss, which is precisely the thing this feature is
        // supposed to prevent.
        //
        // The name filter is part of that: a hand-edited backup shouldn't be
        // able to name any pref file it likes and have it overwritten.
        val accepted = LinkedHashMap<String, JSONObject>()
        var incoming = 0
        for (name in stores.keys()) {
            if (name != APP_PREFS && !name.startsWith("source_")) continue
            val entries = stores.optJSONObject(name) ?: continue
            accepted[name] = entries
            incoming += entries.length()
        }
        if (incoming == 0) error("Backup was readable but empty")

        // Held across the wipe: a path belonging to this device, not the backup.
        val localDir = prefs(context).getString(KEY_DIR, null)

        var restored = 0
        accepted.forEach { (name, entries) ->
            val editor = context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
            editor.clear()
            restored += decodeInto(editor, entries)
            if (name == APP_PREFS && localDir != null) editor.putString(KEY_DIR, localDir)
            editor.commit()
        }

        // The frequency came out of the backup; the schedule has to be re-armed
        // to match it, or the restored setting is a label with nothing behind it.
        schedule(context, frequency(context))
        restored
    }

    // ---------- encoding ----------
    //
    // SharedPreferences values are one of six types and getAll() erases which,
    // so each entry carries a tag. Without it a Long comes back as an Int and
    // the next getLong() throws ClassCastException on a value that looks fine
    // in the file.

    private fun encode(map: Map<String, *>, skip: Set<String>): JSONObject {
        val out = JSONObject()
        map.forEach { (key, value) ->
            if (key in skip) return@forEach
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject().put("t", "ss").put(
                    "v",
                    JSONArray().apply { value.filterIsInstance<String>().forEach { put(it) } }
                )
                else -> null
            }
            if (entry != null) out.put(key, entry)
        }
        return out
    }

    private fun decodeInto(editor: SharedPreferences.Editor, entries: JSONObject): Int {
        var count = 0
        for (key in entries.keys()) {
            val entry = entries.optJSONObject(key) ?: continue
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "s" -> editor.putString(key, entry.optString("v"))
                "ss" -> {
                    val arr = entry.optJSONArray("v") ?: JSONArray()
                    editor.putStringSet(
                        key,
                        (0 until arr.length()).mapNotNull { arr.optString(it) }.toSet()
                    )
                }
                // An unknown tag is a key from a future version. Dropped, not fatal.
                else -> continue
            }
            count++
        }
        return count
    }

    /**
     * Which pref files exist, read off disk rather than guessed.
     *
     * The `source_<id>` stores are created by [SourceSettings] the first time an
     * extension writes a preference, so there's no list of them anywhere in the
     * app to enumerate — only the extensions currently installed, which isn't
     * the same set. The directory is.
     */
    private fun storeNames(context: Context): List<String> {
        val dir = File(context.applicationContext.applicationInfo.dataDir, "shared_prefs")
        val found = runCatching {
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml") }
                ?.map { it.name.removeSuffix(".xml") }
                ?.filter { it == APP_PREFS || it.startsWith("source_") }
                .orEmpty()
        }.getOrDefault(emptyList())
        // APP_PREFS may not have been flushed to disk yet on a fresh install.
        return if (APP_PREFS in found) found else found + APP_PREFS
    }
}

/** Off, or a period WorkManager will accept (its floor is 15 minutes). */
internal enum class BackupFrequency(val hours: Int, val label: String) {
    OFF(0, "Off"),
    SIX_HOURS(6, "Every 6 hours"),
    TWELVE_HOURS(12, "Every 12 hours"),
    DAILY(24, "Daily"),
    WEEKLY(168, "Weekly");

    companion object {
        fun from(hours: Int) = entries.firstOrNull { it.hours == hours } ?: OFF
    }
}

/**
 * Public and top-level on purpose: WorkManager instantiates workers reflectively
 * by class name, so this can't be nested inside [Backup] or made private.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        // Success either way, deliberately. A false from runAutomatic means no
        // folder is set, or the SAF grant was revoked, or the card is gone —
        // none of which get better by retrying in ten minutes, and a retry loop
        // against a revoked grant is battery spent on nothing. Reporting failure
        // for a periodic request also puts the schedule itself at the mercy of
        // WorkManager's policy for failed periodic work, which is a strange
        // thing to bet a backup schedule on. This skips the occurrence and waits
        // for the next period; the settings screen shows when the last one
        // actually landed, so a folder that stopped working is visible there.
        Backup.runAutomatic(applicationContext)
        ListenableWorker.Result.success()
    }
}
