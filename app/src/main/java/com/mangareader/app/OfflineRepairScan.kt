package com.mangareader.app

import android.content.Context
import java.io.File

/** A read-only audit; no files or completion markers are deleted or changed. */
internal data class OfflineRepairScan(
    val summary: OfflineRepairBatchSummary,
    val repairItems: List<DownloadItem>,
)

/**
 * The persisted download index retains chapter identity when a previously
 * complete chapter loses a page. The regular Downloads list hides such chapters,
 * so a repair audit must read the raw records instead.
 *
 * An index record alone is not proof that a download exists: require an existing
 * chapter folder and a valid completion marker. Unknown counts, legacy duplicate
 * names, and ambiguous indexes are blocked for manual review.
 */
internal fun scanIndexedOfflineRepairs(
    records: List<DownloadIndexRecord>,
    directoryFor: (String) -> File,
): OfflineRepairScan {
    val unique = records.distinctBy { it.chapterId }
        .filter { it.chapterId.isNotBlank() && it.sourceId.isNotBlank() }
    val alreadySeenDirs = mutableSetOf<String>()
    var complete = 0
    var repairable = 0
    var blocked = 0
    var pages = 0
    val toQueue = mutableListOf<DownloadItem>()

    unique.forEach { record ->
        val dir = directoryFor(record.chapterId)
        val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!alreadySeenDirs.add(canonical)) return@forEach
        // Do not create missing directories just because metadata survived.
        if (!dir.isDirectory || !File(dir, ".complete").isFile) {
            blocked++
            return@forEach
        }
        val integrity = inspectOfflineChapter(dir)
        if (integrity.isComplete) {
            complete++
            return@forEach
        }
        val plan = planOfflinePageRepair(dir)
        if (plan.canRepairAutomatically && plan.downloadIndices.isNotEmpty() &&
            record.seriesId.isNotBlank()) {
            repairable++
            pages += plan.downloadIndices.size
            toQueue += DownloadItem(
                sourceId = record.sourceId,
                chapterId = record.chapterId,
                chapterName = record.chapterName,
                seriesTitle = record.title,
                seriesId = record.seriesId,
                cover = record.cover,
            )
        } else {
            blocked++
        }
    }

    return OfflineRepairScan(
        summary = OfflineRepairBatchSummary(
            totalChapters = complete + repairable + blocked,
            completeChapters = complete,
            repairableChapters = repairable,
            blockedChapters = blocked,
            pagesToRedownload = pages,
        ),
        repairItems = toQueue,
    )
}

internal fun scanStoredOfflineRepairs(context: Context): OfflineRepairScan =
    scanIndexedOfflineRepairs(DownloadIndexStorage.read(context)) {
        Downloads.dirFor(context, it)
    }
