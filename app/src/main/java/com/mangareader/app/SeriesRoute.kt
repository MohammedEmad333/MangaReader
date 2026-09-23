package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun SeriesRoute(
    series: Series,
    chapters: List<Chapter>,
    chaptersFetched: Boolean,
    source: Source?,
    sourceId: String?,
    loading: Boolean,
    error: String?,
    readTick: Int,
    scroll: ScrollMemory,
    localDownloadTick: Int,
    onFindVideos: (Chapter) -> Unit,
    onDownload: (Source, Chapter) -> Unit,
    onDownloadAll: (Source, List<Chapter>) -> Unit,
    onCancelDownloads: (String) -> Unit,
    onDownloadStateChanged: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLibraryChanged: () -> Unit,
    onSearchTag: (String) -> Unit,
    onGlobalSearchTag: (String) -> Unit,
    onMigrate: () -> Unit,
    onSolveChallenge: (() -> Unit)?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resolvedSourceId = sourceId.orEmpty()

    SeriesScreen(
        series = series,
        chapters = chapters,
        chaptersFetched = chaptersFetched,
        onFindVideos = onFindVideos,
        sourceId = resolvedSourceId,
        sourceName = source?.name.orEmpty(),
        canDownload = source?.supportsDownload == true,
        downloadProgress = DownloadQueue.progress,
        downloadTick = localDownloadTick + DownloadQueue.tick,
        downloadingAll = DownloadQueue.hasSeries(series.id),
        onDownload = { chapter -> source?.let { onDownload(it, chapter) } },
        onDownloadAll = { source?.let { onDownloadAll(it, chapters) } },
        onCancelDownloads = { onCancelDownloads(series.id) },
        onDeleteDownloads = {
            chapters.forEach { Downloads.delete(context, it.id) }
            onDownloadStateChanged()
        },
        onDeleteChapter = { chapter ->
            Downloads.delete(context, chapter.id)
            onDownloadStateChanged()
        },
        onSetRead = { list, value ->
            list.forEach {
                ReadState.setRead(context, chapterKeyOf(resolvedSourceId, it), value)
            }
            onLibraryChanged()
        },
        onSetBookmarked = { list, value ->
            Bookmarks.setBookmarkedBulk(
                context,
                list.map { chapterKeyOf(resolvedSourceId, it) },
                value
            )
            onLibraryChanged()
        },
        loading = loading,
        error = error,
        readTick = readTick,
        scroll = scroll,
        onOpen = { chapterId ->
            val index = chapters.indexOfFirst { it.id == chapterId }
            if (index >= 0) onOpenChapter(index)
        },
        onRefresh = onRefresh,
        seriesUrl = remember(series.handle, sourceId) {
            source?.seriesUrl(series)
        },
        onLibraryChanged = onLibraryChanged,
        onSearchTag = onSearchTag,
        onGlobalSearchTag = onGlobalSearchTag,
        onMigrate = onMigrate,
        onSolveChallenge = onSolveChallenge,
        onBack = onBack
    )
}
