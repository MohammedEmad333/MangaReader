package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun openLibraryEntry(
    context: Context,
    entry: LibraryEntry,
    onSourceResolved: (Source) -> Unit,
    onCachedChapters: (List<Chapter>) -> Unit,
    onResolved: (Source, Series, List<Chapter>) -> Unit
) {
    val source = withContext(Dispatchers.IO) {
        findInstalledSource(context, entry.sourceId)
    }
    onSourceResolved(source)

    val cached = withContext(Dispatchers.IO) {
        loadCachedChapters(context, source, entry.seriesId)
    }
    if (cached.isNotEmpty()) {
        onCachedChapters(cached)
    }

    val (series, chapters) = withContext(Dispatchers.IO) {
        resolveLibrarySeries(context, source, entry)
    }
    onResolved(source, series, chapters)
}

internal suspend fun openDownloadedEntry(
    context: Context,
    entry: DownloadedSeries,
    onSourceResolved: (Source) -> Unit,
    onCachedChapters: (List<Chapter>) -> Unit,
    onResolved: (Source, Series, List<Chapter>) -> Unit
) {
    val source = withContext(Dispatchers.IO) {
        findInstalledSource(context, entry.sourceId)
    }
    onSourceResolved(source)

    val cached = withContext(Dispatchers.IO) {
        loadCachedChapters(context, source, entry.seriesId)
    }
    if (cached.isNotEmpty()) {
        onCachedChapters(cached)
    }

    val resolved = withContext(Dispatchers.IO) {
        resolveDownloadedSeries(context, source, entry)
    }

    if (resolved == null) {
        if (cached.isEmpty()) {
            throw IllegalStateException(
                "No chapter list cached for this series — open it once online"
            )
        }
        return
    }

    onResolved(source, resolved.first, resolved.second)
}

internal suspend fun openHistoryEntry(
    context: Context,
    entry: HistoryEntry,
    onResolved: (ResumeTarget) -> Unit
) {
    val target = withContext(Dispatchers.IO) {
        loadHistoryResumeTarget(context, entry)
    }
    onResolved(target)
}
