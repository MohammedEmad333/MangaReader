package com.mangareader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesContent(
    series: Series,
    sourceId: String,
    sourceName: String,
    chapters: List<Chapter>,
    visibleChapters: List<Chapter>,
    chaptersFetched: Boolean,
    loading: Boolean,
    error: String?,
    isAnimeSource: Boolean,
    inLibrary: Boolean,
    canDownload: Boolean,
    downloadingAll: Boolean,
    downloadedCount: Int,
    downloadTick: Int,
    downloadProgress: Map<String, DownloadQueue.DownloadProgress>,
    effectiveReadTick: Int,
    chapterDisplay: ChapterDisplay,
    selecting: Boolean,
    selectedIds: Set<String>,
    selectedChapters: List<Chapter>,
    filtersActive: Boolean,
    resumeIndex: Int,
    anyProgress: Boolean,
    listState: LazyListState,
    barAlpha: Float,
    seriesUrl: String?,
    onOpenCover: () -> Unit,
    onGlobalSearchTag: (String) -> Unit,
    onLibraryAction: () -> Unit,
    onCategories: () -> Unit,
    onToggleAllDownloads: () -> Unit,
    onDeleteDownloads: () -> Unit,
    descriptionExpanded: Boolean,
    onToggleDescriptionExpanded: () -> Unit,
    onSearchTag: (String) -> Unit,
    onSolveChallenge: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSetRead: (List<Chapter>, Boolean) -> Unit,
    onSetBookmarked: (List<Chapter>, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onRequestDeleteChapter: (Chapter) -> Unit,
    onDownload: (Chapter) -> Unit,
    onSelectAll: (Set<String>) -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
    onOpenChapterOptions: () -> Unit,
    onFindVideos: (Chapter) -> Unit,
    onMigrate: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = loading && chapters.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SeriesHero(
                        series = series,
                        sourceName = sourceName,
                        inLibrary = inLibrary,
                        canDownload = canDownload,
                        hasChapters = chapters.isNotEmpty(),
                        downloadingAll = downloadingAll,
                        downloadedCount = downloadedCount,
                        onOpenCover = onOpenCover,
                        onGlobalSearch = onGlobalSearchTag,
                        onLibraryAction = onLibraryAction,
                        onCategories = onCategories,
                        onToggleAllDownloads = onToggleAllDownloads,
                        onDeleteDownloads = onDeleteDownloads,
                    )
                }

                item {
                    SeriesDescriptionAndGenres(
                        series = series,
                        expanded = descriptionExpanded,
                        onToggleExpanded = onToggleDescriptionExpanded,
                        sourceName = sourceName,
                        onSearchTag = onSearchTag,
                        onGlobalSearchTag = onGlobalSearchTag,
                    )
                }

                item {
                    SeriesChapterStatus(
                        loading = loading,
                        error = error,
                        onSolveChallenge = onSolveChallenge,
                        chaptersFetched = chaptersFetched,
                        chapterCount = chapters.size,
                        visibleCount = visibleChapters.size,
                        isAnimeSource = isAnimeSource,
                    )
                }

                itemsIndexed(visibleChapters) { _, chapter ->
                    SeriesChapterRow(
                        chapter = chapter,
                        sourceId = sourceId,
                        readTick = effectiveReadTick,
                        isAnime = isAnimeSource,
                        downloadTick = downloadTick,
                        progress = downloadProgress[chapter.id],
                        canDownload = canDownload,
                        selecting = selecting,
                        selected = chapter.id in selectedIds,
                        chapterDisplay = chapterDisplay,
                        onSetRead = { read -> onSetRead(listOf(chapter), read) },
                        onSetBookmarked = { bookmarked ->
                            onSetBookmarked(listOf(chapter), bookmarked)
                        },
                        onOpen = { onOpen(chapter.id) },
                        onToggleSelected = { onToggleSelected(chapter.id) },
                        onDeleteChapter = { onRequestDeleteChapter(chapter) },
                        onDownload = { onDownload(chapter) },
                    )
                }

                item { Spacer(Modifier.height(88.dp)) }
            }
        }

        ListScrollHandle(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        if (selecting) {
            SeriesSelectionOverlay(
                selectedChapters = selectedChapters,
                visibleChapters = visibleChapters,
                canDownload = canDownload,
                sourceId = sourceId,
                onSelectAll = onSelectAll,
                onClear = onClearSelection,
                onDownload = { chaptersToDownload ->
                    chaptersToDownload.forEach(onDownload)
                    onClearSelection()
                },
                onSetRead = { read ->
                    onSetRead(selectedChapters, read)
                    onClearSelection()
                },
                onSetBookmarked = { adding ->
                    onSetBookmarked(selectedChapters, adding)
                    onClearSelection()
                },
                onDelete = onRequestDeleteSelection,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SeriesTopBar(
            title = series.title,
            canDownload = canDownload,
            chapters = chapters,
            visibleChapters = visibleChapters,
            sourceId = sourceId,
            onDownload = onDownload,
            filtersActive = filtersActive,
            onOpenChapterOptions = onOpenChapterOptions,
            onRefresh = onRefresh,
            onFindVideos = onFindVideos,
            inLibrary = inLibrary,
            onEditCategories = onCategories,
            onMigrate = onMigrate,
            seriesUrl = seriesUrl,
            onBack = onBack,
            barAlpha = barAlpha,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SeriesResumeFab(
            visible = chapters.isNotEmpty() && !selecting,
            chapters = chapters,
            resumeIndex = resumeIndex,
            anyProgress = anyProgress,
            onOpen = onOpen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}
