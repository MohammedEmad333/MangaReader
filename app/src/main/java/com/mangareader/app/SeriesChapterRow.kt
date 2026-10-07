package com.mangareader.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

private data class ChapterDownloadUiState(
    val complete: Boolean,
    val sizeLabel: String?,
)

@Composable
private fun rememberChapterDownloadUiState(
    chapterId: String,
    downloadTick: Int,
): ChapterDownloadUiState? {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val state by produceState<ChapterDownloadUiState?>(null, chapterId, downloadTick) {
        value = withContext(Dispatchers.IO) {
            val complete = Downloads.isComplete(appContext, chapterId)
            ChapterDownloadUiState(
                complete = complete,
                sizeLabel = if (complete) formatBytes(Downloads.sizeOf(appContext, chapterId)) else null,
            )
        }
    }
    return state
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesChapterRow(
    chapter: Chapter,
    sourceId: String,
    readTick: Int,
    isAnime: Boolean,
    downloadTick: Int,
    progress: DownloadQueue.DownloadProgress?,
    canDownload: Boolean,
    selecting: Boolean,
    selected: Boolean,
    chapterDisplay: ChapterDisplay,
    onSetRead: (Boolean) -> Unit,
    onSetBookmarked: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onToggleSelected: () -> Unit,
    onDeleteChapter: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val key = chapterKeyOf(sourceId, chapter)
    val read = remember(key, readTick) { ReadState.isRead(context, key) }
    val resumePage = remember(key, readTick, isAnime) { if (isAnime) 0 else savedPage(context, key) }
    val resumePositionMs = remember(key, readTick, isAnime) {
        if (isAnime) VideoPlaybackProgress.position(context, key) else 0L
    }
    val bookmarked = remember(key, readTick) { Bookmarks.isBookmarked(context, key) }
    val downloadState = rememberChapterDownloadUiState(chapter.id, downloadTick)

    val toggleRead = SwipeAction(
        onSwipe = { if (!selecting) onSetRead(!read) },
        icon = {
            Icon(
                if (read) Icons.Default.Clear else Icons.Default.Check,
                contentDescription = if (isAnime) {
                    if (read) "Mark unwatched" else "Mark watched"
                } else {
                    if (read) "Mark unread" else "Mark read"
                },
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        background = MaterialTheme.colorScheme.secondaryContainer,
        isUndo = read,
    )

    val toggleBookmark = SwipeAction(
        onSwipe = { if (!selecting) onSetBookmarked(!bookmarked) },
        icon = {
            Icon(
                if (bookmarked) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        },
        background = MaterialTheme.colorScheme.tertiaryContainer,
        isUndo = bookmarked,
    )

    SwipeableActionsBox(
        startActions = listOf(toggleRead),
        endActions = listOf(toggleBookmark),
        swipeThreshold = 96.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clipToBounds(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        chapterLabel(chapter, chapterDisplay),
                        color = if (read) MaterialTheme.colorScheme.onSurface.copy(alpha = READ_DIM)
                        else MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    val sizeLabel = downloadState?.sizeLabel
                    val bits = listOfNotNull(
                        formatChapterDate(chapter.dateUploaded),
                        chapter.scanlator,
                        sizeLabel,
                        when {
                            isAnime && resumePositionMs > 0L -> "${if (read) "Rewatch" else "Resume"} ${formatMediaTime(resumePositionMs)}"
                            read -> null
                            !isAnime && resumePage > 0 -> "Page ${resumePage + 1}"
                            else -> null
                        },
                    )
                    if (bits.isNotEmpty()) {
                        Text(
                            bits.joinToString(" • "),
                            color = when {
                                (isAnime && resumePositionMs > 0L) || (!isAnime && resumePage > 0) -> MaterialTheme.colorScheme.primary
                                read -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = READ_DIM)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (bookmarked) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = "Bookmarked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        if (canDownload) {
                            val percent = progress?.percent
                            val downloaded = downloadState?.complete
                            val active = DownloadQueue.activeId == chapter.id
                            val queued = DownloadQueue.isQueued(chapter.id)
                            when {
                                active -> Text(
                                    if (percent != null && percent > 0) "$percent%" else "…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                queued -> Text(
                                    "Queued",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                downloaded == true -> IconButton(onClick = onDeleteChapter) {
                                    Icon(Icons.Default.Delete, "Downloaded — delete", tint = MaterialTheme.colorScheme.primary)
                                }
                                downloaded == null -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                else -> IconButton(onClick = onDownload) {
                                    Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.combinedClickable(
                    onClick = { if (selecting) onToggleSelected() else onOpen() },
                    onLongClick = onToggleSelected,
                ),
            )
        }
    }
}
