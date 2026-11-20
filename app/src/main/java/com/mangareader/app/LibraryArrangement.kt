package com.mangareader.app

import android.content.Context
import java.text.Normalizer
import java.util.Locale

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
    searchQuery: LibrarySearchQuery = LibrarySearchQuery.from(spec.search),
): List<LibraryEntry> {

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
            searchQuery.matches(entry.title)
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
                spec.lastReadAt[libraryEntryKey(it.sourceId, it.seriesId)] ?: Long.MIN_VALUE
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
                LibraryPrefs.shuffleKey(
                    libraryEntryKey(it.sourceId, it.seriesId), spec.randomSeed
                )
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
    // Share one normalized query across every source/category group. The query
    // used to be normalized again for every title in every group.
    val searchQuery = LibrarySearchQuery.from(spec.search)
    return when (grouping) {
        LibraryGroup.UNGROUPED -> listOf(
            LibraryGroupView(
                key = "all",
                label = "All",
                items = arrangeLibraryEntries(entries, spec, searchQuery),
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
                        items = arrangeLibraryEntries(items, spec, searchQuery),
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
                    items = arrangeLibraryEntries(base, spec, searchQuery),
                )
            }
        }
    }
}

/** Match each query word independently so order and extra whitespace do not matter. */
private val LIBRARY_SEARCH_WORD_SEPARATOR = Regex("[\\s\\p{Zs}]+")
private val LIBRARY_SEARCH_MARKS = Regex("\\p{M}+")
private val LIBRARY_SEARCH_ARABIC_ALEF = Regex("[أإآٱ]")

internal fun librarySearchTerms(query: String): List<String> =
    query.trim().split(LIBRARY_SEARCH_WORD_SEPARATOR).filter { it.isNotEmpty() }

private fun searchableLibraryText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(LIBRARY_SEARCH_MARKS, "")
        .replace(LIBRARY_SEARCH_ARABIC_ALEF, "ا")
        .replace("ى", "ي")
        .replace("ـ", "")
        .lowercase(Locale.ROOT)

/**
 * A compiled library query. Query terms are normalized once and can then be
 * reused for every title, including titles split across multiple groups.
 */
internal class LibrarySearchQuery private constructor(
    private val normalizedTerms: List<String>,
) {
    fun matches(title: String): Boolean {
        if (normalizedTerms.isEmpty()) return true
        val normalizedTitle = searchableLibraryText(title)
        return normalizedTerms.all { term -> normalizedTitle.contains(term) }
    }

    companion object {
        fun from(query: String): LibrarySearchQuery =
            LibrarySearchQuery(
                normalizedTerms = librarySearchTerms(query).map(::searchableLibraryText),
            )

        internal fun fromTerms(terms: List<String>): LibrarySearchQuery =
            LibrarySearchQuery(terms.map(::searchableLibraryText))
    }
}

/** Compatibility helper for callers/tests that already have raw query terms. */
internal fun libraryTitleMatches(title: String, terms: List<String>): Boolean =
    LibrarySearchQuery.fromTerms(terms).matches(title)
