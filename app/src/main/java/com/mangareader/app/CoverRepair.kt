package com.mangareader.app

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Which library covers are wrong, and how each one came to be known.
 *
 * `LibraryEntry` caches a cover string at add time so the grid renders instantly
 * and offline. Nothing ever re-asks, so the string rots in two different ways,
 * and the two are not equally cheap to find:
 *
 * - **Absent.** Blank, or pointing at a loopback address a self-hosted source
 *   handed out on somebody else's device. Both are visible in the stored data
 *   with no network at all, so finding these costs nothing.
 * - **Stale.** Present, well-formed, and 404 — the source moved or re-encoded
 *   the file, which a content hash in the filename makes routine. Nothing in the
 *   stored string says so. The only cheap way to know is to notice the failure
 *   at the moment the grid tries to draw it, which is what [report] is for.
 *
 * `Library.healCover` covers neither case in bulk: it fires once per series, on
 * open, and only on the absent kind. That is the whole of both board items —
 * a 3571-entry library repaired one tap at a time.
 *
 * The repair itself belongs to the library refresh sweep, which is the only
 * thing in this app that walks the whole library on a schedule the user chose.
 * This object is just the list of what to fix when it gets there.
 */
internal const val COVER_REPAIR_PREFS_KEY = "cover_repair_ids"

object CoverRepair {
    private const val LIMIT = 2000
    private const val PERSIST_BATCH_SIZE = 32
    private const val PERSIST_DELAY_MS = 350L

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    // UI image failures update this state synchronously, but never serialize or
    // write the growing SharedPreferences set from the callback.
    private val lock = Any()
    private var cached: CoverRepairState? = null
    private var changesSincePersist = 0
    private var pendingWrite: ScheduledFuture<*>? = null
    private var pendingWriteImmediate = false

    // A single daemon writer keeps preference commits ordered and off the UI
    // thread. It is process-scoped, like the cached repair set.
    private val writer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "cover-repair-persist").apply { isDaemon = true }
    }

    private fun stateLocked(context: Context): CoverRepairState =
        cached ?: CoverRepairState(
            initialIds = prefs(context)
                .getStringSet(COVER_REPAIR_PREFS_KEY, emptySet())
                .orEmpty(),
            limit = LIMIT,
        ).also { cached = it }

    fun reported(context: Context): Set<String> =
        synchronized(lock) { stateLocked(context).snapshot() }

    /**
     * Records that [seriesId]'s stored cover is gone.
     *
     * Called from a composable's error callback. The in-memory set is updated
     * immediately, while disk persistence is coalesced: at most one pending
     * timer, with an early flush every [PERSIST_BATCH_SIZE] mutations. This
     * avoids copying and rewriting the entire set once per failed cover.
     */
    fun report(context: Context, seriesId: String) {
        if (seriesId.isBlank()) return
        synchronized(lock) {
            if (!stateLocked(context).report(seriesId)) return
            changesSincePersist++
            schedulePersistLocked(context.applicationContext)
        }
    }

    /** Drops ids whose covers have been rewritten. */
    fun clear(context: Context, seriesIds: Set<String>) {
        if (seriesIds.isEmpty()) return
        synchronized(lock) {
            if (!stateLocked(context).clear(seriesIds)) return
            changesSincePersist++
            schedulePersistLocked(context.applicationContext)
        }
    }

    /**
     * Schedule one bounded batch. Small bursts are written after the first
     * pending change's short window; sustained bursts flush every 32 mutations.
     */
    private fun schedulePersistLocked(context: Context) {
        val immediate = changesSincePersist >= PERSIST_BATCH_SIZE
        val pending = pendingWrite
        if (pending != null && !pending.isDone) {
            if (!immediate || pendingWriteImmediate) return
            pending.cancel(false)
        }

        pendingWriteImmediate = immediate
        val delay = if (immediate) 0L else PERSIST_DELAY_MS
        pendingWrite = writer.schedule(
            { persist(context) },
            delay,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun persist(context: Context) {
        val snapshot = synchronized(lock) {
            val current = cached?.snapshot() ?: emptySet()
            pendingWrite = null
            pendingWriteImmediate = false
            changesSincePersist = 0
            current
        }

        // commit() is safe here because this is the dedicated background
        // writer. On failure, mark the set dirty and retry through the same
        // batching path rather than losing the repair hints.
        val saved = runCatching {
            prefs(context).edit()
                .putStringSet(COVER_REPAIR_PREFS_KEY, snapshot)
                .commit()
        }.getOrDefault(false)
        if (!saved) {
            synchronized(lock) {
                changesSincePersist = maxOf(changesSincePersist, 1)
                schedulePersistLocked(context)
            }
        }
    }

    /**
     * Whether this entry's cover is worth spending a request on.
     *
     * [reported] is passed in rather than read here because this is asked once
     * per series across a whole sweep, and the answer cannot change while one is
     * running — the sweep is the only thing that clears the set.
     */
    fun needsRepair(entry: LibraryEntry, reported: Set<String>): Boolean =
        entry.cover.isBlank() ||
            isLoopback(entry.cover) ||
            entry.seriesId in reported
}
