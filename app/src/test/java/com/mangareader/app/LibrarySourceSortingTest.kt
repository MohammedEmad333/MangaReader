package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySourceSortingTest {
    private fun entry(source: String, id: String) = LibraryEntry(
        seriesId = id, sourceId = source, title = "Same title", cover = "", addedAt = 1L,
    )

    @Test fun lastReadOrderingUsesSourceQualifiedSeriesIdentity() {
        val first = entry("source-a", "shared")
        val second = entry("source-b", "shared")
        val spec = LibraryArrangeSpec(
            search = "",
            sort = LibrarySort.LAST_READ,
            ascending = true,
            randomSeed = 1,
            filterDownloaded = FilterState.OFF,
            filterLocal = FilterState.OFF,
            filterRead = FilterState.OFF,
            filterUnread = FilterState.OFF,
            filterStarted = FilterState.OFF,
            filterCompleted = FilterState.OFF,
            filterNsfw = FilterState.OFF,
            downloadedIds = emptySet(),
            readIds = emptySet(),
            counts = emptyMap(),
            nsfwSources = emptyMap(),
            lastReadAt = mapOf(
                libraryEntryKey("source-a", "shared") to 300L,
                libraryEntryKey("source-b", "shared") to 100L,
            ),
        )
        assertEquals(listOf(second, first), arrangeLibraryEntries(listOf(first, second), spec))
    }
}
