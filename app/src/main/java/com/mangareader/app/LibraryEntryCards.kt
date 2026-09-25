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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    )
                } else {
                    Modifier
                },
            )
            .pointerInput(entry.seriesId, selecting) {
                detectTapGestures(
                    onTap = {
                        if (selecting) onToggle() else onOpen()
                    },
                    onLongPress = { onToggle() },
                )
            }
            .padding(vertical = 6.dp),
    ) {
        CoverImage(
            cover = entry.cover.ifBlank { null },
            title = entry.title,
            seriesId = entry.seriesId,
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(0.7f)
                .alpha(if (dim) 0.4f else 1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            entry.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(if (dim) 0.4f else 1f),
        )
        if (isAnime) {
            LibraryAnimeBadge()
            Spacer(Modifier.width(6.dp))
        }
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
            .padding(vertical = 4.dp)
            .pointerInput(entry.seriesId, selecting) {
                detectTapGestures(
                    onTap = {
                        if (selecting) onToggle() else onOpen()
                    },
                    onLongPress = { onToggle() },
                )
            },
    ) {
        Box {
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
                    .padding(4.dp),
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
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                ) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.labelSmall,
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
                        .padding(4.dp),
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ),
                )
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(2.dp),
                )
            }
        }

        if (display == LibraryDisplay.COMFORTABLE_GRID) {
            Spacer(Modifier.height(4.dp))
            Text(
                entry.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (dim) 0.4f else 1f),
            )
        }
    }
}

@Composable
internal fun LibraryAnimeBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            "Anime",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
