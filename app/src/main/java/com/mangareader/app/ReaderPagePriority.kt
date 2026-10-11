package com.mangareader.app

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shares the reader's visible page with its active chapter fetch.
 * A ticket prevents an older cancelled load from clearing a newer one.
 */
internal object ReaderPagePriority {
    private val active = ConcurrentHashMap<String, AtomicInteger>()
    private val visible = ConcurrentHashMap<String, List<Int>>()

    fun begin(chapterKey: String, startAt: Int): AtomicInteger =
        AtomicInteger(startAt).also { active[chapterKey] = it }

    fun update(chapterKey: String, pageIndex: Int) {
        if (pageIndex >= 0) active[chapterKey]?.set(pageIndex)
    }

    fun updateVisible(chapterKey: String, pages: List<Int>) {
        if (active.containsKey(chapterKey)) visible[chapterKey] = pages.filter { it >= 0 }.distinct()
    }

    fun visiblePages(chapterKey: String): List<Int> = visible[chapterKey].orEmpty()

    fun finish(chapterKey: String, ticket: AtomicInteger) {
        if (active.remove(chapterKey, ticket)) visible.remove(chapterKey)
    }
}

/** The current page, followed by upcoming pages, then previous pages. */
internal fun prioritizedPageOrder(
    pageCount: Int,
    visiblePage: Int,
    alreadyStarted: Set<Int> = emptySet(),
    visiblePages: List<Int> = emptyList(),
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val start = visiblePage.coerceIn(0, pageCount - 1)
    val viewport = visiblePages.filter { it in 0 until pageCount }
    return (listOf(start) + viewport + (start + 1 until pageCount) + (start - 1 downTo 0))
        .distinct().filterNot { it in alreadyStarted }
}
