package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
internal fun ReaderRoute(
    pages: List<File?>,
    stillLoading: Boolean,
    sourceId: String,
    series: Series?,
    chapter: Chapter,
    chapters: List<Chapter>,
    chapterIndex: Int,
    onOpenChapter: (Int) -> Unit,
    onRetryPage: (Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val chapterKey = chapterKeyOf(sourceId, chapter)
    val routeIdentity = readerRouteIdentity(sourceId, series?.id, chapter.id)
    val total = pages.size

    key(routeIdentity) {
        var observedPage by remember { mutableIntStateOf(-1) }

        // Check actual files only when the reader reaches the final page.
        // A large chapter can contain hundreds of cached images, so never
        // inspect them synchronously on Compose's UI/progress callback.
        // React to updated page slots as well as the end of streaming so a
        // successful retry can finally mark a previously incomplete chapter.
        LaunchedEffect(observedPage, pages, stillLoading) {
            if (shouldValidateReaderCompletion(observedPage, pages.size, stillLoading)) {
                val complete = withContext(Dispatchers.IO) {
                    isReaderChapterFullyLoadedAtEnd(observedPage, pages)
                }
                if (complete) ReadState.setRead(context, chapterKey, true)
            }
        }

        ReaderScreen(
            pages = pages,
            stillLoading = stillLoading,
            initialPage = safeReaderPageIndex(savedPage(context, chapterKey), total),
            sourceId = sourceId,
            seriesId = series?.id.orEmpty(),
            seriesTitle = series?.title ?: "",
            chapterName = chapter.name,
            chapters = chapters,
            chapterIndex = chapterIndex,
            hasPrev = chapterIndex > 0,
            hasNext = chapterIndex < chapters.size - 1,
            onPrev = { onOpenChapter(chapterIndex - 1) },
            onNext = { onOpenChapter(chapterIndex + 1) },
            onSelectChapter = onOpenChapter,
            onVisiblePages = { visible -> ReaderPagePriority.updateVisible(chapterKey, visible) },
            onRetryPage = onRetryPage,
            onProgress = { page ->
                if (!isValidReaderPageIndex(page, total)) return@ReaderScreen
                observedPage = page
                ReaderPagePriority.update(chapterKey, page)
                savePage(context, chapterKey, page)
                if (!isIncognito(context)) {
                    ReaderSessionCheckpointStore.save(
                        context,
                        ReaderSessionCheckpoint(
                            sourceId = sourceId,
                            seriesId = series?.id.orEmpty(),
                            chapterId = chapter.id,
                            page = page,
                        )
                    )
                }
                if (!isIncognito(context)) {
                    ReaderHistoryWriter.submit(
                        context,
                        HistoryEntry(
                            chapterKey = chapterKey,
                            title = listOfNotNull(series?.title, chapter.name)
                                .joinToString(" · "),
                            sourceId = sourceId,
                            seriesId = series?.id ?: "",
                            coverPath = when (val cover = series?.cover) {
                                is File -> cover.absolutePath
                                is String -> cover
                                else -> ""
                            },
                            page = page,
                            total = total,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            },
            onClose = onClose
        )
    }
}
