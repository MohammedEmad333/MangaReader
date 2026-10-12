package com.mangareader.app

/**
 * Moves one queued item immediately after the currently active item.
 * An active download must not be interrupted to change queue order.
 */
internal fun <T> prioritizeQueuedItem(
    items: List<T>,
    selectedId: String,
    activeId: String?,
    idOf: (T) -> String,
): List<T> {
    if (selectedId.isBlank() || selectedId == activeId) return items
    val selected = items.firstOrNull { idOf(it) == selectedId } ?: return items
    val remaining = items.filterNot { idOf(it) == selectedId }
    val activePosition = remaining.indexOfFirst { idOf(it) == activeId }
    val insertion = if (activePosition >= 0) activePosition + 1 else 0
    return remaining.toMutableList().apply { add(insertion, selected) }
}

/** Move a waiting chapter one position later, never moving the active transfer. */
internal fun <T> moveQueuedItemDown(
    items: List<T>,
    selectedId: String,
    activeId: String?,
    idOf: (T) -> String,
): List<T> {
    val index = items.indexOfFirst { idOf(it) == selectedId }
    if (index < 0 || index == items.lastIndex || selectedId == activeId ||
        idOf(items[index + 1]) == activeId) return items
    return items.toMutableList().apply {
        val selected = removeAt(index)
        add(index + 1, selected)
    }
}

/** Move a waiting chapter one slot earlier, without interrupting the active chapter. */
internal fun <T> moveQueuedItemUp(
    items: List<T>,
    selectedId: String,
    activeId: String?,
    idOf: (T) -> String,
): List<T> {
    val index = items.indexOfFirst { idOf(it) == selectedId }
    if (index <= 0 || selectedId == activeId ||
        idOf(items[index - 1]) == activeId) return items
    return items.toMutableList().apply {
        val selected = removeAt(index)
        add(index - 1, selected)
    }
}

/** Show the first runnable waiting item, skipping paused and active chapters. */
internal fun nextDownloadChapterId(
    queuedIds: List<String>,
    activeId: String?,
    pausedIds: Set<String>,
): String? = queuedIds.firstOrNull { it != activeId && it !in pausedIds }
