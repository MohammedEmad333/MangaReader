package com.mangareader.app

/**
 * In-memory set of series whose cached cover needs a refresh.
 *
 * Kept independent of Android preferences so deduplication, the safety cap and
 * removal after a successful repair can be tested without a device.
 */
internal class CoverRepairState(
    initialIds: Collection<String> = emptyList(),
    private val limit: Int,
) {
    init {
        require(limit >= 0)
    }

    private val ids = LinkedHashSet<String>().apply {
        initialIds.asSequence()
            .filter { it.isNotBlank() }
            .take(limit)
            .forEach(::add)
    }

    fun report(seriesId: String): Boolean {
        if (seriesId.isBlank() || seriesId in ids || ids.size >= limit) return false
        return ids.add(seriesId)
    }

    fun clear(seriesIds: Set<String>): Boolean =
        ids.removeAll(seriesIds)

    fun snapshot(): Set<String> = ids.toSet()
}
