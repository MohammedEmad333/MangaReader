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

/**
 * Progress of a library refresh, for anything on screen that wants to show it.
 *
 * Process-wide Compose snapshot state, exactly like `DownloadQueue`: the service
 * writes to it from a background thread and any composable reading it
 * recomposes. Both are in the same process, so there is no flow, binder or
 * broadcast anywhere in this path.
 *
 * None of *this* is persisted — it is progress for a running sweep and means
 * nothing once one isn't. What survives a stop is one long in [RefreshCursor]
 * plus the `sweptAt` stamps in `SeriesIndex`, which together are the resume
 * point; there is still no JSON mirror of a queue like `DownloadQueue` carries.
 *
 * The original note here said losing a sweep cost nothing because what it had
 * written was still correct. Correct, and still the wrong conclusion: the
 * expensive thing a sweep spends is not consistency but *requests*, one per
 * series, paced. A 3567-series library is tens of minutes of them, so a stop
 * that discarded the position capped how far the index could ever get at the
 * length of the longest uninterrupted run.
 */
object LibraryRefresh {
    var running by mutableStateOf(false)
        internal set

    var total by mutableIntStateOf(0)
        internal set

    /** Series attempted so far, whether they succeeded or not. */
    var done by mutableIntStateOf(0)
        internal set

    /**
     * Series whose chapter list was fetched and counted.
     *
     * Not "whose counts moved" — the sweep can't know that, because whether a
     * candidate differs from what's stored is decided later, inside
     * [SeriesIndex.recordAll], against an index the next flush may have already
     * changed. The label this feeds says "counted" for the same reason.
     */
    var counted by mutableIntStateOf(0)
        internal set

    /** Series a resumed sweep inherited as already done. Also seeds [done]. */
    var resumed by mutableIntStateOf(0)
        internal set

    var failed by mutableIntStateOf(0)
        internal set

    /** Series whose source is no longer installed. Not a failure, just absent. */
    var skipped by mutableIntStateOf(0)
        internal set

    var currentTitle by mutableStateOf("")
        internal set

    /**
     * The first failure, kept rather than the last.
     *
     * With a sweep this size the last error is whatever happened to finish
     * nearest the end, which is noise. The first one is usually the cause and
     * the rest are its neighbours — the same reasoning `loadPagesProgressively`
     * applies to a batch of pages where one 429 takes the others with it.
     */
    var firstError by mutableStateOf<String?>(null)
        internal set

    var finishedAt by mutableStateOf(0L)
        internal set

    internal fun begin(count: Int, alreadyDone: Int = 0) {
        running = true
        total = count
        // Seeded, not zeroed. A resume that showed "0 of 3567" would read as
        // having lost the previous run's work, which is the bug this fixes.
        done = alreadyDone
        resumed = alreadyDone
        counted = 0
        failed = 0
        skipped = 0
        currentTitle = ""
        firstError = null
        finishedAt = 0L
    }

    internal fun end() {
        running = false
        currentTitle = ""
        finishedAt = System.currentTimeMillis()
    }

    internal fun noteFailure(message: String?) {
        failed++
        if (firstError == null) firstError = message ?: "Unknown error"
    }

    /** Everything zeroed, so a dismissed summary doesn't come back. */
    fun clearSummary() {
        if (running) return
        total = 0
        done = 0
        counted = 0
        resumed = 0
        failed = 0
        skipped = 0
        firstError = null
        finishedAt = 0L
    }
}

/**
 * The one persisted long that makes a stopped refresh resumable.
 *
 * Holds the start time of the sweep in progress, or 0 when there isn't one.
 * Every series a sweep counts gets stamped with `SeriesCounts.sweptAt`, so
 * "what's left" is a comparison against this timestamp rather than a stored
 * list of thousands of ids — no second store, no batching, no invalidation
 * policy of its own.
 *
 * **Why not an offset into the entry list.** The library is re-read and
 * re-grouped from scratch on every start, and adding or removing a single series
 * shifts every position after it. A cursor of "resume at 812" would silently
 * mean a different 812 series each time.
 *
 * Cleared when a sweep reaches the end. Deliberately left in place by a stop, a
 * crash or a process kill — which is exactly when it is worth something.
 */
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
class LibraryRefreshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    /** Set only when a sweep actually reached the end. Gates clearing the cursor. */
    private var completed = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyAt = 0L

    /** Counts waiting to be flushed. Written from several source coroutines. */
    private val pending = HashMap<String, SeriesCounts>()
    private val pendingLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Within a few seconds of startForegroundService() or the system kills
        // the process outright, so this goes before any work.
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            val wasRunning = worker?.isActive == true
            worker?.cancel()
            // With nothing running, the worker's `finally` — which is what
            // normally drops the notification and stops the service — will never
            // run. goForeground() above has just promoted us, so without this a
            // stray Stop leaves an ongoing notification with nothing behind it.
            if (!wasRunning) stopEverything()
            return START_NOT_STICKY
        }

        ensureWorker()
        // Still not sticky, unlike DownloadService, but for a different reason
        // than before: a refresh the *system* restarts after a process kill is
        // work the user never asked for. Now that a resume point survives, they
        // can pick it up themselves from Settings, which is where the decision
        // belongs.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        completed = false
        worker = scope.launch {
            acquireWakeLock()
            try {
                sweep()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryRefresh.noteFailure(e.message ?: e.javaClass.simpleName)
            } finally {
                // Whatever is still in hand goes in, cancelled or not: these are
                // counts already fetched, and throwing them away would mean a
                // stopped refresh had done nothing but spend the requests.
                flush()
                // Only a sweep that finished gives up its resume point.
                if (completed) RefreshCursor.clear(this@LibraryRefreshService)
                LibraryRefresh.end()
                releaseWakeLock()
                stopEverything()
            }
        }
    }

    private suspend fun sweep() {
        val entries = Library.list(this)
        if (entries.isEmpty()) {
            LibraryRefresh.begin(0)
            RefreshCursor.clear(this)
            completed = true
            return
        }

        // Resumes the sweep in progress, or opens a new one. Everything below
        // hangs off this timestamp.
        val startedAt = RefreshCursor.beginOrResume(this)
        // Read once. Not `SeriesIndex.of` per series: the memo is keyed on the
        // raw pref string and every flush below writes a new one, so a lookup
        // in the loop would re-parse the whole index once per flush.
        val alreadySwept = SeriesIndex.sweptSince(this, startedAt)
        val remaining = entries.filterNot { it.seriesId in alreadySwept }

        // `total` stays the whole library and `done` starts where the last run
        // stopped, so a resume reads as "812 of 3567" rather than restarting a
        // progress bar the user has already watched fill once.
        LibraryRefresh.begin(entries.size, alreadyDone = entries.size - remaining.size)
        if (remaining.isEmpty()) {
            completed = true
            return
        }

        // Classloads the extension APKs, so once, up front, not per series.
        val sources = SourceManager.listAllSources(this).associateBy { it.id }
        val bySource = remaining.groupBy { it.sourceId }

        notifyNow()

        // The same chunked-async shape runGlobalSearch uses, rather than a
        // Semaphore — it's already proven in this codebase and needs no API this
        // module hasn't used before. A chunk waits for its slowest source, which
        // costs nothing here: one source usually holds most of the library and
        // dominates the total either way.
        coroutineScope {
            bySource.entries.chunked(SOURCE_CONCURRENCY).forEach { batch ->
                batch.map { (sourceId, list) ->
                    async { refreshSource(sources[sourceId], list, startedAt) }
                }.awaitAll()
            }
        }

        // Not simply `true` here. refreshSource returns early on cancellation
        // rather than throwing, so a stop can walk out of the block above
        // without an exception — and clearing the cursor then would discard the
        // resume point at the exact moment it becomes the thing worth keeping.
        completed = currentCoroutineIsActive()
    }

    /**
     * One source's series, in order, with a gap between requests.
     *
     * Sequential on purpose. Chapter-list fetches are one request each, so the
     * gain from running two at once on the same host is small and the risk is
     * the failure mode §5 spent five attempts on: a CDN that starts refusing
     * once a single connection has carried enough requests.
     */
    private suspend fun refreshSource(
        src: Source?,
        entries: List<LibraryEntry>,
        startedAt: Long
    ) {
        for (entry in entries) {
            if (!currentCoroutineIsActive()) return

            if (src == null) {
                // The extension has been uninstalled. Not a failure — there is
                // nothing wrong and nothing to fix — so it is counted apart from
                // one, or a phone missing one extension would report hundreds of
                // errors and bury a real one.
                LibraryRefresh.skipped++
                LibraryRefresh.done++
                continue
            }

            LibraryRefresh.currentTitle = entry.title
            try {
                // restoreSeries, not getSeries: it builds the url + title pair a
                // stored entry is and makes no network call, so this is one
                // request per series rather than two. The details endpoint is
                // exactly what this doesn't need — no metadata is being shown.
                val series = src.restoreSeries(entry.seriesId, entry.title)
                    ?: throw IllegalStateException("Couldn't rebuild the series")
                val chapters = src.listChapters(series)
                if (chapters.isNotEmpty()) {
                    // Free, and worth having: this is the offline chapter list,
                    // and the sweep has just fetched a newer one than whatever
                    // was stored.
                    ChapterCache.save(this, entry.seriesId, chapters)
                    // Stamped with the sweep's start, not with `now`: this is
                    // what marks the series as belonging to *this* sweep, and a
                    // resume tests `>= startedAt`.
                    val counts = SeriesIndex.countsFor(
                        this, entry.sourceId, chapters, sweptAt = startedAt
                    )
                    if (counts != null) {
                        synchronized(pendingLock) { pending[entry.seriesId] = counts }
                        LibraryRefresh.counted++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryRefresh.noteFailure(
                    "${entry.title} — ${e.message ?: e.javaClass.simpleName}"
                )
            }

            LibraryRefresh.done++
            notifyThrottled()
            if (pendingSize() >= FLUSH_EVERY) flush()
            delay(REQUEST_SPACING_MS)
        }
    }

    private fun pendingSize(): Int = synchronized(pendingLock) { pending.size }

    /** One write for up to [FLUSH_EVERY] series. See [SeriesIndex.recordAll]. */
    private fun flush() {
        val batch = synchronized(pendingLock) {
            if (pending.isEmpty()) return
            HashMap(pending).also { pending.clear() }
        }
        runCatching { SeriesIndex.recordAll(this, batch) }
    }

    /**
     * `isActive` off the service's own job.
     *
     * Read through a helper because this is called from a `for` loop inside a
     * suspend function rather than from a coroutine builder, where the receiver
     * isn't in scope.
     */
    private fun currentCoroutineIsActive(): Boolean = worker?.isActive != false

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- wake lock ----------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    // ---------- notification ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Library refresh",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress while chapter counts are updated"
                setShowBadge(false)
            }
        )
    }

    private fun goForeground(): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }.isSuccess

    private fun notifyThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        notifyNow()
    }

    private fun notifyNow() {
        lastNotifyAt = System.currentTimeMillis()
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val total = LibraryRefresh.total
        val done = LibraryRefresh.done

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Refreshing library")
            .setContentText(
                if (total == 0) "Starting" else "$done of $total"
            )
            .setSubText(LibraryRefresh.currentTitle.ifBlank { null })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Indeterminate until the count is known, or the bar sits at 100%
            // for the moment before the first series lands.
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .addAction(
                0,
                "Stop",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, LibraryRefreshService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "library_refresh"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_TAG = "Yomu:refresh"
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
        private const val NOTIFY_INTERVAL_MS = 500L

        /** Sources worked on at once. Deliberately small; see the class note. */
        private const val SOURCE_CONCURRENCY = 3

        /** Between two requests to the same source. */
        private const val REQUEST_SPACING_MS = 250L

        /**
         * Series per index write.
         *
         * Every write serialises the whole index, so this trades write count
         * against how much a kill loses: a hundred means about 36 writes over a
         * 3567-series library and at most a hundred series' counts unsaved,
         * which the next refresh picks up anyway.
         */
        private const val FLUSH_EVERY = 100

        const val ACTION_STOP = "com.mangareader.app.REFRESH_STOP"

        /**
         * Starts a refresh.
         *
         * Only ever called from a user action in a visible Activity, which is
         * what keeps the foreground-service start legal on Android 12+.
         */
        fun start(context: Context) {
            // Cleared here rather than left to begin(), which doesn't run until the
            // worker has read the library and the swept set — a several-thousand
            // entry parse each. Until it does, `running` is still false and the
            // Settings row is showing the *last* sweep's summary beside a refresh
            // that has already started.
            LibraryRefresh.clearSummary()
            val intent = Intent(context, LibraryRefreshService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * Throws the resume point away, then starts.
         *
         * Kept separate from [start] because "refresh again from the top" is a
         * real request — a sweep that finished a week ago is stale — and after
         * this change a plain start would honour a stale cursor instead.
         */
        fun startOver(context: Context) {
            RefreshCursor.clear(context)
            start(context)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LibraryRefreshService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
