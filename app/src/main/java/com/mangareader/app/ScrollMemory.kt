package com.mangareader.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * Lazy-list positions that have to outlive the composable showing them.
 *
 * The routing chain in `YomuApp` is an if/else, so opening a series doesn't
 * cover the library — it *replaces* it. Every `remember` in the branch that went
 * away is gone, and `rememberSaveable` doesn't help: that survives a config
 * change and process death, not leaving composition. It's the same reason the
 * category tab was hoisted into `YomuApp` already; scroll position has exactly
 * the same problem and needs the same answer.
 *
 * So the position lives here, in an object the root holds, and each grid seeds
 * itself from it on the way in and writes back on the way out.
 *
 * [sync] is the other half of it. A stored position only means anything against
 * the ordering that produced it — after a re-sort, "item 40" is a different
 * series, and restoring it drops the user somewhere arbitrary. Handing the
 * current ordering to [sync] on every composition throws the positions away
 * when, and only when, that ordering actually changes. A return trip from a
 * series restores (same ordering); a re-sort starts at the top.
 */
internal class ScrollMemory {

    data class Position(val index: Int, val offset: Int)

    private val positions = HashMap<String, Position>()
    private var signature: Any? = null

    /** Drops every stored position when [current] differs from the last call. */
    fun sync(current: Any?) {
        if (signature != current) {
            positions.clear()
            signature = current
        }
    }

    fun of(key: String): Position = positions[key] ?: ZERO

    /**
     * Records where [key] is sitting.
     *
     * [ordering] is checked rather than trusted: a grid disposed *because* the
     * ordering changed reports a position belonging to the ordering before it,
     * and it reports it after [sync] has already cleared the store. Letting that
     * write land would put the stale position straight back, and the next visit
     * to this screen would restore it.
     */
    fun save(key: String, ordering: Any?, index: Int, offset: Int) {
        if (ordering != signature) return
        positions[key] = Position(index, offset)
    }

    private companion object {
        val ZERO = Position(0, 0)
    }
}

/**
 * A grid state seeded from [memory] and written back to it on dispose.
 *
 * [ordering] is a key rather than something this reads: when it changes, the
 * state object itself is rebuilt, and that is what puts the grid back at the top
 * after a re-sort. [ScrollMemory.sync] has emptied the store by then, so the new
 * state starts at zero.
 */
@Composable
internal fun rememberRestoredGridState(
    memory: ScrollMemory,
    key: String,
    ordering: Any?
): LazyGridState {
    val state = remember(memory, key, ordering) {
        val at = memory.of(key)
        LazyGridState(at.index, at.offset)
    }
    DisposableEffect(state) {
        onDispose {
            memory.save(
                key,
                ordering,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset
            )
        }
    }
    return state
}

/** [rememberRestoredGridState] for a `LazyColumn`; the list display mode uses one. */
@Composable
internal fun rememberRestoredListState(
    memory: ScrollMemory,
    key: String,
    ordering: Any?
): LazyListState {
    val state = remember(memory, key, ordering) {
        val at = memory.of(key)
        LazyListState(at.index, at.offset)
    }
    DisposableEffect(state) {
        onDispose {
            memory.save(
                key,
                ordering,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset
            )
        }
    }
    return state
}
