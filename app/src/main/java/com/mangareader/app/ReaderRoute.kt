package com.mangareader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val chapterKey = chapterKeyOf(sourceId, chapter)
    val total = pages.size

    key(chapterKey) {
        ReaderScreen(
            pages = pages,
            stillLoading = stillLoading,
            initialPage = savedPage(context, chapterKey).coerceIn(0, total - 1),
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
            onProgress = { page ->
                savePage(context, chapterKey, page)
                if (page >= total - 1) {
                    ReadState.setRead(context, chapterKey, true)
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
