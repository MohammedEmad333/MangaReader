package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LibraryDerivedState(
    val readIds: Set<String>,
    val downloadedIds: Set<String>,
    val counts: Map<String, SeriesCounts>,
    val groups: List<LibraryGroupView>,
    val ordering: List<Any?>,
)

private data class LibraryDerivedInputs(
    val readIds: Set<String>,
    val downloadedIds: Set<String>,
    val counts: Map<String, SeriesCounts>,
    val nsfwSources: Map<String, Boolean>,
    val lastReadAt: Map<String, Long>,
)

@Composable
private fun rememberLibraryDerivedInputs(
    context: Context,
    tick: Int,
    categories: List<Category>,
    sort: LibrarySort,
    badgeDl: Boolean,
    badgeUnread: Boolean,
    fDownloaded: FilterState,
    fUnread: FilterState,
    fStarted: FilterState,
    fCompleted: FilterState,
    fNsfw: FilterState,
): LibraryDerivedInputs? {
    val appContext = context.applicationContext
    val nsfwVersion = SourceNsfw.version

    val state by produceState<LibraryDerivedInputs?>(
        initialValue = null,
        tick,
        categories,
        sort,
        badgeDl,
        badgeUnread,
        fDownloaded,
        fUnread,
        fStarted,
        fCompleted,
        fNsfw,
        nsfwVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            val readCat = categories.firstOrNull {
                it.name.equals("Read", ignoreCase = true)
            }
            val readIds = if (readCat == null) {
                emptySet()
            } else {
                Categories.seriesIn(appContext, readCat.id)
            }

            // seriesIds() is already the cheap download-index path, but it can
            // still probe one marker per downloaded chapter on a cold process.
            // It belongs on IO, not in the frame that is opening the library.
            val downloadedIds =
                if (!badgeDl && fDownloaded == FilterState.OFF) {
                    emptySet()
                } else {
                    StartupTimings.once("Downloaded ids") {
                        DownloadIndex.seriesIds(appContext)
                    }
                }

            val wantsCounts =
                badgeUnread ||
                    sort == LibrarySort.UNREAD_COUNT ||
                    sort == LibrarySort.TOTAL_CHAPTERS ||
                    sort == LibrarySort.LATEST_CHAPTER ||
                    fUnread != FilterState.OFF ||
                    fStarted != FilterState.OFF ||
                    fCompleted != FilterState.OFF
            val counts = if (wantsCounts) {
                SeriesIndex.all(appContext)
            } else {
                emptyMap()
            }

            val nsfwSources = if (fNsfw == FilterState.OFF) {
                emptyMap()
            } else {
                SourceNsfw.all(appContext)
            }

            val lastReadAt = if (sort != LibrarySort.LAST_READ) {
                emptyMap()
            } else {
                History.list(appContext)
                    .asSequence()
                    .filter { it.seriesId.isNotBlank() }
                    .groupBy { it.seriesId }
                    .mapValues { (_, rows) -> rows.maxOf { it.updatedAt } }
            }

            LibraryDerivedInputs(
                readIds = readIds,
                downloadedIds = downloadedIds,
                counts = counts,
                nsfwSources = nsfwSources,
                lastReadAt = lastReadAt,
            )
        }
    }
    return state
}

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
    scroll: ScrollMemory,
): LibraryDerivedState? {
    // Scroll identity is intentionally cheap and synchronous. The lazy grid
    // reads ScrollMemory while it is being composed, so a new ordering has to
    // invalidate the old position before the next grid state is constructed.
    val ordering = remember(
        sort,
        ascending,
        randomSeed,
        grouping,
        search,
        mediaFilter,
        fDownloaded,
        fLocal,
        fRead,
        fUnread,
        fStarted,
        fCompleted,
    ) {
        listOf(
            sort,
            ascending,
            randomSeed,
            grouping,
            search.trim(),
            mediaFilter,
            fDownloaded,
            fLocal,
            fRead,
            fUnread,
            fStarted,
            fCompleted,
        )
    }
    scroll.sync(ordering)

    val inputs = rememberLibraryDerivedInputs(
        context = context,
        tick = tick,
        categories = categories,
        sort = sort,
        badgeDl = badgeDl,
        badgeUnread = badgeUnread,
        fDownloaded = fDownloaded,
        fUnread = fUnread,
        fStarted = fStarted,
        fCompleted = fCompleted,
        fNsfw = fNsfw,
    ) ?: return null

    val appContext = context.applicationContext
    val sourceNamesVersion = SourceNames.version
    val groups by produceState<List<LibraryGroupView>?>(
        initialValue = null,
        entries,
        categories,
        tick,
        grouping,
        search,
        sort,
        ascending,
        randomSeed,
        fDownloaded,
        fLocal,
        fRead,
        fUnread,
        fStarted,
        fCompleted,
        fNsfw,
        inputs,
        sourceNamesVersion,
    ) {
        val spec = LibraryArrangeSpec(
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
            downloadedIds = inputs.downloadedIds,
            readIds = inputs.readIds,
            counts = inputs.counts,
            nsfwSources = inputs.nsfwSources,
            lastReadAt = inputs.lastReadAt,
        )

        value = withContext(Dispatchers.IO) {
            buildLibraryGroups(
                context = appContext,
                entries = entries,
                categories = categories,
                grouping = grouping,
                spec = spec,
            )
        }
    }

    val resolvedGroups = groups ?: return null
    return LibraryDerivedState(
        readIds = inputs.readIds,
        downloadedIds = inputs.downloadedIds,
        counts = inputs.counts,
        groups = resolvedGroups,
        ordering = ordering,
    )
}
