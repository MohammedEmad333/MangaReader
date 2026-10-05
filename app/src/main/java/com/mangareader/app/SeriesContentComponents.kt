package com.mangareader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun SeriesHero(
    series: Series,
    sourceName: String,
    inLibrary: Boolean,
    canDownload: Boolean,
    hasChapters: Boolean,
    downloadingAll: Boolean,
    downloadedCount: Int?,
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
                    .alpha(0.16f),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column {
            Spacer(Modifier.height(TOP_BAR_HEIGHT + 8.dp))

            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                CoverImage(
                    cover = series.cover,
                    title = series.title,
                    modifier = Modifier
                        .width(116.dp)
                        .aspectRatio(0.7f)
                        .then(
                            if (series.cover != null) {
                                Modifier.clickable(onClick = onOpenCover)
                            } else {
                                Modifier
                            },
                        ),
                )

                Spacer(Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.headlineSmall,
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
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = if (credits.size > 1) "$role · $name" else name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                onGlobalSearch(name)
                            },
                        )
                    }

                    val meta = listOfNotNull(
                        series.status?.takeIf { it.isNotBlank() },
                        sourceName.ifBlank { null },
                    ).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.padding(horizontal = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SeriesAction(
                        icon = if (inLibrary) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        label = if (inLibrary) "In library" else "Add to library",
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
                            icon = if (downloadingAll) Icons.Default.Clear else Icons.Default.Download,
                            label = if (downloadingAll) "Stop" else "Download all",
                            tint = if (downloadingAll) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = onToggleAllDownloads,
                        )

                        if (downloadedCount != null && downloadedCount > 0) {
                            SeriesAction(
                                icon = Icons.Default.Delete,
                                label = "Delete ($downloadedCount)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onDeleteDownloads,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
