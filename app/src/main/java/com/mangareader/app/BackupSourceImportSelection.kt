package com.mangareader.app

/** Ignore category assignments from favourites whose source identity was rejected. */
internal fun acceptedBackupCategoryAssignments(
    assignments: List<Pair<Pair<String, String>, Set<String>>>,
    acceptedKeys: Set<Pair<String, String>>,
): Map<String, Set<String>> = assignments.asSequence()
    .filter { (identity, _) -> identity in acceptedKeys }
    .associate { (identity, categories) -> identity.second to categories }

/**
 * Restore history only for favourite entries successfully admitted to the
 * library; restoring chapter progress for all sources is a separate action.
 */
internal fun acceptedBackupHistory(
    history: List<HistoryEntry>,
    acceptedKeys: Set<Pair<String, String>>,
): List<HistoryEntry> = history
    .filter { (it.sourceId to it.seriesId) in acceptedKeys }
    .sortedBy { it.updatedAt }
    .takeLast(40)
