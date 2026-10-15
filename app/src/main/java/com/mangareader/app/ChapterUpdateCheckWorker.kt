package com.mangareader.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Short, bounded checks avoid starting a foreground service from background work.
 * Each scheduled run advances a persisted cursor through the library.
 */
class ChapterUpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (ChapterUpdateSchedulePrefs.frequency(context) == ChapterUpdateFrequency.OFF)
            return@withContext ListenableWorker.Result.success()
        if (LibraryRefresh.running) return@withContext ListenableWorker.Result.success()
        val library = Library.list(context)
        if (library.isEmpty()) return@withContext ListenableWorker.Result.success()
        val prefs = context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)
        val offset = prefs.getInt("chapter_update_check_cursor", 0).coerceAtLeast(0) % library.size
        val batch = scheduledUpdateBatch(library, offset, 12)
        // Discovery may fail before any series is attempted. Retry instead of
        // silently losing the entire scheduled checking window.
        val sources = try {
            SourceManager.listAllSources(context).associateBy { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (shouldRetryUpdateSourceDiscovery(runAttemptCount)) {
                return@withContext ListenableWorker.Result.retry()
            }
            ChapterUpdateCheckSummaryStore.save(
                context,
                ChapterUpdateCheckSummary(
                    completedAt = System.currentTimeMillis(),
                    checked = 0,
                    failed = 0,
                    unavailable = 0,
                    sourceDiscoveryFailed = true,
                ),
            )
            return@withContext ListenableWorker.Result.failure()
        }
        var newChapters = 0
        var checked = 0
        var failed = 0
        var unavailable = 0
        var completedEntries = 0
        for (entry in batch) {
            if (!shouldContinueScheduledCheck(
                    isStopped,
                    LibraryRefresh.running,
                    ChapterUpdateSchedulePrefs.frequency(context),
                )
            ) break
            var cancelled = false
            try {
                val source = sources[entry.sourceId]
                if (source == null) {
                    unavailable++
                } else {
                    val series = source.restoreSeries(entry.seriesId, entry.title)
                    if (series == null) {
                        failed++
                    } else {
                        val chapters = source.listChapters(series)
                        if (chapters.isEmpty()) {
                            failed++
                        } else {
                            val previous = ChapterCache.load(context, entry.seriesId)
                            val fresh = ChapterUpdateDetector.newlyAvailable(previous, chapters)
                            if (fresh.isNotEmpty()) {
                                newChapters += fresh.size
                                val now = System.currentTimeMillis()
                                ChapterUpdateJournal.append(context, fresh.map {
                                    ChapterUpdateEvent(
                                        entry.sourceId, entry.seriesId, entry.title,
                                        it.id, it.name, now,
                                    )
                                })
                                ChapterUpdateNotifier.post(context, entry.title, fresh.size)
                            }
                            ChapterCache.save(context, entry.seriesId, chapters)
                            SeriesIndex.record(context, entry.sourceId, entry.seriesId, chapters)
                            checked++
                        }
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (_: Exception) {
                failed++
            } finally {
                // Save progress after each completed attempt. A cancellation during an
                // active request leaves that entry for the next scheduled run.
                if (!cancelled) {
                    completedEntries++
                    prefs.edit().putInt(
                        "chapter_update_check_cursor",
                        scheduledCheckNextCursor(offset, completedEntries, library.size),
                    ).apply()
                }
            }
            delay(350L)
        }
        if (!isStopped) {
            ChapterUpdateCheckSummaryStore.save(
                context,
                ChapterUpdateCheckSummary(System.currentTimeMillis(), checked, failed, unavailable, newChapters),
            )
        }
        ListenableWorker.Result.success()
    }
}

internal fun <T> scheduledUpdateBatch(items: List<T>, offset: Int, limit: Int): List<T> {
    if (items.isEmpty() || limit <= 0) return emptyList()
    val start = offset.coerceAtLeast(0) % items.size
    return (0 until minOf(limit, items.size)).map { items[(start + it) % items.size] }
}

/** Cursor advances only by entries actually attempted, even in a partial run. */
internal fun scheduledCheckNextCursor(offset: Int, attempted: Int, size: Int): Int =
    if (size <= 0) 0 else ((offset.toLong().coerceAtLeast(0) + attempted.coerceAtLeast(0)) % size).toInt()

internal object ChapterUpdateScheduler {
    private const val WORK_NAME = "chapter_update_check"
    fun schedule(context: Context, preserveExisting: Boolean = false) {
        val appContext = context.applicationContext
        val frequency = ChapterUpdateSchedulePrefs.frequency(appContext)
        val manager = WorkManager.getInstance(appContext)
        if (frequency == ChapterUpdateFrequency.OFF) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val network = if (ChapterUpdateSchedulePrefs.wifiOnly(appContext))
            NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<ChapterUpdateCheckWorker>(
            frequency.hours.toLong(), TimeUnit.HOURS
        ).setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME, chapterUpdateExistingWorkPolicy(preserveExisting), request
        )
    }
}

/** Startup keeps the existing clock; explicit settings changes update constraints. */
internal fun chapterUpdateExistingWorkPolicy(preserveExisting: Boolean): ExistingPeriodicWorkPolicy =
    if (preserveExisting) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE

/** Avoid unlimited retries if loading installed extensions repeatedly fails. */
internal fun shouldRetryUpdateSourceDiscovery(previousAttempts: Int): Boolean =
    previousAttempts in 0..2

/** Re-evaluate before every entry, not just when the scheduled run starts. */
internal fun shouldContinueScheduledCheck(
    stopped: Boolean,
    manualRefreshRunning: Boolean,
    frequency: ChapterUpdateFrequency,
): Boolean = !stopped && !manualRefreshRunning && frequency != ChapterUpdateFrequency.OFF
