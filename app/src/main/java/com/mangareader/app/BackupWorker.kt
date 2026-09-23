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
