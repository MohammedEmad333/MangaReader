package com.mangareader.app

import android.content.Context

/** Never navigate to a similarly named series from another source. */
internal fun findUpdateLibraryEntry(
    entries: List<LibraryEntry>,
    update: ChapterUpdateEvent,
): LibraryEntry? = entries.firstOrNull {
    it.seriesId == update.seriesId && it.sourceId == update.sourceId
}

/** Chapter IDs must match exactly; never open an unrelated chapter by position. */
internal fun findUpdateChapterIndex(chapters: List<Chapter>, chapterId: String): Int? =
    chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 }

/** Leave transient routes before showing the chapter update feed from a notification. */
internal fun YomuStateBundle.openChapterUpdatesFromNotification(context: Context) {
    reader.close()
    series.clear()
    series.tagReturn = null
    browse.clearSource()
    search.cancel()
    search.open = false
    migration.from = null
    migration.target = null
    media.cancelScan()
    media.clearPlayer()

    app.challengeUrl = null
    app.downloadsOpen = false
    app.settingsOpen = false
    app.filtersOpen = false
    app.probeOpen = false
    app.loading = false
    app.error = null
    app.currentTab = 5
    app.updatesTick++

    // A notification tap is an explicit navigation choice, not a reader resume.
    ReaderSessionCheckpointStore.clear(context)
}
