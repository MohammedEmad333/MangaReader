package com.mangareader.app

import android.content.Context

internal data class LibraryGroupView(
    val key: String,
    val label: String,
    val items: List<LibraryEntry>,
)

internal data class LibraryArrangeSpec(
    val search: String,
    val sort: LibrarySort,
    val ascending: Boolean,
    val randomSeed: Int,
    val filterDownloaded: FilterState,
    val filterLocal: FilterState,
    val filterRead: FilterState,
    val filterUnread: FilterState,
    val filterStarted: FilterState,
    val filterCompleted: FilterState,
    val filterNsfw: FilterState,
    val downloadedIds: Set<String>,
    val readIds: Set<String>,
    val counts: Map<String, SeriesCounts>,
    val nsfwSources: Map<String, Boolean>,
    val lastReadAt: Map<String, Long>,
)

private fun FilterState.accepts(value: Boolean): Boolean = when (this) {
    FilterState.OFF -> true
    FilterState.INCLUDE -> value
    FilterState.EXCLUDE -> !value
}

internal fun arrangeLibraryEntries(
    list: List<LibraryEntry>,
    spec: LibraryArrangeSpec,
): List<LibraryEntry> {
    val needle = spec.search.trim()

    // Keep this allocation-free per entry. The old version built a seven-Pair
    // List for every series, which turns a quick search/re-sort over a large
    // library into thousands of short-lived objects and avoidable GC work.
    val filtered = list.filter { entry ->
        val counts = spec.counts[entry.seriesId]
        spec.filterDownloaded.accepts(entry.seriesId in spec.downloadedIds) &&
            spec.filterLocal.accepts(entry.sourceId.isLocalSourceId()) &&
            spec.filterRead.accepts(entry.seriesId in spec.readIds) &&
            spec.filterUnread.accepts((counts?.unread ?: 0) > 0) &&
            spec.filterStarted.accepts(counts?.started ?: false) &&
            spec.filterCompleted.accepts(counts?.completed ?: false) &&
            spec.filterNsfw.accepts(spec.nsfwSources[entry.sourceId] ?: false) &&
            (needle.isBlank() || entry.title.contains(needle, ignoreCase = true))
    }

    val ordered = when (spec.sort) {
        LibrarySort.ALPHABETICAL ->
            filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        LibrarySort.DATE_ADDED ->
            filtered.sortedBy { it.addedAt }
        LibrarySort.LAST_READ ->
            filtered.sortedBy {
                spec.lastReadAt[it.seriesId] ?: Long.MIN_VALUE
            }
        LibrarySort.UNREAD_COUNT ->
            filtered.sortedBy {
                spec.counts[it.seriesId]?.unread ?: Int.MIN_VALUE
            }
        LibrarySort.TOTAL_CHAPTERS ->
            filtered.sortedBy {
                spec.counts[it.seriesId]?.total ?: Int.MIN_VALUE
            }
        LibrarySort.LATEST_CHAPTER ->
            filtered.sortedBy {
                spec.counts[it.seriesId]?.latestChapterAt ?: Long.MIN_VALUE
            }
        LibrarySort.RANDOM ->
            filtered.sortedBy {
                LibraryPrefs.shuffleKey(it.seriesId, spec.randomSeed)
            }
    }

    return if (spec.ascending || spec.sort == LibrarySort.RANDOM) {
        ordered
    } else {
        ordered.reversed()
    }
}

internal fun buildLibraryGroups(
    context: Context,
    entries: List<LibraryEntry>,
    categories: List<Category>,
    grouping: LibraryGroup,
    spec: LibraryArrangeSpec,
): List<LibraryGroupView> {
    return when (grouping) {
        LibraryGroup.UNGROUPED -> listOf(
            LibraryGroupView(
                key = "all",
                label = "All",
                items = arrangeLibraryEntries(entries, spec),
            ),
        )

        LibraryGroup.SOURCES -> {
            val names = SourceNames.all(context)
            entries.groupBy { it.sourceId }
                .map { (sourceId, items) ->
                    LibraryGroupView(
                        key = sourceId,
                        label = names[sourceId]?.takeIf { it.isNotBlank() }
                            ?: SourceNames.unnamed(sourceId),
                        items = arrangeLibraryEntries(items, spec),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }

        else -> {
            // One pass over the assignment map for every category. Previously
            // seriesIn() scanned the whole map once per category, multiplying
            // the work by however many tabs the user had created.
            val byCategory = Categories.seriesByCategory(context)
            val assigned = byCategory.values
                .asSequence()
                .flatten()
                .toHashSet()

            categories.map { category ->
                val ids = byCategory[category.id].orEmpty()
                val base = if (category.id == Categories.DEFAULT_ID) {
                    entries.filter {
                        it.seriesId !in assigned ||
                            it.seriesId in ids
                    }
                } else {
                    entries.filter { it.seriesId in ids }
                }

                LibraryGroupView(
                    key = category.id,
                    label = category.name,
                    items = arrangeLibraryEntries(base, spec),
                )
            }
        }
    }
}
