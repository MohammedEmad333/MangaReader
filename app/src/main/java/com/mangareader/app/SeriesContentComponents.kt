package com.mangareader.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

@Composable
internal fun SeriesHero(
    series: Series,
    sourceName: String,
    inLibrary: Boolean,
    canDownload: Boolean,
    hasChapters: Boolean,
    downloadingAll: Boolean,
    downloadedCount: Int,
    onOpenCover: () -> Unit,
    onGlobalSearch: (String) -> Unit,
    onLibraryAction: () -> Unit,
    onCategories: () -> Unit,
    onToggleAllDownloads: () -> Unit,
    onDeleteDownloads: () -> Unit,
) {
    Box {
        if (series.cover != null) {
            AsyncImage(
                model = series.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.20f),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column {
            Spacer(Modifier.height(TOP_BAR_HEIGHT))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                CoverImage(
                    cover = series.cover,
                    title = series.title,
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(0.7f)
                        .then(
                            if (series.cover != null) {
                                Modifier.clickable(onClick = onOpenCover)
                            } else {
                                Modifier
                            },
                        ),
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        series.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            onGlobalSearch(series.title)
                        },
                    )

                    val credits = listOfNotNull(
                        series.author
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "Story" to it },
                        series.artist
                            ?.takeIf {
                                it.isNotBlank() &&
                                    !it.equals(series.author, true)
                            }
                            ?.let { "Art" to it },
                    )

                    credits.forEach { (role, name) ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (credits.size > 1) {
                                "$role · $name"
                            } else {
                                name
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                onGlobalSearch(name)
                            },
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            series.status,
                            sourceName.ifBlank { null },
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SeriesAction(
                    icon = if (inLibrary) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    label = if (inLibrary) {
                        "In library"
                    } else {
                        "Add to library"
                    },
                    tint = if (inLibrary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = onLibraryAction,
                )

                if (inLibrary) {
                    SeriesAction(
                        icon = Icons.Default.Edit,
                        label = "Categories",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onCategories,
                    )
                }

                if (canDownload && hasChapters) {
                    SeriesAction(
                        icon = if (downloadingAll) {
                            Icons.Default.Clear
                        } else {
                            Icons.Default.Download
                        },
                        label = if (downloadingAll) {
                            "Stop"
                        } else {
                            "Download all"
                        },
                        tint = if (downloadingAll) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = onToggleAllDownloads,
                    )

                    if (downloadedCount > 0) {
                        SeriesAction(
                            icon = Icons.Default.Delete,
                            label = "Delete ($downloadedCount)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onDeleteDownloads,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeriesDescriptionAndGenres(
    series: Series,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    sourceName: String,
    onSearchTag: (String) -> Unit,
    onGlobalSearchTag: (String) -> Unit,
) {
    if (!series.description.isNullOrBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                series.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (expanded) {
                    "Collapse"
                } else {
                    "Expand"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    } else if (series.genres.isNotEmpty()) {
        Icon(
            if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (expanded) {
                "Collapse tags"
            } else {
                "Expand tags"
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 8.dp),
        )
    }

    if (series.genres.isNotEmpty()) {
        val tagModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)

        if (expanded) {
            FlowRow(
                modifier = tagModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GenreChips(
                    genres = series.genres,
                    sourceName = sourceName,
                    onSearchTag = onSearchTag,
                    onGlobalSearchTag = onGlobalSearchTag,
                )
            }
        } else {
            Row(
                modifier = tagModifier.horizontalScroll(
                    rememberScrollState(),
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GenreChips(
                    genres = series.genres,
                    sourceName = sourceName,
                    onSearchTag = onSearchTag,
                    onGlobalSearchTag = onGlobalSearchTag,
                )
            }
        }
    }
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
    val read = remember(key, readTick) {
        ReadState.isRead(context, key)
    }
    val resumePage = remember(key, readTick, isAnime) {
        if (isAnime) 0 else savedPage(context, key)
    }
    val resumePositionMs = remember(key, readTick, isAnime) {
        if (isAnime) VideoPlaybackProgress.position(context, key) else 0L
    }
    val bookmarked = remember(key, readTick) {
        Bookmarks.isBookmarked(context, key)
    }

    val toggleRead = SwipeAction(
        onSwipe = {
            if (!selecting) onSetRead(!read)
        },
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
        onSwipe = {
            if (!selecting) onSetBookmarked(!bookmarked)
        },
        icon = {
            Icon(
                if (bookmarked) {
                    Icons.Default.BookmarkBorder
                } else {
                    Icons.Default.Bookmark
                },
                contentDescription = if (bookmarked) {
                    "Remove bookmark"
                } else {
                    "Bookmark"
                },
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
        modifier = Modifier.clipToBounds(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    chapterLabel(chapter, chapterDisplay),
                    color = if (read) {
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = READ_DIM)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            supportingContent = {
                val sizeLabel = remember(chapter.id, downloadTick) {
                    if (Downloads.isComplete(context, chapter.id)) {
                        formatBytes(
                            Downloads.sizeOf(context, chapter.id),
                        )
                    } else {
                        null
                    }
                }

                val bits = listOfNotNull(
                    formatChapterDate(chapter.dateUploaded),
                    chapter.scanlator,
                    sizeLabel,
                    when {
                        isAnime && resumePositionMs > 0L ->
                            "${if (read) "Rewatch" else "Resume"} ${formatMediaTime(resumePositionMs)}"
                        read -> null
                        !isAnime && resumePage > 0 ->
                            "Page ${resumePage + 1}"
                        else -> null
                    },
                )

                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" • "),
                        color = when {
                            (isAnime && resumePositionMs > 0L) ||
                                (!isAnime && resumePage > 0) ->
                                MaterialTheme.colorScheme.primary
                            read ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = READ_DIM)
                            else ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        val downloaded =
                            remember(chapter.id, downloadTick) {
                                Downloads.isComplete(
                                    context,
                                    chapter.id,
                                )
                            }
                        val active =
                            DownloadQueue.activeId == chapter.id
                        val queued =
                            DownloadQueue.isQueued(chapter.id)

                        when {
                            active -> Text(
                                if (percent != null && percent > 0) {
                                    "$percent%"
                                } else {
                                    "…"
                                },
                                style =
                                    MaterialTheme.typography.labelMedium,
                                color =
                                    MaterialTheme.colorScheme.primary,
                            )

                            queued -> Text(
                                "Queued",
                                style =
                                    MaterialTheme.typography.labelMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            downloaded -> IconButton(
                                onClick = onDeleteChapter,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription =
                                        "Downloaded — delete",
                                    tint =
                                        MaterialTheme.colorScheme.primary,
                                )
                            }

                            else -> IconButton(
                                onClick = onDownload,
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))
                    }
                }
            },
            colors = if (selected) {
                ListItemDefaults.colors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier.combinedClickable(
                onClick = {
                    if (selecting) {
                        onToggleSelected()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = onToggleSelected,
            ),
        )
    }

    HorizontalDivider()
}
