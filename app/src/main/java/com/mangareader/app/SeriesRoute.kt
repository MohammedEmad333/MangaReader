package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onReadStateChanged: () -> Unit,
    onLibraryChanged: () -> Unit,
    onSearchTag: (String) -> Unit,
    onLibrarySearchTag: (String) -> Unit,
    onGlobalSearchTag: (String) -> Unit,
    onMigrate: () -> Unit,
    onSolveChallenge: (() -> Unit)?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
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
        onDownloadBatch = { batch -> source?.let { onDownloadAll(it, batch) } },
        onDownloadAll = { source?.let { onDownloadAll(it, chapters) } },
        onCancelDownloads = { onCancelDownloads(series.id) },
        onDeleteDownloads = {
            val snapshot = chapters.toList()
            scope.launch {
                withContext(Dispatchers.IO) {
                    Downloads.deleteMany(appContext, snapshot.map { it.id })
                }
                onDownloadStateChanged()
            }
        },
        onDeleteChapter = { chapter ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    Downloads.delete(appContext, chapter.id)
                }
                onDownloadStateChanged()
            }
        },
        onDeleteChapters = { selected ->
            val snapshot = selected.toList()
            scope.launch {
                withContext(Dispatchers.IO) {
                    Downloads.deleteMany(appContext, snapshot.map { it.id })
                }
                onDownloadStateChanged()
            }
        },
        onSetRead = { list, value ->
            val snapshot = list.toList()
            val anime = source?.isAnime == true
            scope.launch {
                withContext(Dispatchers.IO) {
                    snapshot.forEach {
                        val key = chapterKeyOf(resolvedSourceId, it)
                        ReadState.setRead(appContext, key, value)
                        if (anime) {
                            if (value) {
                                VideoPlaybackProgress.markCompleted(
                                    appContext,
                                    key,
                                    VideoPlaybackProgress.duration(appContext, key),
                                )
                            } else {
                                VideoPlaybackProgress.markIncomplete(appContext, key)
                            }
                        }
                    }
                }
                onReadStateChanged()
            }
        },
        onSetBookmarked = { list, value ->
            val keys = list.map { chapterKeyOf(resolvedSourceId, it) }
            scope.launch {
                withContext(Dispatchers.IO) {
                    Bookmarks.setBookmarkedBulk(
                        appContext,
                        keys,
                        value
                    )
                }
                onReadStateChanged()
            }
        },
        loading = loading,
        error = error,
        readTick = readTick,
        scroll = scroll,
        onOpen = { chapterId ->
            val index = chapters.indexOfFirst { it.id == chapterId }
            if (index >= 0) {
                if (source?.isAnime == true) {
                    onFindVideos(chapters[index])
                } else {
                    onOpenChapter(index)
                }
            }
        },
        onRefresh = onRefresh,
        seriesUrl = remember(series.handle, sourceId) {
            source?.seriesUrl(series)
        },
        onLibraryChanged = onLibraryChanged,
        onSearchTag = onSearchTag,
        onLibrarySearchTag = onLibrarySearchTag,
        onGlobalSearchTag = onGlobalSearchTag,
        onMigrate = onMigrate,
        onSolveChallenge = onSolveChallenge,
        onBack = onBack
    )
}
