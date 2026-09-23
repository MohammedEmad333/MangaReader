package com.mangareader.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object RefreshCursor {
    private const val KEY = "refresh_sweep_started_at"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    fun startedAt(context: Context): Long = prefs(context).getLong(KEY, 0L)

    /** The sweep already in progress if there is one, otherwise a fresh one. */
    fun beginOrResume(context: Context): Long {
        val existing = startedAt(context)
        if (existing > 0L) return existing
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY, now).apply()
        return now
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}

/**
 * Fetches a chapter list for every series in the library and updates the index.
 *
 * **Why this exists.** `SeriesIndex` is written when a series screen resolves
 * its chapter list, which is the only moment the app holds one — so it knows
 * about series that have been opened and nothing else. Every count, badge,
 * filter and sort it feeds was therefore answering for a minority of the
 * library. This is the thing that makes them answer for all of it.
 *
 * **Why a foreground service.** Same reason as `DownloadService`: this is long,
 * user-visible, network-bound work that has to survive the app being
 * backgrounded. A sweep over several thousand series is minutes, not seconds,
 * and running it in `YomuApp`'s scope would tie it to the Activity — which this
 * app cannot even survive a recreation of.
 *
 * **What it is careful about**, all of it from §5 of the handoff:
 *
 * - **It is an import-scale operation.** Every per-series write in this app
 *   rewrites a whole JSON store, so the counts are accumulated in memory and
 *   flushed through [SeriesIndex.recordAll] in batches. Calling
 *   `SeriesIndex.record` in this loop would be quadratic and would take longer
 *   than the network does.
 * - **It fans out across sources, so it paces itself per source.** Sources are
 *   processed a few at a time and each source's own series go through
 *   sequentially with a gap between requests. The manhwatoon 400s were
 *   per-connection request limits, and the lesson there was that hammering one
 *   host is the variable that matters — not total throughput.
 * - **A per-series failure is recorded, not fatal.** One dead source must not
 *   end the sweep for the other ninety-four.
 * - **A stop is a pause, not a discard.** The sweep's start time is kept in
 *   [RefreshCursor] and every counted series is stamped with it, so starting
 *   again picks up the series that stamp hasn't reached instead of re-spending
 *   thousands of requests on the ones it has. The stamp was not free: see
 *   `SeriesCounts.sweptAt` for why the existing `updatedAt` could not answer
 *   this question.
 * - **It never blocks reading.** It writes the index and `ChapterCache`, both of
 *   which any screen is free to read at any point; a half-finished sweep is a
 *   library with some counts newer than others, which is what the index is
 *   anyway.
 */
