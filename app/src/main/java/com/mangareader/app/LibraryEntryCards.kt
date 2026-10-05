package com.mangareader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LibraryListEntryRow(
    entry: LibraryEntry,
    isSelected: Boolean,
    dim: Boolean,
    downloaded: Boolean,
    badgeLocal: Boolean,
    unread: Int?,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val isAnime = entry.sourceId.isAnimeExtensionSourceId() ||
        entry.seriesId.startsWith("anime:")
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .pointerInput(entry.seriesId, selecting) {
                detectTapGestures(
                    onTap = {
                        if (selecting) onToggle() else onOpen()
                    },
                    onLongPress = { onToggle() },
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        CoverImage(
            cover = entry.cover.ifBlank { null },
            title = entry.title,
            seriesId = entry.seriesId,
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(0.7f)
                .alpha(if (dim) 0.4f else 1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(if (dim) 0.4f else 1f),
        )
        if (isAnime) {
            Spacer(Modifier.width(8.dp))
            LibraryAnimeBadge()
        }
        Spacer(Modifier.width(6.dp))
        EntryBadges(
            downloaded = downloaded,
            local = badgeLocal,
            unread = unread,
        )
    }
}

@Composable
internal fun LibraryGridEntryCard(
    entry: LibraryEntry,
    display: LibraryDisplay,
    isSelected: Boolean,
    dim: Boolean,
    downloaded: Boolean,
    badgeLocal: Boolean,
    unread: Int?,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val isAnime = entry.sourceId.isAnimeExtensionSourceId() ||
        entry.seriesId.startsWith("anime:")

    Column(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .pointerInput(entry.seriesId, selecting) {
                detectTapGestures(
                    onTap = {
                        if (selecting) onToggle() else onOpen()
                    },
                    onLongPress = { onToggle() },
                )
            },
    ) {
        Box(
            modifier = Modifier.clip(MaterialTheme.shapes.medium),
        ) {
            CoverImage(
                cover = entry.cover.ifBlank { null },
                title = entry.title,
                seriesId = entry.seriesId,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .alpha(if (dim) 0.4f else 1f),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp),
            ) {
                EntryBadges(
                    downloaded = downloaded,
                    local = badgeLocal,
                    unread = unread,
                )
            }

            if (display == LibraryDisplay.COMPACT_GRID) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.84f),
                                ),
                            ),
                        )
                        .padding(start = 9.dp, end = 9.dp, top = 24.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (dim) 0.6f else 1f),
                    )
                }
            }

            if (isAnime && !isSelected) {
                LibraryAnimeBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp),
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                )
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp),
                )
            }
        }

        if (display == LibraryDisplay.COMFORTABLE_GRID) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .alpha(if (dim) 0.4f else 1f),
            )
        }
    }
}

@Composable
internal fun LibraryAnimeBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = "Anime",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
