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
