package com.mangareader.app

/**
 * Stable source-qualified key for UI state and history ordering.
 * Length prefixes distinguish e.g. ("a|b", "c") from ("a", "b|c").
 */
internal fun libraryEntryKey(sourceId: String, seriesId: String): String =
    "${sourceId.length}:$sourceId${seriesId.length}:$seriesId"

/**
 * Older library screens and category/series indexes are still keyed by seriesId.
 * Until those indexes migrate, never silently replace a saved series from a
 * different source when an extension reuses that seriesId.
 */
internal fun hasLibrarySourceCollision(
    saved: List<LibraryEntry>,
    incoming: LibraryEntry,
): Boolean = saved.any {
    it.seriesId == incoming.seriesId && it.sourceId != incoming.sourceId
}

/**
 * Batch imports preserve previously saved source ownership on collision.
 * Ambiguous new incoming IDs are skipped rather than overwriting one another.
 * The caller can introduce source-qualified storage after dependent indexes
 * and bulk selection are migrated together.
 */
internal fun acceptedLibraryImportEntries(
    saved: List<LibraryEntry>,
    incoming: List<LibraryEntry>,
): List<LibraryEntry> {
    val existingById = saved.associateBy { it.seriesId }
    return incoming.groupBy { it.seriesId }.mapNotNull { (id, candidates) ->
        val previous = existingById[id]
        if (previous != null) candidates.firstOrNull { it.sourceId == previous.sourceId }
        else candidates.firstOrNull()?.takeIf { first ->
            candidates.all { it.sourceId == first.sourceId }
        }
    }
}

/** Match exactly the source-qualified entries being removed by a backup. */
internal fun withoutLibraryIdentities(
    saved: List<LibraryEntry>,
    removed: Set<Pair<String, String>>,
): List<LibraryEntry> = saved.filterNot { (it.sourceId to it.seriesId) in removed }

internal fun mergeLibraryWithoutSourceCollisions(
    saved: List<LibraryEntry>,
    incoming: List<LibraryEntry>,
): List<LibraryEntry> {
    val accepted = acceptedLibraryImportEntries(saved, incoming)
    val replaced = accepted.mapTo(mutableSetOf()) { it.seriesId }
    return (accepted + saved.filterNot { it.seriesId in replaced })
        .distinctBy { it.seriesId }
}
