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
/**
 * The tally key for a failure that belongs to no source.
 *
 * Source ids are `"tachi:<n>"` or a local uuid, so the parentheses make this
 * unmistakable in the failure list and impossible to collide with.
 */
internal const val SWEEP_ITSELF = "(the sweep itself)"

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
     * What this sweep is limited to, or blank for the whole library.
     *
     * Shown beside the progress, because "812 of 900" is otherwise
     * indistinguishable from a full sweep on a library that shrank.
     */
    var scopeLabel by mutableStateOf("")
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

    /**
     * Failures per source id.
     *
     * [firstError] keeps one string, deliberately and for a good reason, but one
     * string cannot answer "which sources are failing" — and that is the
     * question the end-of-sweep arithmetic exists to raise. Bug 6's fix turns
     * 547 unaccounted series into a *count* of failures; without a tally the
     * next step is still opening sources by hand to find the broken one, which
     * is what the sweep was supposed to replace.
     *
     * A `ConcurrentHashMap` of `AtomicInteger` rather than the pattern the
     * counters use, because `failed++` from up to `SOURCE_CONCURRENCY`
     * coroutines is exactly bug 3 and loses increments. **This does not fix bug
     * 3** — [failed] is untouched and still races. It means the *tally* doesn't,
     * so if the two disagree at the end of a sweep, the tally is the one to
     * believe and the size of the disagreement is a free measurement of bug 3.
     */
    private val failureTally = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Covers rewritten by the current sweep.
     *
     * An `AtomicInteger` rather than a plain `var` like [counted] and its
     * neighbours, because this is incremented from up to `SOURCE_CONCURRENCY`
     * coroutines at once and `n++` from three of them is exactly bug 3. Cheap
     * to get right here because nothing existing depends on it.
     *
     * Persisted into the saved summary alongside the other counters. An earlier
     * revision left it out, reasoning that a restored summary describes a sweep
     * that has already ended — which got it exactly backwards. A *stopped* sweep
     * is when this number is most wanted and least recoverable: it is
     * process-lifetime state, and the run that produced it is over.
     */
    internal val coversRepaired = AtomicInteger(0)

    /** A snapshot of [failureTally], worst first. Safe to call at any time. */
    fun failureCounts(): List<Pair<String, Int>> =
        failureTally.entries
            .map { it.key to it.value.get() }
            .sortedByDescending { it.second }

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
        failureTally.clear()
        coversRepaired.set(0)
    }

    internal fun end(context: Context) {
        running = false
        currentTitle = ""
        finishedAt = System.currentTimeMillis()
        saveSummary(context)
    }

    internal fun noteFailure(sourceId: String, message: String?) {
        failed++
        failureTally.computeIfAbsent(sourceId) { _ -> AtomicInteger(0) }.incrementAndGet()
        if (firstError == null) firstError = message ?: "Unknown error"
    }

    /** Everything zeroed, so a dismissed summary doesn't come back. */
    fun clearSummary(context: Context) {
        if (running) return
        total = 0
        done = 0
        counted = 0
        resumed = 0
        failed = 0
        skipped = 0
        firstError = null
        finishedAt = 0L
        failureTally.clear()
        coversRepaired.set(0)
        scopeLabel = ""
        loaded = true
        runCatching { prefs(context).edit().remove(SUMMARY_KEY).apply() }
    }

    // ---------- persistence ----------

    /**
     * Where the finished summary lives between processes.
     *
     * One key holding one JSON object, not eight keys. The prefs file is 2.5 MB
     * across several thousand entries and what makes it slow to load is the
     * entry count rather than the byte size
     * (`SESSION_HANDOFF_0.71_RESULT.md` §4), so adding eight entries to save six
     * numbers would be paying in the currency that is actually scarce.
     */
    private const val SUMMARY_KEY = "refresh_last_summary"

    @Volatile
    private var loaded = false

    /**
     * Restores the last finished sweep's numbers, once per process.
     *
     * Called from the Settings screen rather than from `onCreate`, because that
     * is the only screen that shows them and the startup path is not somewhere
     * to add a read that nothing on the first frame needs.
     *
     * Why persist at all: every counter here is Compose state with nothing
     * behind it, so installing a build — or anything else that ends the process
     * — destroyed the summary. That is how sweep 1's numbers were lost and how
     * sweep 2's were nearly lost, and those numbers are the whole input to the
     * `counted + failed + skipped == total - resumed` check that bugs 3 and 6
     * announce themselves through.
     */
    fun loadSummary(context: Context) {
        if (loaded || running) return
        loaded = true
        runCatching {
            val raw = prefs(context).getString(SUMMARY_KEY, null) ?: return
            val o = JSONObject(raw)
            total = o.optInt("total")
            done = o.optInt("done")
            counted = o.optInt("counted")
            resumed = o.optInt("resumed")
            failed = o.optInt("failed")
            skipped = o.optInt("skipped")
            finishedAt = o.optLong("finishedAt")
            coversRepaired.set(o.optInt("coversRepaired"))
            firstError = if (o.isNull("firstError")) null else o.optString("firstError")
            o.optJSONObject("failures")?.let { f ->
                failureTally.clear()
                for (key in f.keys()) failureTally[key] = AtomicInteger(f.optInt(key))
            }
        }
    }

    private fun saveSummary(context: Context) {
        loaded = true
        runCatching {
            val failures = JSONObject()
            for ((key, value) in failureTally) failures.put(key, value.get())
            val o = JSONObject()
                .put("total", total)
                .put("done", done)
                .put("counted", counted)
                .put("resumed", resumed)
                .put("failed", failed)
                .put("skipped", skipped)
                .put("finishedAt", finishedAt)
                .put("firstError", firstError ?: JSONObject.NULL)
                .put("coversRepaired", coversRepaired.get())
                .put("failures", failures)
            prefs(context).edit().putString(SUMMARY_KEY, o.toString()).apply()
        }
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

    /** Same shape and same exposure as DownloadService's — see the note there. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, t ->
                LibraryRefresh.noteFailure(SWEEP_ITSELF, t.javaClass.simpleName)
                runCatching { stopEverything() }
            }
    )
    private var worker: Job? = null

    /** Set only when a sweep actually reached the end. Gates clearing the cursor. */
    private var completed = false

    /**
     * Source ids this run is limited to, or null for the whole library.
     *
     * **A targeted run deliberately does not touch [RefreshCursor].** The cursor
     * records only a timestamp, not a filter, so a stopped targeted sweep and a
     * stopped full one would be indistinguishable to Resume — it would offer to
     * carry on with a scope it cannot know. A targeted run is short by
     * construction, so the answer to interrupting one is to run it again, and
     * the full sweep's resume machinery stays exactly as it was.
     */
    private var scopeIds: Set<String>? = null

    /**
     * Limits the run to series the index has no counts for.
     *
     * The case this exists for: a sweep *skips* every series whose extension
     * isn't installed — 179 of them on the library this was built against — and
     * a skipped series is deliberately never stamped, so it stays un-counted.
     * Install the missing extensions and the only way to pick those up was a
     * full sweep, three hours to fetch two hundred series.
     *
     * Un-counted also catches the ones that failed, which is the same question
     * asked a different way: what does the index still not know?
     */
    private var scopeUncounted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyAt = 0L

    /** Counts waiting to be flushed. Written from several source coroutines. */
    private val pending = HashMap<String, SeriesCounts>()
    private val pendingLock = Any()

    /**
     * Fresh covers waiting to be written, keyed by series id.
     *
     * Held and flushed exactly like [pending], for the same reason: `Library` is
     * a single JSON string, so writing one cover per series over a sweep would
     * rewrite several thousand entries several thousand times.
     */
    private val pendingCovers = HashMap<String, String>()
    private val coverLock = Any()

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

        // Before ensureWorker: the worker reads this on its first line, and a
        // second start() while one is already running must not silently change
        // the scope underneath it.
        if (worker?.isActive != true) {
            scopeIds = intent?.getStringArrayListExtra(EXTRA_SOURCES)
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
            scopeUncounted = intent?.getBooleanExtra(EXTRA_UNCOUNTED, false) == true
            LibraryRefresh.scopeLabel = intent?.getStringExtra(EXTRA_SCOPE_LABEL).orEmpty()
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
            } catch (e: Throwable) {
                // Not attributable to a source: this is the sweep itself
                // falling over, not one extension misbehaving. Filed under a
                // name no sourceId can collide with so it can't be mistaken for
                // a badly-behaved source in the tally.
                LibraryRefresh.noteFailure(SWEEP_ITSELF, e.message ?: e.javaClass.simpleName)
            } finally {
                // Whatever is still in hand goes in, cancelled or not: these are
                // counts already fetched, and throwing them away would mean a
                // stopped refresh had done nothing but spend the requests.
                flush()
                // Only a *full* sweep that finished gives up its resume point.
                // A targeted run never owned it, and clearing it here would
                // silently discard a half-finished full sweep's progress.
                if (completed && ownsCursor) {
                    RefreshCursor.clear(this@LibraryRefreshService)
                }
                LibraryRefresh.end(this@LibraryRefreshService)
                releaseWakeLock()
                stopEverything()
            }
        }
    }

    /** True when this run may touch the shared resume point. See [scopeIds]. */
    private val ownsCursor: Boolean get() = scopeIds == null && !scopeUncounted

    private suspend fun sweep() {
        val scope = scopeIds
        val entries = Library.list(this)
            .let { all -> if (scope == null) all else all.filter { it.sourceId in scope } }
            .let { picked ->
                if (!scopeUncounted) picked else {
                    // Read once, outside the filter. `all()` is memoised on the
                    // raw pref string and every flush below writes a new one, so
                    // asking per series would re-parse the index thousands of
                    // times — the same trap `alreadySwept` avoids just below.
                    val known = SeriesIndex.all(this).keys
                    picked.filterNot { it.seriesId in known }
                }
            }
        if (entries.isEmpty()) {
            LibraryRefresh.begin(0)
            if (ownsCursor) RefreshCursor.clear(this)
            completed = true
            return
        }

        // Resumes the sweep in progress, or opens a new one. Everything below
        // hangs off this timestamp.
        //
        // A targeted run takes a timestamp without recording it: it still stamps
        // the series it counts, so a later full sweep correctly treats them as
        // already done, but it leaves no resume point of its own to be confused
        // with a full sweep's.
        val startedAt =
            if (scope == null) RefreshCursor.beginOrResume(this)
            else System.currentTimeMillis()
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
        // Read once for the whole pass, not per entry. The set only shrinks, and
        // only when this sweep writes a repaired cover, so a stale read here can
        // at worst repeat one repair — while re-reading it 3571 times would be
        // the per-row cost §5 keeps finding.
        val reportedCovers = CoverRepair.reported(this)

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

                // The cover, but only where the stored one is known to be
                // wrong. This is a *second* request for the series, which is
                // exactly what the comment above says the sweep avoids — so it
                // is spent on the entries that need it rather than all 3571.
                // An earlier plan had this coming free from the SManga already
                // in hand; it doesn't, because restoreSeries never fetched one.
                if (CoverRepair.needsRepair(entry, reportedCovers)) {
                    // Caught separately from the counts above. A cover repair
                    // that fails says nothing about whether the chapter list
                    // landed, and letting it fall into the handler below would
                    // record a failure against a series that was counted
                    // perfectly — inflating the one tally whose job is to point
                    // at sources that are genuinely broken.
                    try {
                        val fresh = (src.loadDetails(series).cover as? String)
                            ?.takeIf { it.isNotBlank() && !isLoopback(it) }
                        if (fresh != null && fresh != entry.cover) {
                            synchronized(coverLock) { pendingCovers[entry.seriesId] = fresh }
                            LibraryRefresh.coversRepaired.incrementAndGet()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // Stays on the repair list, so the next sweep retries it.
                    }
                    // A second request to the same host inside one iteration, so
                    // it gets its own spacing. Per-host request volume is the
                    // variable the manhwatoon 400s turned on, and a repair pass
                    // is not a reason to halve the gap between requests.
                    delay(REQUEST_SPACING_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: a sweep calls listChapters on every installed
                // source, so one extension built against a newer API would
                // otherwise take the whole sweep — and the app — down partway.
                LibraryRefresh.noteFailure(
                    entry.sourceId,
                    "${entry.title} — ${e.message ?: e.javaClass.simpleName}"
                )
            }

            LibraryRefresh.done++
            notifyThrottled()
            if (pendingSize() >= FLUSH_EVERY || pendingCoverSize() >= FLUSH_EVERY) flush()
            delay(REQUEST_SPACING_MS)
        }
    }

    private fun pendingSize(): Int = synchronized(pendingLock) { pending.size }

    private fun pendingCoverSize(): Int = synchronized(coverLock) { pendingCovers.size }

    /**
     * One write for up to [FLUSH_EVERY] series, per store.
     *
     * Two stores now, and neither may return early on behalf of the other — an
     * `if (empty) return` inside the first block would strand a batch of covers
     * whenever the counts happened to be empty, which is every sweep of a
     * library that has already been counted once.
     */
    private fun flush() {
        val batch = synchronized(pendingLock) {
            if (pending.isEmpty()) null else HashMap(pending).also { pending.clear() }
        }
        if (batch != null) runCatching { SeriesIndex.recordAll(this, batch) }

        val covers = synchronized(coverLock) {
            if (pendingCovers.isEmpty()) null
            else HashMap(pendingCovers).also { pendingCovers.clear() }
        }
        if (covers != null) {
            runCatching {
                Library.setCovers(this, covers)
                // Cleared only after the write lands. A repair that failed to
                // save has to stay on the list, or the entry is never asked
                // about again until it next fails to draw.
                CoverRepair.clear(this, covers.keys)
            }
        }
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
        const val EXTRA_SOURCES = "com.mangareader.app.REFRESH_SOURCES"
        const val EXTRA_SCOPE_LABEL = "com.mangareader.app.REFRESH_SCOPE"
        const val EXTRA_UNCOUNTED = "com.mangareader.app.REFRESH_UNCOUNTED"

        /**
         * Starts a refresh.
         *
         * Only ever called from a user action in a visible Activity, which is
         * what keeps the foreground-service start legal on Android 12+.
         */
        /** Every source in the library. */
        fun start(context: Context) = start(context, null, "")

        /**
         * Refreshes only [sourceIds].
         *
         * Useful the moment the summary names a source that failed repeatedly:
         * re-running the whole library to retry twelve series is most of an
         * hour. This does not resume and cannot be resumed — see
         * `LibraryRefreshService.scopeIds`.
         */
        /**
         * Refreshes only series the index has no counts for.
         *
         * What to reach for after installing an extension the library already
         * had entries from: those were skipped, never stamped, and a full sweep
         * is the wrong size of hammer.
         */
        fun startUncounted(context: Context, count: Int) {
            start(context, null, "$count never counted", uncounted = true)
        }

        fun start(
            context: Context,
            sourceIds: Set<String>?,
            scopeLabel: String,
            uncounted: Boolean = false
        ) {
            // Cleared here rather than left to begin(), which doesn't run until the
            // worker has read the library and the swept set — a several-thousand
            // entry parse each. Until it does, `running` is still false and the
            // Settings row is showing the *last* sweep's summary beside a refresh
            // that has already started.
            LibraryRefresh.clearSummary(context)
            val intent = Intent(context, LibraryRefreshService::class.java)
            if (!sourceIds.isNullOrEmpty()) {
                intent.putStringArrayListExtra(EXTRA_SOURCES, ArrayList(sourceIds))
            }
            if (uncounted) intent.putExtra(EXTRA_UNCOUNTED, true)
            if (!sourceIds.isNullOrEmpty() || uncounted) {
                intent.putExtra(EXTRA_SCOPE_LABEL, scopeLabel)
            }
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
