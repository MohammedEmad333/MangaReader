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
    private val directions = ConcurrentHashMap<String, Int>()
    private val strides = ConcurrentHashMap<String, Int>()

    fun begin(chapterKey: String, startAt: Int): AtomicInteger {
        directions.remove(chapterKey)
        strides.remove(chapterKey)
        visible.remove(chapterKey)
        return AtomicInteger(startAt).also { active[chapterKey] = it }
    }

    fun update(chapterKey: String, pageIndex: Int) {
        if (pageIndex < 0) return
        active[chapterKey]?.getAndSet(pageIndex)?.let { previous ->
            if (pageIndex != previous) {
                directions[chapterKey] = if (pageIndex > previous) 1 else -1
                strides[chapterKey] = kotlin.math.abs(pageIndex.toLong() - previous.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
    }

    fun direction(chapterKey: String): Int = directions[chapterKey] ?: 1

    /** Remember the size of the latest seek or page turn to prioritize near the landing page. */
    fun stride(chapterKey: String): Int = strides[chapterKey] ?: 1

    fun updateVisible(chapterKey: String, pages: List<Int>) {
        if (active.containsKey(chapterKey)) visible[chapterKey] = pages.filter { it >= 0 }.distinct()
    }

    fun visiblePages(chapterKey: String): List<Int> = visible[chapterKey].orEmpty()

    fun finish(chapterKey: String, ticket: AtomicInteger) {
        if (active.remove(chapterKey, ticket)) {
            visible.remove(chapterKey)
            directions.remove(chapterKey)
            strides.remove(chapterKey)
        }
    }
}

/**
 * Prioritize the current viewport, then a small directional neighborhood.
 * A distant jump expands the favored side by one page but never crowds out
 * the opposite adjacent page. Remaining pages keep their usual direction order
 * so downloads can still complete rather than waiting for another navigation.
 */
internal fun prioritizedPageOrder(
    pageCount: Int,
    visiblePage: Int,
    alreadyStarted: Set<Int> = emptySet(),
    visiblePages: List<Int> = emptyList(),
    direction: Int = 1,
    stride: Int = 1,
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val start = visiblePage.coerceIn(0, pageCount - 1)
    val viewport = visiblePages.filter { it in 0 until pageCount }
    val forward = (start + 1 until pageCount).toList()
    val backward = (start - 1 downTo 0).toList()
    val preferred = if (direction < 0) backward else forward
    val opposite = if (direction < 0) forward else backward
    val nearbyAhead = if (stride >= 3) 3 else 2
    val pending = preferred.take(nearbyAhead) + opposite.take(1) +
        preferred.drop(nearbyAhead) + opposite.drop(1)
    return (listOf(start) + viewport + pending)
        .distinct().filterNot { it in alreadyStarted }
}
