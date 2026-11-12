package com.mangareader.app

/**
 * Keep only the latest pending update per source-qualified series, preserving
 * the order of each series' *last* update.
 *
 * For example, A(page 1), B(page 2), A(page 3) becomes B(page 2), A(page 3).
 * Applying this batch with History.touchAll produces the same visible history
 * as applying all three updates individually, with fewer preference writes.
 */
internal class ReaderHistoryBatch {
    private val latest = LinkedHashMap<Triple<Boolean, String, String>, HistoryEntry>()

    fun add(entry: HistoryEntry) {
        val identity = historyEntryIdentity(entry)
        latest.remove(identity)
        latest[identity] = entry
    }

    fun entries(): List<HistoryEntry> = latest.values.toList()

    val size: Int get() = latest.size
}
