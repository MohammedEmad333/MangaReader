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

internal fun arrangeLibraryEntries(
    list: List<LibraryEntry>,
    spec: LibraryArrangeSpec,
): List<LibraryEntry> {
    val needle = spec.search.trim()

    val filtered = list.filter { entry ->
        val counts = spec.counts[entry.seriesId]
        val checks = listOf(
            spec.filterDownloaded to (entry.seriesId in spec.downloadedIds),
            spec.filterLocal to entry.sourceId.isLocalSourceId(),
            spec.filterRead to (entry.seriesId in spec.readIds),
            spec.filterUnread to ((counts?.unread ?: 0) > 0),
            spec.filterStarted to (counts?.started ?: false),
            spec.filterCompleted to (counts?.completed ?: false),
            spec.filterNsfw to (spec.nsfwSources[entry.sourceId] ?: false),
        )

        checks.all { (state, holds) ->
            when (state) {
                FilterState.OFF -> true
                FilterState.INCLUDE -> holds
                FilterState.EXCLUDE -> !holds
            }
        } && (
            needle.isBlank() ||
                entry.title.contains(needle, ignoreCase = true)
            )
    }

    val ordered = when (spec.sort) {
        LibrarySort.ALPHABETICAL ->
            filtered.sortedBy { it.title.lowercase() }
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
            val assigned by lazy { Categories.assignedSeries(context) }
            categories.map { category ->
                val base = if (category.id == Categories.DEFAULT_ID) {
                    val explicit = Categories.seriesIn(context, category.id)
                    entries.filter {
                        it.seriesId !in assigned ||
                            it.seriesId in explicit
                    }
                } else {
                    val ids = Categories.seriesIn(context, category.id)
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
