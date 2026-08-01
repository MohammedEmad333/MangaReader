package com.mangareader.app

import android.content.Context
import kotlin.random.Random

/**
 * How the library grid is laid out, ordered, grouped and filtered.
 *
 * One store, read on every composition of the library and written on every tap
 * in the options sheet — the same "save on edit, not on dismiss" rule
 * `ReaderPrefs` follows, and for the same reason: a sheet can be swiped away or
 * the screen taken by the system, and a setting lost that way is a bug nobody
 * reports.
 *
 * Every value carries a `key` so it survives being reordered or renamed here.
 */

/**
 * [UNREAD_COUNT], [TOTAL_CHAPTERS] and [LATEST_CHAPTER] all read `SeriesIndex`,
 * which is a lagging store — a series that has never been opened has no entry
 * and no counts. Those sort to the end rather than to zero: "un-counted" and
 * "none left to read" are opposite claims, and after an import the first is
 * nearly the whole library.
 */
enum class LibrarySort(val key: String, val label: String) {
    ALPHABETICAL("alpha", "Alphabetically"),
    DATE_ADDED("added", "Date added"),
    LAST_READ("lastread", "Last read"),
    UNREAD_COUNT("unread", "Unread count"),
    TOTAL_CHAPTERS("chapters", "Chapter count"),
    LATEST_CHAPTER("latest", "Latest chapter"),
    RANDOM("random", "Random");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: ALPHABETICAL
    }
}

enum class LibraryDisplay(val key: String, val label: String) {
    COMPACT_GRID("compact", "Compact grid"),
    COMFORTABLE_GRID("comfortable", "Comfortable grid"),
    COVER_ONLY_GRID("cover", "Cover-only grid"),
    LIST("list", "List");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: COMFORTABLE_GRID
    }
}

enum class LibraryGroup(val key: String, val label: String) {
    CATEGORIES("categories", "Categories"),
    SOURCES("sources", "Sources"),
    UNGROUPED("none", "Ungrouped");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CATEGORIES
    }
}

/**
 * A filter that can be off, required, or forbidden.
 *
 * Stored as an Int rather than reusing Compose's `ToggleableState` so this file
 * stays free of UI types; the sheet maps between the two. The ordinals are
 * written to prefs, so don't reorder them.
 */
enum class FilterState(val stored: Int) {
    OFF(0), INCLUDE(1), EXCLUDE(2);

    fun next(): FilterState = when (this) {
        OFF -> INCLUDE
        INCLUDE -> EXCLUDE
        EXCLUDE -> OFF
    }

    companion object {
        fun from(stored: Int) = entries.firstOrNull { it.stored == stored } ?: OFF
    }
}

object LibraryPrefs {
    private fun p(c: Context) = c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    // ---- sort ----

    fun sort(c: Context) = LibrarySort.from(p(c).getString("lib_sort", null))
    fun setSort(c: Context, v: LibrarySort) = p(c).edit().putString("lib_sort", v.key).apply()

    fun ascending(c: Context) = p(c).getBoolean("lib_sort_asc", true)
    fun setAscending(c: Context, v: Boolean) = p(c).edit().putBoolean("lib_sort_asc", v).apply()

    /**
     * The shuffle seed for [LibrarySort.RANDOM].
     *
     * Persisted so the order is stable across recompositions, tab swipes and
     * app restarts — a random sort that reshuffles every time the grid
     * recomposes isn't a sort, it's a slot machine. The refresh button next to
     * the option is the only thing that moves it.
     */
    fun randomSeed(c: Context) = p(c).getInt("lib_random_seed", 0)

    // Random.nextInt() rather than the clock: two reshuffles a second apart
    // differ only in the low bits of a timestamp, and a seed that only moves in
    // its low bits is a weak input to any mixing that follows.
    fun reshuffle(c: Context) =
        p(c).edit().putInt("lib_random_seed", Random.nextInt()).apply()

    /**
     * The ordering key for [LibrarySort.RANDOM].
     *
     * `hashCode() xor seed` is the tempting version and it's weak in a way that
     * only shows on real data: ids from one source differ mostly in their last
     * few characters, so their hashes arrive already clustered, and xor is a
     * bit-flip — it can reverse blocks of an order but it can't break a cluster
     * apart. Running the id and the seed through an avalanche mix gives an order
     * with no visible relation to either, which is what "random" has to mean
     * here, and it stays a pure function of (id, seed) so the order is still
     * stable across recompositions and restarts.
     */
    fun shuffleKey(seriesId: String, seed: Int): Int = mix(seriesId.hashCode() xor mix(seed))

    /** murmur3's fmix32 finaliser. */
    private fun mix(value: Int): Int {
        var h = value
        h = h xor (h ushr 16)
        h *= 0x85EBCA6B.toInt()
        h = h xor (h ushr 13)
        h *= 0xC2B2AE35.toInt()
        h = h xor (h ushr 16)
        return h
    }

    // ---- display ----

    fun display(c: Context) = LibraryDisplay.from(p(c).getString("lib_display", null))
    fun setDisplay(c: Context, v: LibraryDisplay) =
        p(c).edit().putString("lib_display", v.key).apply()

    /** 0 means Auto — size-driven columns rather than a fixed count. */
    fun itemsPerRow(c: Context) = p(c).getInt("lib_per_row", 0)
    fun setItemsPerRow(c: Context, v: Int) = p(c).edit().putInt("lib_per_row", v).apply()

    fun badgeDownloaded(c: Context) = p(c).getBoolean("lib_badge_dl", true)
    fun setBadgeDownloaded(c: Context, v: Boolean) =
        p(c).edit().putBoolean("lib_badge_dl", v).apply()

    fun badgeLocal(c: Context) = p(c).getBoolean("lib_badge_local", true)
    fun setBadgeLocal(c: Context, v: Boolean) = p(c).edit().putBoolean("lib_badge_local", v).apply()

    fun badgeUnread(c: Context) = p(c).getBoolean("lib_badge_unread", true)
    fun setBadgeUnread(c: Context, v: Boolean) =
        p(c).edit().putBoolean("lib_badge_unread", v).apply()

    fun showTabs(c: Context) = p(c).getBoolean("lib_show_tabs", true)
    fun setShowTabs(c: Context, v: Boolean) = p(c).edit().putBoolean("lib_show_tabs", v).apply()

    fun showCount(c: Context) = p(c).getBoolean("lib_show_count", false)
    fun setShowCount(c: Context, v: Boolean) = p(c).edit().putBoolean("lib_show_count", v).apply()

    // ---- group ----

    /**
     * The category tab last looked at, so the Library opens where it was left.
     *
     * A pref rather than `rememberSaveable` alone. Saveable state is restored
     * from the Activity's saved bundle, which exists across a rotation or a
     * low-memory kill — and is **null on an ordinary cold start from the
     * launcher**, which is the case anyone actually notices. The saveable stays
     * as well; it is what covers a recreation without a disk read.
     *
     * Null means "never chosen", which resolves to the first tab. A key that no
     * longer matches any group falls back to index 0 on its own, so a deleted
     * category needs no cleanup here.
     */
    fun lastCategory(c: Context): String? = p(c).getString("lib_last_category", null)
    fun setLastCategory(c: Context, v: String?) =
        p(c).edit().putString("lib_last_category", v).apply()

    fun group(c: Context) = LibraryGroup.from(p(c).getString("lib_group", null))
    fun setGroup(c: Context, v: LibraryGroup) = p(c).edit().putString("lib_group", v.key).apply()

    // ---- filter ----

    fun filterDownloaded(c: Context) = FilterState.from(p(c).getInt("lib_f_dl", 0))
    fun setFilterDownloaded(c: Context, v: FilterState) =
        p(c).edit().putInt("lib_f_dl", v.stored).apply()

    fun filterLocal(c: Context) = FilterState.from(p(c).getInt("lib_f_local", 0))
    fun setFilterLocal(c: Context, v: FilterState) =
        p(c).edit().putInt("lib_f_local", v.stored).apply()

    fun filterRead(c: Context) = FilterState.from(p(c).getInt("lib_f_read", 0))
    fun setFilterRead(c: Context, v: FilterState) =
        p(c).edit().putInt("lib_f_read", v.stored).apply()

    // The three below are answered from SeriesIndex, which is a lagging store:
    // a series that has never been opened has no counts at all. Those are read
    // as "the condition doesn't hold" rather than guessed at, which falls out of
    // the existing tri-state with no special case and gives the right shape both
    // ways round — INCLUDE Unread shows only series known to have unread
    // chapters, EXCLUDE Unread hides the ones known to have them and leaves the
    // un-counted alone. The sheet carries a note saying so, because after an
    // import "un-counted" is most of the library and an empty Unread tab would
    // otherwise look like a broken filter.

    fun filterUnread(c: Context) = FilterState.from(p(c).getInt("lib_f_unread", 0))
    fun setFilterUnread(c: Context, v: FilterState) =
        p(c).edit().putInt("lib_f_unread", v.stored).apply()

    fun filterStarted(c: Context) = FilterState.from(p(c).getInt("lib_f_started", 0))
    fun setFilterStarted(c: Context, v: FilterState) =
        p(c).edit().putInt("lib_f_started", v.stored).apply()

    fun filterCompleted(c: Context) = FilterState.from(p(c).getInt("lib_f_completed", 0))
    fun setFilterCompleted(c: Context, v: FilterState) =
        p(c).edit().putInt("lib_f_completed", v.stored).apply()

    /** True when anything is filtering, so the bar can show it. */
    fun anyFilterActive(c: Context) =
        filterDownloaded(c) != FilterState.OFF ||
            filterLocal(c) != FilterState.OFF ||
            filterRead(c) != FilterState.OFF ||
            filterUnread(c) != FilterState.OFF ||
            filterStarted(c) != FilterState.OFF ||
            filterCompleted(c) != FilterState.OFF
}
