package com.mangareader.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private val foreground by lazy { LibraryRefreshForeground(this) }

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
        foreground.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Within a few seconds of startForegroundService() or the system kills
        // the process outright, so this goes before any work.
        if (!foreground.goForeground()) {
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
        foreground.releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        completed = false
        worker = scope.launch {
            foreground.acquireWakeLock()
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
                foreground.releaseWakeLock()
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
        // `ownsCursor`, not `scope == null`. 0.99 added a second kind of scoped
        // run — un-counted-only — which leaves `scopeIds` null, so this line
        // handed it the shared cursor while `ownsCursor` correctly refused to
        // let it clear one. The result was a resume point for a full sweep that
        // never ran, left behind permanently.
        //
        // Two predicates for one question is the bug. There is now one.
        val startedAt =
            if (ownsCursor) RefreshCursor.beginOrResume(this)
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

        foreground.notifyNow()

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
            foreground.notifyThrottled()
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

    companion object {

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
