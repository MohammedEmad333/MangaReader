package com.mangareader.app

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shares the reader's visible page with its active chapter fetch.
 * A ticket prevents an older cancelled load from clearing a newer one.
 */
internal object ReaderPagePriority {
    private val active = ConcurrentHashMap<String, AtomicInteger>()

    fun begin(chapterKey: String, startAt: Int): AtomicInteger =
        AtomicInteger(startAt).also { active[chapterKey] = it }

    fun update(chapterKey: String, pageIndex: Int) {
        if (pageIndex >= 0) active[chapterKey]?.set(pageIndex)
    }

    fun finish(chapterKey: String, ticket: AtomicInteger) {
        active.remove(chapterKey, ticket)
    }
}

/** The current page, followed by upcoming pages, then previous pages. */
internal fun prioritizedPageOrder(
    pageCount: Int,
    visiblePage: Int,
    alreadyStarted: Set<Int> = emptySet(),
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val start = visiblePage.coerceIn(0, pageCount - 1)
    return ((start until pageCount) + (start - 1 downTo 0))
        .filterNot { it in alreadyStarted }
}
