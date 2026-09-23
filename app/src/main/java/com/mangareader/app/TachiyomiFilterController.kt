package com.mangareader.app

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * Owns Tachiyomi's mutable live filter instance for one source adapter.
 *
 * Tachiyomi filters keep selection in mutable state, so searches must reuse the
 * same [FilterList] instance that the UI edits instead of requesting fresh
 * defaults on every call.
 */
internal class TachiyomiFilterController(
    private val delegate: CatalogueSource,
) {
    @Volatile
    private var cachedFilters: FilterList? = null

    val filterList: FilterList
        get() = cachedFilters ?: synchronized(this) {
            cachedFilters ?: runCatching { delegate.getFilterList() }
                .getOrDefault(FilterList())
                .also { cachedFilters = it }
        }

    fun reset() {
        cachedFilters = null
    }

    /**
     * Applies the genre/tag filter matching [genre].
     *
     * Sources commonly expose genres either as a Group of TriState/CheckBox
     * children or as a Select list. The filter state is reset first so tapping a
     * genre does not accidentally compose with stale manual filter selections.
     */
    fun applyGenre(genre: String): Boolean {
        reset()

        for (filter in filterList) {
            when (filter) {
                is Filter.Group<*> -> {
                    val child = (filter.state as? List<*>)
                        ?.filterIsInstance<Filter<*>>()
                        ?.firstOrNull { it.name.equals(genre, ignoreCase = true) }

                    when (child) {
                        is Filter.TriState -> {
                            child.state = Filter.TriState.STATE_INCLUDE
                            return true
                        }
                        is Filter.CheckBox -> {
                            child.state = true
                            return true
                        }
                        else -> Unit
                    }
                }

                is Filter.Select<*> -> {
                    val index = filter.values
                        .indexOfFirst { it?.toString().equals(genre, ignoreCase = true) }
                    if (index >= 0) {
                        filter.state = index
                        return true
                    }
                }

                else -> Unit
            }
        }

        // Leave defaults in place for the caller's fallback title search.
        reset()
        return false
    }
}
