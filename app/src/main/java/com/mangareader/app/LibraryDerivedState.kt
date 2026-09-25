package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.*

internal data class LibraryDerivedState(
    val readIds: Set<String>,
    val downloadedIds: Set<String>,
    val counts: Map<String, SeriesCounts>,
    val groups: List<LibraryGroupView>,
    val ordering: List<Any?>,
)

@Composable
internal fun rememberLibraryDerivedState(
    context: Context,
    tick: Int,
    entries: List<LibraryEntry>,
    categories: List<Category>,
    grouping: LibraryGroup,
    search: String,
    mediaFilter: String,
    sort: LibrarySort,
    ascending: Boolean,
    randomSeed: Int,
    badgeDl: Boolean,
    badgeUnread: Boolean,
    fDownloaded: FilterState,
    fLocal: FilterState,
    fRead: FilterState,
    fUnread: FilterState,
    fStarted: FilterState,
    fCompleted: FilterState,
    fNsfw: FilterState,
): LibraryDerivedState {
    // "Read" is a normal user category, so this is a name match rather than a
    // new field. One parse for the members; asking it per entry, through
    // categoriesFor(), is a full parse per series and is the mistake §5 records.
    val readIds = remember(tick, categories) {
        val readCat = categories.firstOrNull { it.name.equals("Read", ignoreCase = true) }
        if (readCat == null) emptySet<String>() else Categories.seriesIn(context, readCat.id)
    }

    // Asked for only when something on screen depends on it — and that gate was
    // never enough on its own, because the thing that depends on it (the
    // Downloaded badge) is on by default for everybody on every cold start.
    // This used to call DownloadIndex.list(), which sizes every downloaded
    // chapter, sorts by size, and runs an O(library) recovery scan to answer a
    // question about ids. Measured at 19.9 s of a cold start on this library.
    // seriesIds() is the same answer without the Downloads tab's work attached.
    val downloadedIds = remember(tick, badgeDl, fDownloaded) {
        if (!badgeDl && fDownloaded == FilterState.OFF) emptySet()
        else StartupTimings.once("Downloaded ids") { DownloadIndex.seriesIds(context) }
    }

    // Chapter counts per series. Unlike DownloadIndex this is a single string
    // read and one parse — no directory walk — but it is still conditional, for
    // the same reason and by the same rule: the library screen does no work the
    // current settings don't ask for.
    val counts = remember(tick, badgeUnread, sort, fUnread, fStarted, fCompleted) {
        val wanted = badgeUnread ||
            sort == LibrarySort.UNREAD_COUNT ||
            sort == LibrarySort.TOTAL_CHAPTERS ||
            sort == LibrarySort.LATEST_CHAPTER ||
            fUnread != FilterState.OFF ||
            fStarted != FilterState.OFF ||
            fCompleted != FilterState.OFF
        if (wanted) SeriesIndex.all(context) else emptyMap()
    }

    // Which sources are 18+. One string read and one parse, and only when the
    // filter is on — same rule as `counts` above. Keyed on SourceNsfw.version
    // because the flags are learned inside listAllSources on IO, which on a
    // cold start finishes after this screen has already filtered itself.
    val nsfwSources = remember(tick, fNsfw, SourceNsfw.version) {
        if (fNsfw == FilterState.OFF) emptyMap() else SourceNsfw.all(context)
    }

    // Most recent read per series, from History. History is capped at 40
    // chapters, so this is a partial answer by construction: anything older
    // simply has no timestamp and sorts to the end. That is worth having and
    // isn't worth a second store.
    val lastReadAt = remember(tick, sort) {
        if (sort != LibrarySort.LAST_READ) emptyMap()
        else History.list(context)
            .filter { it.seriesId.isNotBlank() }
            .groupBy { it.seriesId }
            .mapValues { (_, v) -> v.maxOf { it.updatedAt } }
    }

    val arrangeSpec = remember(
        search, sort, ascending, randomSeed,
        fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted, fNsfw,
        downloadedIds, readIds, counts, nsfwSources, lastReadAt,
    ) {
        LibraryArrangeSpec(
            search = search,
            sort = sort,
            ascending = ascending,
            randomSeed = randomSeed,
            filterDownloaded = fDownloaded,
            filterLocal = fLocal,
            filterRead = fRead,
            filterUnread = fUnread,
            filterStarted = fStarted,
            filterCompleted = fCompleted,
            filterNsfw = fNsfw,
            downloadedIds = downloadedIds,
            readIds = readIds,
            counts = counts,
            nsfwSources = nsfwSources,
            lastReadAt = lastReadAt,
        )
    }

    val groups: List<LibraryGroupView> = remember(
        entries, categories, tick, grouping, arrangeSpec,
        SourceNames.version,
    ) {
        buildLibraryGroups(
            context = context,
            entries = entries,
            categories = categories,
            grouping = grouping,
            spec = arrangeSpec,
        )
    }

    // What a stored scroll position is a position *into*. Everything that
    // changes which series sits at which index goes in here, and nothing else
    // does — a return trip from a series has to leave this identical or it
    // counts as a re-sort and throws the position away.
    //
    // This is why re-sorting used to look like the grid "just scrolling down":
    // the grid keys its items by series id, so on a reorder it hunts down
    // whatever was at the top and scrolls to its new index — which after a
    // shuffle is somewhere in the middle. The list really had been reordered;
    // it was just showing the same series, a thousand rows further in. Clearing
    // the position here is what makes a reorder start at the top.
    //
    // `counts` is deliberately *not* in here, though the three index-backed
    // sorts read it. It moves every time a chapter is finished, so including it
    // would drop the library's scroll position on every return from the reader —
    // which is the bug this whole mechanism exists to prevent, reintroduced from
    // the other end. What it costs is a slightly stale anchor when counts shift
    // under an index sort, and that case is a single series moving a few rows
    // rather than the wholesale reorder the position can't survive. Item keys
    // then re-anchor to the same series, which under a small change is the
    // behaviour you want anyway.
    val ordering = remember(
        sort, ascending, randomSeed, grouping, search, mediaFilter,
        fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted
    ) {
        listOf(
            sort, ascending, randomSeed, grouping, search.trim(), mediaFilter,
            fDownloaded, fLocal, fRead, fUnread, fStarted, fCompleted
        )
    }
    // Deliberately in composition rather than an effect: the grids below build
    // their state from `scroll` as they compose, so a stale position has to be
    // gone before they do, not one frame later. Idempotent, and a map lookup.
    scroll.sync(ordering)


    return LibraryDerivedState(
        readIds = readIds,
        downloadedIds = downloadedIds,
        counts = counts,
        groups = groups,
        ordering = ordering,
    )
}
