package com.mangareader.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun ReaderLongStripPages(
    listState: LazyListState,
    modifier: Modifier,
    pages: List<File?>,
    stillLoading: Boolean,
    chapters: List<Chapter>,
    chapterIndex: Int,
    chapterName: String,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    colorFilter: ColorFilter?,
    textColor: Color,
    pageGap: Dp,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(pageGap),
    ) {
        item(
            key = "reader:previous",
            contentType = "transition",
        ) {
            ChapterTransitionRow(
                topLabel = if (hasPrev) "Previous" else null,
                topName = if (hasPrev) chapters.getOrNull(chapterIndex - 1)?.name else null,
                bottomLabel = "Current",
                bottomName = chapterName,
                fallback = "There's no previous chapter",
                textColor = textColor,
                onClick = if (hasPrev) onPrev else null,
            )
        }

        itemsIndexed(
            pages,
            key = { index, _ -> "reader:page:$index" },
            contentType = { _, _ -> "page" },
        ) { index, file ->
            ReaderPage(
                file = file,
                index = index,
                stillLoading = stillLoading,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
                contentScale = ContentScale.FillWidth,
                textColor = textColor,
            )
        }

        item(
            key = "reader:next",
            contentType = "transition",
        ) {
            ChapterTransitionRow(
                topLabel = "Finished",
                topName = chapterName,
                bottomLabel = if (hasNext) "Next" else null,
                bottomName = if (hasNext) chapters.getOrNull(chapterIndex + 1)?.name else null,
                fallback = "There's no next chapter",
                textColor = textColor,
                onClick = if (hasNext) onNext else null,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderPagedPages(
    pagerState: PagerState,
    modifier: Modifier,
    mode: ReaderMode,
    pages: List<File?>,
    stillLoading: Boolean,
    headRows: Int,
    chapters: List<Chapter>,
    chapterIndex: Int,
    chapterName: String,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    colorFilter: ColorFilter?,
    textColor: Color,
    onTap: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        reverseLayout = mode == ReaderMode.PAGED_RTL,
        modifier = modifier,
    ) { index ->
        when (index) {
            0 -> ChapterTransitionRow(
                topLabel = if (hasPrev) "Previous" else null,
                topName = if (hasPrev) chapters.getOrNull(chapterIndex - 1)?.name else null,
                bottomLabel = "Current",
                bottomName = chapterName,
                fallback = "There's no previous chapter",
                textColor = textColor,
                onClick = if (hasPrev) onPrev else null,
            )

            pages.size + headRows -> ChapterTransitionRow(
                topLabel = "Finished",
                topName = chapterName,
                bottomLabel = if (hasNext) "Next" else null,
                bottomName = if (hasNext) chapters.getOrNull(chapterIndex + 1)?.name else null,
                fallback = "There's no next chapter",
                textColor = textColor,
                onClick = if (hasNext) onNext else null,
            )

            else -> ReaderPage(
                file = pages.getOrNull(index - headRows),
                index = index - headRows,
                stillLoading = stillLoading,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                textColor = textColor,
                zoomable = true,
                onTap = onTap,
            )
        }
    }
}
