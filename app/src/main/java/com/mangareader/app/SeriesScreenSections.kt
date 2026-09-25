package com.mangareader.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun SeriesChapterStatus(
    loading: Boolean,
    error: String?,
    onSolveChallenge: (() -> Unit)?,
    chaptersFetched: Boolean,
    chapterCount: Int,
    visibleCount: Int,
    isAnimeSource: Boolean,
    chapterStateReady: Boolean,
) {
    if (loading || (!chapterStateReady && chapterCount > 0)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    val challengeable = onSolveChallenge != null &&
        error?.contains("Cloudflare", ignoreCase = true) == true
    ErrorBanner(
        error = error,
        actionLabel = if (challengeable) "Open in WebView" else null,
        onAction = if (challengeable) onSolveChallenge else null,
    )

    if (chaptersFetched || chapterCount > 0) {
        Text(
            when {
                chapterCount > 0 && !chapterStateReady ->
                    if (isAnimeSource) "Preparing episodes…" else "Preparing chapters…"

                chapterCount == 0 ->
                    if (isAnimeSource) "This source returned no episodes"
                    else "This source returned no chapters"

                visibleCount != chapterCount ->
                    if (isAnimeSource) {
                        "$visibleCount of $chapterCount episodes"
                    } else {
                        "$visibleCount of $chapterCount chapters"
                    }

                chapterCount == 1 ->
                    if (isAnimeSource) "1 episode" else "1 chapter"

                else ->
                    if (isAnimeSource) "$chapterCount episodes"
                    else "$chapterCount chapters"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun SeriesSelectionOverlay(
    selectedChapters: List<Chapter>,
    visibleChapters: List<Chapter>,
    canDownload: Boolean,
    sourceId: String,
    onSelectAll: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onDownload: (List<Chapter>) -> Unit,
    onSetRead: (Boolean) -> Unit,
    onSetBookmarked: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ChapterSelectionBar(
        count = selectedChapters.size,
        canDownload = canDownload,
        onSelectAll = { onSelectAll(visibleChapters.map { it.id }.toSet()) },
        onClear = onClear,
        onDownload = { onDownload(selectedChapters) },
        onRead = { onSetRead(true) },
        onUnread = { onSetRead(false) },
        bookmarkAdds = selectedChapters.any {
            !Bookmarks.isBookmarked(context, chapterKeyOf(sourceId, it))
        },
        onBookmark = onSetBookmarked,
        onDelete = onDelete,
        modifier = modifier,
    )
}

@Composable
internal fun SeriesAuxiliaryDialogs(
    series: Series,
    sourceId: String,
    confirmDeleteChapter: Chapter?,
    onDismissDeleteChapter: () -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    confirmDeleteSelection: Boolean,
    selectedChapters: List<Chapter>,
    downloadTick: Int,
    onDismissDeleteSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    showCategories: Boolean,
    onDismissCategories: () -> Unit,
    showAddToLibrary: Boolean,
    onDismissAddToLibrary: () -> Unit,
    onSavedToLibrary: () -> Unit,
    showChapterOptions: Boolean,
    onDismissChapterOptions: () -> Unit,
    onChapterOptionsChanged: () -> Unit,
    coverOpen: Boolean,
    onDismissCover: () -> Unit,
) {
    SeriesDeleteDialogs(
        chapter = confirmDeleteChapter,
        onDismissChapter = onDismissDeleteChapter,
        onDeleteChapter = onDeleteChapter,
        selectionOpen = confirmDeleteSelection,
        selectedChapters = selectedChapters,
        downloadTick = downloadTick,
        onDismissSelection = onDismissDeleteSelection,
        onDeleteSelection = onDeleteSelection,
    )

    if (showCategories) {
        CategoryAssignDialog(
            seriesId = series.id,
            onDismiss = onDismissCategories,
        )
    }

    if (showAddToLibrary) {
        AddToLibraryDialog(
            series = series,
            sourceId = sourceId,
            onDismiss = onDismissAddToLibrary,
            onSaved = onSavedToLibrary,
        )
    }

    if (showChapterOptions) {
        ChapterOptionsSheet(
            onDismiss = onDismissChapterOptions,
            onChanged = onChapterOptionsChanged,
        )
    }

    if (coverOpen && series.cover != null) {
        CoverViewer(
            cover = series.cover,
            onDismiss = onDismissCover,
        )
    }
}
